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
 * Cold-start probe comparison: builtin subprocess vs FFM isatty.
 * <p>
 * One scenario per JVM invocation; the caller measures process lifetime
 * externally. Touches nothing before the timed call — no Linker, no
 * loggers, no providers — so the first timing includes full class and
 * native initialization.
 * <p>
 * Usage: {@code java ... TtyStartupProbe shell}, {@code ... ffm}, or
 * {@code ... combined}. The shell arm calls {@code TtyDetect.isTty}
 * (public entry point, fresh read per fd); the ffm arm calls the
 * package-private fast path directly; the combined arm calls the
 * caching convenience methods in {@code AeshRuntimeRunner} order
 * (stdout, then stderr, then all three), exercising the one-child
 * stdout+stderr probe. Output is one JSON line; {@code null} answers
 * print as {@code null}, never as fabricated booleans.
 */
public final class TtyStartupProbe {

    private TtyStartupProbe() {
    }

    /**
     * Run one cold-start scenario and print JSON timings.
     *
     * @param args single scenario name: {@code shell}, {@code ffm},
     *        or {@code combined}
     */
    public static void main(String[] args) {
        String which = args.length == 0 ? "shell" : args[0];
        boolean combined = "combined".equals(which);
        boolean shell = !combined && !"ffm".equals(which);
        String scenario = combined ? "tty-combined" : shell ? "tty-shell" : "tty-ffm";

        long start = System.nanoTime();
        Boolean first = combined ? TtyDetect.isStdoutTty() : probe(shell, 1);
        long firstNanos = System.nanoTime() - start;

        start = System.nanoTime();
        Boolean repeat = combined ? TtyDetect.isStderrTty() : probe(shell, 1);
        long repeatNanos = System.nanoTime() - start;

        long allStart = System.nanoTime();
        Boolean fd0 = combined ? TtyDetect.isStdinTty() : probe(shell, 0);
        Boolean fd1 = combined ? TtyDetect.isStdoutTty() : probe(shell, 1);
        Boolean fd2 = combined ? TtyDetect.isStderrTty() : probe(shell, 2);
        long allNanos = System.nanoTime() - allStart;

        System.out.println("{\"scenario\":\"" + scenario + "\""
                + ",\"first\":" + first
                + ",\"firstNanos\":" + firstNanos
                + ",\"repeat\":" + repeat
                + ",\"repeatNanos\":" + repeatNanos
                + ",\"fd0\":" + fd0
                + ",\"fd1\":" + fd1
                + ",\"fd2\":" + fd2
                + ",\"allThreeNanos\":" + allNanos + "}");
    }

    private static Boolean probe(boolean shell, int fd) {
        if (shell) {
            return TtyDetect.isTty(fd) ? Boolean.TRUE : Boolean.FALSE;
        }
        return PosixTtyProbe.tryIsTty(fd);
    }
}
