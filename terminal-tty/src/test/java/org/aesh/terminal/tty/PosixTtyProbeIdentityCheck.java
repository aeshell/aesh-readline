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

/**
 * Child-JVM probe for the denied-native-access decline path.
 * <p>
 * Launched without blanket native access, it reports whether the FFM
 * overlay is loaded and what the helper answers for the standard
 * descriptors. The parent test asserts every answer is a decline.
 */
public final class PosixTtyProbeIdentityCheck {

    private PosixTtyProbeIdentityCheck() {
    }

    /**
     * Print one line: overlay presence, native-access flag, and the
     * helper answer per standard fd.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        boolean overlay = false;
        try {
            java.security.CodeSource source = PosixTtyProbe.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                String location = source.getLocation().toString();
                overlay = location.contains("test-classes") || location.endsWith(".jar");
            }
        } catch (SecurityException e) {
            overlay = false;
        }
        StringBuilder out = new StringBuilder("overlay=");
        out.append(overlay);
        out.append(" native=").append(PosixTtyProbe.isNativeAccessEnabled());
        for (int fd = 0; fd <= 2; fd++) {
            out.append(' ').append(fd).append(':').append(PosixTtyProbe.tryIsTty(fd));
        }
        System.out.println(out);
    }
}
