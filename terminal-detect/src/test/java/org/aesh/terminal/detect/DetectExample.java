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

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DetectExample {

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        long elapsed = System.nanoTime() - start;

        System.out.println("--- detect() (env vars only) ---");
        System.out.println("Terminal:       " + caps.terminalName());
        System.out.println("True color:     " + caps.supportsTrueColor());
        System.out.println("256 colors:     " + caps.supports256Colors());
        System.out.println("Image protocol: " + caps.imageProtocol());
        System.out.println("Theme:          " + caps.theme());
        System.out.printf("Detection time:  %.3f ms%n%n", elapsed / 1_000_000.0);

        // Direct synchronous query — JNI/FFM path (cold)
        start = System.nanoTime();
        TerminalColorQuery directResult = TerminalColorQuery.query();
        long directTime = System.nanoTime() - start;
        System.out.printf("Native query (cold): %.3f ms%n", directTime / 1_000_000.0);

        // Direct synchronous query — JNI/FFM path (warm)
        start = System.nanoTime();
        directResult = TerminalColorQuery.query();
        directTime = System.nanoTime() - start;
        System.out.printf("Native query (warm): %.3f ms%n", directTime / 1_000_000.0);

        // Force stty path for comparison
        start = System.nanoTime();
        TerminalColorQuery sttyResult = TerminalColorQuery.queryStty();
        long sttyTime = System.nanoTime() - start;
        System.out.printf("Stty query:          %.3f ms%n", sttyTime / 1_000_000.0);

        start = System.nanoTime();
        TerminalCapabilities async = TerminalCapabilities.detectAsync();
        long returnTime = System.nanoTime() - start;
        System.out.printf("--- detectAsync() (returned in %.3f ms) ---%n", returnTime / 1_000_000.0);

        boolean completed = async.awaitColors(2, TimeUnit.SECONDS);
        elapsed = System.nanoTime() - start;
        System.out.printf("Query wall time: %.3f ms%n", elapsed / 1_000_000.0);

        System.out.println("Query completed: " + completed);
        System.out.println("Theme:          " + async.theme());
        System.out.println("256 colors:     " + async.supports256Colors());
        System.out.println("Foreground:     " + formatRGB(async.foregroundRGB()));
        System.out.println("Background:     " + formatRGB(async.backgroundRGB()));

        Map<Integer, int[]> palette = async.paletteColors();
        if (!palette.isEmpty()) {
            System.out.println("Palette colors:");
            for (Map.Entry<Integer, int[]> entry : palette.entrySet()) {
                int[] rgb = entry.getValue();
                // Show a colored block using true color (ESC[48;2;r;g;bm) + reset (ESC[0m)
                String block = "\033[48;2;" + rgb[0] + ";" + rgb[1] + ";" + rgb[2] + "m    \033[0m";
                System.out.printf("  %2d: %s %s%n", entry.getKey(), block, formatRGB(rgb));
            }
            // Also show all 16 colors in a row as a compact strip
            System.out.print("  Strip: ");
            for (int i = 0; i <= 15; i++) {
                int[] rgb = palette.get(i);
                if (rgb != null) {
                    System.out.printf("\033[48;2;%d;%d;%dm  \033[0m", rgb[0], rgb[1], rgb[2]);
                }
            }
            System.out.println();
        }

        System.out.println("Image protocol: " + async.imageProtocol());
        System.out.printf("Total time:      %.3f ms%n", elapsed / 1_000_000.0);
    }

    private static String formatRGB(int[] rgb) {
        if (rgb == null)
            return "null";
        return "[" + rgb[0] + ", " + rgb[1] + ", " + rgb[2] + "]";
    }
}
