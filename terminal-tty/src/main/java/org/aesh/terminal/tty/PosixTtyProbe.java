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
 * POSIX {@code isatty} fast path for {@link TtyDetect}, without subprocesses.
 * <p>
 * Base-layer stub: the real implementation lives in the Java 22 MRJAR
 * overlay ({@code META-INF/versions/22}) and uses a {@code java.lang.foreign}
 * downcall that cannot be named from release-8 sources. Every entry point
 * here declines (returns null), so callers fall back to the {@code test -t}
 * subprocess on runtimes without the overlay.
 * <p>
 * The public member set must stay identical to the Java 22 MRJAR overlay:
 * MRJAR validation rejects versioned classes that add public members or
 * change signatures, and only release-8 types may appear here.
 */
final class PosixTtyProbe {

    private PosixTtyProbe() {
    }

    /**
     * Whether the FFM isatty binding is available.
     *
     * @return false on runtimes without the Java 22 overlay
     */
    static boolean isNativeAccessEnabled() {
        return false;
    }

    /**
     * Test whether a standard descriptor refers to a terminal.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return null, always: no FFM on runtimes without the overlay
     */
    static Boolean tryIsTty(int fd) {
        return null;
    }
}
