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

/**
 * Warmed stty-size probe cost: many back-to-back {@code querySttySize}
 * calls in one JVM, mimicking repeated terminal width queries during a
 * single render pass. Warms up first so class loading and one-time
 * lookups do not dominate, then times the steady-state loop.
 * <p>
 * Usage: {@code java ... SttySizeProbe [warmup] [measured]}. Output is
 * one JSON line; {@code present} counts non-null answers.
 */
public final class SttySizeProbe {

    private SttySizeProbe() {
    }

    /**
     * Run one warmed measurement and print JSON timings.
     *
     * @param args optional warmup and measured iteration counts
     */
    public static void main(String[] args) {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int measured = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        for (int i = 0; i < warmup; i++) {
            TerminalSizeQuery.querySttySize();
        }
        int present = 0;
        long start = System.nanoTime();
        for (int i = 0; i < measured; i++) {
            if (TerminalSizeQuery.querySttySize() != null) {
                present++;
            }
        }
        long totalNanos = System.nanoTime() - start;
        System.out.println("{\"present\":" + present
                + ",\"calls\":" + measured
                + ",\"totalNanos\":" + totalNanos + "}");
    }
}
