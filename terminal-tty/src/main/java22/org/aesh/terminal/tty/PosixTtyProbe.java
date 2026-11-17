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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import org.aesh.terminal.utils.OSUtils;

/**
 * POSIX {@code isatty} fast path for {@link TtyDetect} (Java 22+).
 * <p>
 * Answers the descriptor question with one in-process syscall instead of
 * a {@code sh -c "test -t N"} child: no process launch, no pipe drain,
 * no watchdog. Only the answer is cached per call site by the caller;
 * this helper keeps no descriptor state of its own.
 * <p>
 * Lookup order keeps every failure mode cheap and quiet: unsupported
 * hosts decline before touching the linker, runtimes without native
 * access decline before resolving anything, and a missing or unusable
 * binding declines instead of throwing. Callers treat {@code null} as
 * "unavailable" and fall back to the {@code test -t} probe.
 */
final class PosixTtyProbe {

    private PosixTtyProbe() {
    }

    /**
     * Downcall holder, initialized on first use only. Deliberately
     * separate from any session or PTY handle tables: a registration
     * gap here must decline this probe, never poison unrelated FFM
     * paths. Kept at runtime initialization for native images, like
     * the other downcall holders.
     */
    private static final class Handles {
        static final MethodHandle ISATTY;

        static {
            MethodHandle isatty = null;
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup stdlib = linker.defaultLookup();
                isatty = linker.downcallHandle(stdlib.find("isatty").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            } catch (RuntimeException | LinkageError e) {
                isatty = null;
            }
            ISATTY = isatty;
        }

        private Handles() {
        }
    }

    /**
     * Whether the FFM isatty binding is usable: supported host ABI and
     * native access enabled for this module.
     *
     * @return true when the native call below can run
     */
    static boolean isNativeAccessEnabled() {
        if (!OSUtils.isFfmPosixSupported(System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""))) {
            return false;
        }
        return PosixTtyProbe.class.getModule().isNativeAccessEnabled();
    }

    /**
     * Test whether a standard descriptor refers to a terminal.
     * <p>
     * A completed native call answers for the descriptor itself:
     * {@code TRUE} for a terminal, {@code FALSE} otherwise (including
     * closed or invalid descriptors). Only a missing capability —
     * unsupported host, denied native access, unresolvable or unusable
     * binding — returns null, and the caller falls back.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return TRUE/FALSE from the native call, or null when unavailable
     */
    static Boolean tryIsTty(int fd) {
        if (!OSUtils.isFfmPosixSupported(System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""))) {
            return null;
        }
        if (!isNativeAccessEnabled()) {
            return null;
        }
        MethodHandle isatty = Handles.ISATTY;
        if (isatty == null) {
            return null;
        }
        try {
            return (int) isatty.invokeExact(fd) != 0 ? Boolean.TRUE : Boolean.FALSE;
        } catch (Throwable t) {
            return null;
        }
    }
}
