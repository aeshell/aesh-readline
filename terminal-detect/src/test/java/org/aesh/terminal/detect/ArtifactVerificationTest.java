/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.detect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.tty.impl.WinConsoleNative;
import org.junit.Test;

/**
 * Verifies the terminal-detect artifact keeps its FFM downcall holders
 * pinned to runtime initialization.
 * <p>
 * Background (#368): embedder-wide build-time init (e.g. Quarkus) freezes
 * downcall MethodHandles into constants, and GraalVM CE 25.0.1 cannot build
 * the linkToNative graph for constant receivers. Narrow exact-class
 * {@code --initialize-at-run-time} entries in the shipped
 * native-image.properties win over broad build-time patterns, but unmatched
 * patterns fail silent, so renames or holder moves lose the protection
 * without any build signal. These tests make that loss loud.
 */
public class ArtifactVerificationTest {

    private static final String NATIVE_IMAGE_PROPS = "META-INF/native-image/org.aesh/terminal-detect/native-image.properties";

    /** Downcall holder classes that must stay runtime-initialized. */
    private static final String[] HOLDERS = {
            "org.aesh.terminal.detect.FfmPosix$Handles",
            "org.aesh.terminal.detect.Win32Probe$Handles",
    };

    /**
     * Whether the runtime is Java 22 or later (release-8-safe: no
     * {@code Runtime.version()} which needs Java 9+). The holder classes
     * live in the MRJAR overlay and cannot load below 22.
     */
    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return false;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Test
    public void testNativeImagePropertiesPinsMovedBridgeInit() {
        String content = readNativeImageProperties();
        if (content == null) {
            return; // testNativeImagePropertiesExists will catch this
        }
        assertTrue("native-image.properties must defer the JNI facade static init to run time",
                content.contains("org.aesh.terminal.tty.impl.WinConsoleNative,"));
        assertTrue("native-image.properties must defer the JNI bridge static init "
                + "(DLL load) to run time",
                content.contains("org.aesh.terminal.tty.impl.WinConsoleNativeJni"));
    }

