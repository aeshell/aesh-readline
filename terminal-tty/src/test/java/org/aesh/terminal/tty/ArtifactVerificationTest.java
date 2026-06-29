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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

/**
 * Verifies the terminal-tty artifact is correctly structured for release.
 * <p>
 * These tests guard against regressions that only manifest in the released
 * JAR, not during development builds:
 * <ul>
 *   <li>#211 — Java 22 bytecode in base path breaks JDK 8-17 users</li>
 *   <li>#218 — Missing native-image.properties causes class init failure</li>
 *   <li>#209 — Broken native-image.properties disables all ServiceLoader providers</li>
 * </ul>
 */
public class ArtifactVerificationTest {

    /** Java 8 = major version 52. The project targets Java 8 (maven.compiler.release=8). */
    private static final int MAX_BASE_MAJOR_VERSION = 52;

    private static final String NATIVE_IMAGE_PROPS =
            "META-INF/native-image/org.aesh/terminal-tty/native-image.properties";
    private static final String SERVICES_FILE =
            "META-INF/services/org.aesh.terminal.provider.TerminalProvider";

    // ========== Guard #211: No high bytecode in base path ==========

    @Test
    public void testNoHighBytecodeInBasePath() throws Exception {
        File classesDir = new File("target/classes");
        if (!classesDir.isDirectory()) {
            return; // Skip if not built yet
        }

        List<String> violations = new ArrayList<>();
        scanDirectory(classesDir, classesDir, violations);

        assertTrue(
                "Found class files in target/classes with bytecode version > "
                        + MAX_BASE_MAJOR_VERSION + " (Java 8). "
                        + "These would break JDK 8-17 users if packaged into the JAR.\n"
                        + "Violations:\n  " + String.join("\n  ", violations),
                violations.isEmpty());
    }

    // ========== Guard #218: native-image.properties exists and is correct ==========
    // Note: GraalVM native-image consumes native-image.properties at build time
    // and does NOT embed it as a classpath resource in the native binary.
    // We read from target/classes on disk instead of getResourceAsStream().

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
        assertTrue("native-image.properties must be present at " + NATIVE_IMAGE_PROPS
                + " — without it, GraalVM native-image may fail to initialize "
                + "Windows terminal classes on Linux (#218)", propsFile.isFile());
    }

    @Test
    public void testNativeImagePropertiesContainsInitAtRunTime() throws Exception {
        String content = readNativeImageProperties();
        if (content == null) {
            return; // testNativeImagePropertiesExists will catch this
        }

        assertTrue("native-image.properties must contain --initialize-at-run-time for WinSysTerminal (#218)",
                content.contains("WinSysTerminal"));
        assertTrue("native-image.properties must contain --initialize-at-run-time for WinConsoleNative (#218)",
                content.contains("WinConsoleNative"));
    }

    @Test
    public void testNativeImagePropertiesDoesNotBreakServiceLoader() throws Exception {
        String content = readNativeImageProperties();
        if (content == null) {
            return;
        }

        assertFalse("native-image.properties MUST NOT contain ServiceLoaderFeatureExcludeServiceProviders "
                + "— this flag broke ALL providers in #209",
                content.contains("ServiceLoaderFeatureExcludeServiceProviders"));
    }

    // ========== Guard: ServiceLoader services file is correct ==========

    @Test
    public void testServicesFileExists() {
        InputStream is = getClass().getClassLoader().getResourceAsStream(SERVICES_FILE);
        assertNotNull("ServiceLoader services file must be present at " + SERVICES_FILE, is);
    }

    @Test
    public void testServicesFileContainsAllProviders() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(SERVICES_FILE);
        if (is == null) {
            return;
        }
        Set<String> providers = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    providers.add(line);
                }
            }
        }

        List<String> expected = Arrays.asList(
                "org.aesh.terminal.tty.provider.FfmTerminalProvider",
                "org.aesh.terminal.tty.provider.WinSysTerminalProvider",
                "org.aesh.terminal.tty.provider.CygwinTerminalProvider",
                "org.aesh.terminal.tty.provider.ExecPtyTerminalProvider");

        for (String provider : expected) {
            assertTrue("ServiceLoader services file must list " + provider, providers.contains(provider));
        }
    }

    // ========== Guard #218: Platform-specific classes safe to load ==========

    @Test
    public void testWinSysTerminalClassLoadable() {
        // WinSysTerminal must be loadable (Class.forName) on any platform
        // without triggering native calls. The lazy Handles inner class
        // pattern ensures getStdHandle() is NOT called at class load time.
        try {
            Class.forName("org.aesh.terminal.tty.impl.WinSysTerminal");
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            fail("WinSysTerminal class should be loadable on any platform without "
                    + "triggering native calls (#218). Got: " + e);
        } catch (ClassNotFoundException e) {
            // OK — may not be on classpath in some test configurations
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

    @Test
    public void testAllProviderClassesLoadable() {
        // Every provider listed in META-INF/services must be loadable
        // via Class.forName without errors on any platform.
        String[] providers = {
                "org.aesh.terminal.tty.provider.FfmTerminalProvider",
                "org.aesh.terminal.tty.provider.WinSysTerminalProvider",
                "org.aesh.terminal.tty.provider.CygwinTerminalProvider",
                "org.aesh.terminal.tty.provider.ExecPtyTerminalProvider"
        };
        for (String provider : providers) {
            try {
                Class.forName(provider);
            } catch (Throwable e) {
                fail("Provider " + provider + " must be loadable via Class.forName on any platform. Got: " + e);
            }
        }
    }

    // ========== Helpers ==========

    private void scanDirectory(File root, File dir, List<String> violations) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String relative = relativePath(root, file);
                if (relative.startsWith("META-INF/versions")) {
                    continue;
                }
                scanDirectory(root, file, violations);
            } else if (file.getName().endsWith(".class")) {
                int majorVersion = readMajorVersion(file);
                if (majorVersion > MAX_BASE_MAJOR_VERSION) {
                    String relative = relativePath(root, file);
                    violations.add(relative + " (major version " + majorVersion
                            + " = Java " + (majorVersion - 44) + ")");
                }
            }
        }
    }

    private int readMajorVersion(File classFile) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                java.nio.file.Files.newInputStream(classFile.toPath()))) {
            int magic = dis.readInt();
            if (magic != 0xCAFEBABE) {
                return -1;
            }
            dis.readUnsignedShort(); // minor version
            return dis.readUnsignedShort(); // major version
        }
    }

    private String relativePath(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    }
}
