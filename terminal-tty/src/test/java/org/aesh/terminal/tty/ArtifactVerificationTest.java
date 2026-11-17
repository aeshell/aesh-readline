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

import static org.junit.Assert.assertEquals;
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
import java.util.Set;

import org.junit.Test;

/**
 * Verifies the terminal-tty artifact is correctly structured for release.
 * <p>
 * These tests guard against regressions that only manifest in the released
 * JAR, not during development builds:
 * <ul>
 * <li>#211 — Java 22 bytecode in base path breaks JDK 8-17 users</li>
 * <li>#218 — Missing native-image.properties causes class init failure</li>
 * <li>#209 — Broken native-image.properties disables all ServiceLoader providers</li>
 * </ul>
 */
public class ArtifactVerificationTest {

    /** Java 8 = major version 52. The project targets Java 8 (maven.compiler.release=8). */
    private static final int MAX_BASE_MAJOR_VERSION = 52;

    private static final String NATIVE_IMAGE_PROPS = "META-INF/native-image/org.aesh/terminal-tty/native-image.properties";
    private static final String SERVICES_FILE = "META-INF/services/org.aesh.terminal.provider.TerminalProvider";

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
        assertTrue("native-image.properties must contain --initialize-at-run-time for WinSysTerminal$Handles",
                content.contains("WinSysTerminal$Handles"));
        assertTrue("native-image.properties must contain --initialize-at-run-time for "
                + "AbstractWindowsTerminal$ConsoleOutput$OutputHandle",
                content.contains("AbstractWindowsTerminal$ConsoleOutput$OutputHandle"));
        assertFalse("native-image.properties must not reference the moved JNI bridge: "
                + "WinConsoleNative entries live in terminal-detect since #389",
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

    // ========== Guard #253: FFM native-image configuration ==========

    private static final String REACHABILITY_METADATA = "META-INF/native-image/org.aesh/terminal-tty/reachability-metadata.json";
    private static final String REFLECT_CONFIG = "META-INF/native-image/org.aesh/terminal-tty/reflect-config.json";

    @Test
    public void testReachabilityMetadataExists() {
        if (!new File("target/classes").isDirectory()) {
            return;
        }
        File metadataFile = new File("target/classes/" + REACHABILITY_METADATA);
        assertTrue("reachability-metadata.json must be present at " + REACHABILITY_METADATA
                + " — without it, GraalVM 25+ cannot register FFM downcalls for FfmPty (#253)",
                metadataFile.isFile());
    }

    @Test
    public void testReachabilityMetadataHasDowncalls() {
        File metadataFile = new File("target/classes/" + REACHABILITY_METADATA);
        if (!metadataFile.isFile()) {
            return; // testReachabilityMetadataExists will catch this
        }
        String content = readFileContent(metadataFile);
        assertNotNull("Could not read reachability-metadata.json", content);

        assertTrue("reachability-metadata.json must contain a 'foreign' section",
                content.contains("\"foreign\""));
        assertTrue("reachability-metadata.json must contain a 'downcalls' section",
                content.contains("\"downcalls\""));

        // Verify all 9 downcall signatures are present by checking for distinctive
        // parameter patterns. Each LibC function has a unique type signature.
        // isatty: (jint) -> jint (no captureCallState)
        // open: (void*, jint) -> jint (keeps captureCallState; errno is read)
        // read: (jint, void*, jlong) -> jlong
        // tcgetattr: (jint, void*) -> jint
        // tcsetattr: (jint, jint, void*) -> jint
        // ioctl: firstVariadicArg
        assertTrue("reachability-metadata.json must contain read() downcall (jlong return type)",
                content.contains("\"jlong\""));
        assertTrue("reachability-metadata.json must keep captureCallState for open()",
                content.contains("\"captureCallState\""));
        assertEquals("only open() may capture call state (#348)",
                1, countOccurrences(content, "\"captureCallState\""));
        assertTrue("reachability-metadata.json must contain firstVariadicArg for ioctl",
                content.contains("\"firstVariadicArg\""));
    }

    @Test
    public void testNativeImagePropertiesContainsFfmInitAtRunTime() {
        String content = readNativeImageProperties();
        if (content == null) {
            return;
        }

        assertTrue("native-image.properties must contain --initialize-at-run-time for LibC (#253)",
                content.contains("LibC"));
        assertTrue("native-image.properties must contain --initialize-at-run-time for PosixConstants (#253)",
                content.contains("PosixConstants"));
        assertTrue("native-image.properties must contain --initialize-at-run-time for FfmPty (#253)",
                content.contains("FfmPty"));
    }

    @Test
    public void testNativeImagePropertiesContainsSharedArenaSupport() {
        String content = readNativeImageProperties();
        if (content == null) {
            return;
        }

        assertTrue("native-image.properties must contain SharedArenaSupport for Arena.ofShared() (#253)",
                content.contains("SharedArenaSupport"));
    }

    @Test
    public void testNativeImagePropertiesContainsPosixTtyProbeHandles() {
        String content = readNativeImageProperties();
        if (content == null) {
            return;
        }

        // The isatty downcall holder initializes the Linker on first use;
        // in a native image that must happen at runtime, never at build
        // time. Without this pin an embedder-style broad build-time
        // initialization would bake a build-time Linker into the binary.
        assertTrue("native-image.properties must contain --initialize-at-run-time for "
                + "PosixTtyProbe$Handles (#399)",
                content.contains("org.aesh.terminal.tty.PosixTtyProbe$Handles"));
    }

    @Test
    public void testPosixTtyProbeHandlesDeclared() throws Exception {
        // The runtime pin above is worthless if the holder it names does
        // not exist. The overlay (with its nested Handles) only compiles
        // on Java 22+, and the native compatibility profile deletes the
        // copy for old-GraalVM testing: skip every shape that cannot prove
        // the declaration instead of failing it.
        if (!isJava22OrLater()) {
            return;
        }
        if (!new File("target/test-classes/org/aesh/terminal/tty/PosixTtyProbe.class").isFile()) {
            return;
        }
        Class<?> probe = Class.forName("org.aesh.terminal.tty.PosixTtyProbe");
        Class<?> handles = Class.forName("org.aesh.terminal.tty.PosixTtyProbe$Handles");
        assertTrue("PosixTtyProbe$Handles must be nested in PosixTtyProbe",
                handles.getDeclaringClass() == probe);
    }

    @Test
    public void testReachabilityMetadataHasPlainIntDowncall() {
        File metadataFile = new File("target/classes/" + REACHABILITY_METADATA);
        if (!metadataFile.isFile()) {
            return;
        }
        String content = readFileContent(metadataFile);
        assertNotNull("Could not read reachability-metadata.json", content);

        // The isatty fast path (#399) reuses the existing plain
        // (jint) -> jint descriptor: no duplicate entry, no variadic
        // options, no call-state capture for this shape.
        assertTrue("reachability-metadata.json must register the plain (jint) -> jint downcall "
                + "the isatty fast path reuses",
                content.contains("\"returnType\": \"jint\"")
                        && content.contains("\"parameterTypes\": [\"jint\"]"));
    }

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
    public void testReflectConfigContainsFfmPty() {
        File configFile = new File("target/classes/" + REFLECT_CONFIG);
        if (!configFile.isFile()) {
            return;
        }
        String content = readFileContent(configFile);
        assertNotNull("Could not read reflect-config.json", content);

        assertTrue("reflect-config.json must contain FfmPty for Class.forName in FfmTerminalProvider (#253)",
                content.contains("FfmPty"));
        assertTrue("reflect-config.json must register FfmPty.current() method",
                content.contains("current"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    private String readFileContent(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(java.nio.file.Files.newInputStream(file.toPath()),
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