    private String readFileContent(File configFile) {
        if (!configFile.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(java.nio.file.Files.newInputStream(configFile.toPath()),
                        StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private String readNativeImageProperties() {
        File propsFile = new File("target/classes/" + NATIVE_IMAGE_PROPS);
        if (!propsFile.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(java.nio.file.Files.newInputStream(propsFile.toPath()),
                        StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    @Test
    public void testNativeImagePropertiesExists() {
        File propsFile = new File("target/classes/" + NATIVE_IMAGE_PROPS);
        if (!new File("target/classes").isDirectory()) {
            return; // Skip if not built yet
        }
        assertTrue("native-image.properties must be present at " + NATIVE_IMAGE_PROPS,
                propsFile.isFile());
    }

    @Test
    public void testNativeImagePropertiesPinsDowncallHolders() {
        String content = readNativeImageProperties();
        if (content == null) {
            return; // testNativeImagePropertiesExists will catch this
        }
        assertTrue("native-image.properties must contain --initialize-at-run-time for "
                + "FfmPosix$Handles (#368)", content.contains("FfmPosix$Handles"));
        assertTrue("native-image.properties must contain --initialize-at-run-time for "
                + "Win32Probe$Handles (#368)", content.contains("Win32Probe$Handles"));
    }

    @Test
    public void testPinnedHoldersExistAndHoldDowncalls() throws Exception {
        if (!isJava22OrLater()) {
            return; // Overlay holder classes cannot load below Java 22
        }
        for (int i = 0; i < HOLDERS.length; i++) {
            String holder = HOLDERS[i];
            // Load without initializing: proves the name is current without
            // paying Linker init or needing native access.
            Class<?> clazz;
            try {
                clazz = Class.forName(holder, false, getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                // Closed-world native images drop holders nothing reachable
                // references (e.g. POSIX holders with FFM tests gated off).
                // Absence here is legitimate; the JVM suite above is the
                // strict gate for renames.
                continue;
            }
            boolean holdsDowncall = false;
            Field[] fields = clazz.getDeclaredFields();
            for (int j = 0; j < fields.length; j++) {
                Field field = fields[j];
                if (field.getType() == MethodHandle.class
                        && Modifier.isStatic(field.getModifiers())) {
                    holdsDowncall = true;
                    break;
                }
            }
            assertTrue("Pinned holder " + holder + " must still declare a static "
                    + "MethodHandle field — if the handles moved, the "
                    + "native-image.properties entry protects nothing (#368)",
                    holdsDowncall);
        }
    }

    @Test
    public void testWinConsoleNativeClassLoadable() {
        // WinConsoleNative must be loadable on any platform. Its static
        // initializer has an OS guard that skips loadLibrary() on non-Windows.
        try {
            Class.forName("org.aesh.terminal.tty.impl.WinConsoleNative");
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            fail("WinConsoleNative class should be loadable on any platform "
                    + "without errors. Got: " + e);
        } catch (ClassNotFoundException e) {
            // OK
        }
    }

    // ========== Guard #342: MRJAR base/overlay API parity ==========

    @Test
    public void testWinConsoleNativeHasNoNativeMethods() {
        // jar --validate compares modifiers including native: neither the
        // base facade nor the Java 22 overlay may declare native methods.
        // This runs against whichever variant the test JVM loads, so it
        // pins both sides with one assertion set.
        for (Method method : WinConsoleNative.class.getDeclaredMethods()) {
            assertFalse("WinConsoleNative." + method.getName()
                    + " must not be native (MRJAR parity, #342)",
                    Modifier.isNative(method.getModifiers()));
        }
    }

    @Test
    public void testWinConsoleNativeJniCoversFacade() throws Exception {
        // Every public facade method must have a same-signature JNI binding:
        // the facade delegates unconditionally, so a missing binding is an
        // UnsatisfiedLinkError on every Windows call.
        Class<?> jni;
        try {
            jni = Class.forName("org.aesh.terminal.tty.impl.WinConsoleNativeJni");
        } catch (ClassNotFoundException e) {
            fail("WinConsoleNativeJni must exist as the JNI binding holder (#342)");
            return;
        }
        for (Method facade : WinConsoleNative.class.getDeclaredMethods()) {
            int modifiers = facade.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                continue;
            }
            try {
                jni.getDeclaredMethod(facade.getName(), facade.getParameterTypes());
            } catch (NoSuchMethodException e) {
                fail("WinConsoleNativeJni lacks a binding for facade method "
                        + facade.getName() + " (#342)");
            }
        }
    }

    @Test
    public void testJniConfigNamesJniBridge() {
        File configFile = new File("target/classes/META-INF/native-image/org.aesh.terminal-detect/jni-config.json");
        if (!configFile.isFile()) {
            return;
        }
        String content = readFileContent(configFile);
        assertNotNull("Could not read jni-config.json", content);
        assertTrue("jni-config.json must register the JNI bridge class, "
                + "not the facade (#342)",
                content.contains("org.aesh.terminal.tty.impl.WinConsoleNativeJni"));
        assertFalse("jni-config.json must not register native methods on the facade (#342)",
                content.contains("\"name\": \"org.aesh.terminal.tty.impl.WinConsoleNative\""));
    }

    @Test
    public void testNativeImagePropertiesCoversJniBridge() {
        File configFile = new File("target/classes/META-INF/native-image/org.aesh.terminal-detect/native-image.properties");
        if (!configFile.isFile()) {
            return;
        }
        String content = readFileContent(configFile);
        assertNotNull("Could not read native-image.properties", content);
        assertTrue("native-image.properties must defer the JNI bridge static init "
                + "(DLL load) to run time (#342)",
                content.contains("org.aesh.terminal.tty.impl.WinConsoleNativeJni"));
    }

}
