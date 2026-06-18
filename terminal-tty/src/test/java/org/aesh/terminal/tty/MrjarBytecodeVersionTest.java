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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.Test;

/**
 * Guard against MRJAR bytecode contamination (#211).
 * <p>
 * Verifies that no class file in the base path of the terminal-tty JAR
 * has a bytecode version higher than the project's target (Java 8, major 52).
 * Classes under META-INF/versions/ are allowed to have higher versions.
 * <p>
 * This test catches the scenario where the java22-ffm profile's
 * copy-mrjar-for-test step accidentally copies Java 22 bytecode into
 * target/classes, which then gets packaged into the released JAR.
 */
public class MrjarBytecodeVersionTest {

    /** Java 8 = major version 52. The project targets Java 8 (maven.compiler.release=8). */
    private static final int MAX_BASE_MAJOR_VERSION = 52;

    @Test
    public void testNoHighBytecodeInBasePath() throws Exception {
        // Scan target/classes for .class files with version > 52
        File classesDir = new File("target/classes");
        if (!classesDir.isDirectory()) {
            // Skip if not built yet (e.g., running from IDE without mvn compile)
            return;
        }

        List<String> violations = new ArrayList<>();
        scanDirectory(classesDir, classesDir, violations);

        assertTrue(
                "Found class files in target/classes with bytecode version > "
                        + MAX_BASE_MAJOR_VERSION + " (Java 8). "
                        + "These would break JDK 17 users if packaged into the JAR.\n"
                        + "Violations:\n  " + String.join("\n  ", violations),
                violations.isEmpty());
    }

    private void scanDirectory(File root, File dir, List<String> violations) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                // Skip META-INF/versions — those are allowed to have higher versions
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
                return -1; // Not a class file
            }
            dis.readUnsignedShort(); // minor version
            return dis.readUnsignedShort(); // major version
        }
    }

    private String relativePath(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
    }
}
