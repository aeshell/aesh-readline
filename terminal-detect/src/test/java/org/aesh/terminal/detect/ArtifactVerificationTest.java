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

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

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
            Class<?> clazz = Class.forName(holder, false, getClass().getClassLoader());
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
}
