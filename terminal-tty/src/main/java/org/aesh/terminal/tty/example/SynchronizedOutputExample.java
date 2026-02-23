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
package org.aesh.terminal.tty.example;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.formatting.Color;
import org.aesh.terminal.formatting.TerminalColor;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;

/**
 * Demonstrates Mode 2026 (synchronized output) by rendering a dashboard
 * with many screen elements updated per frame.
 * <p>
 * Press 's' to toggle synchronized output on/off. Without it the terminal
 * may tear — individual write operations become visible as partial frames.
 * With synchronized output enabled, the terminal buffers all writes and
 * paints them atomically.
 * <p>
 * Supported terminals: Kitty, Ghostty, WezTerm, foot, Contour, iTerm2, mintty.
 * On unsupported terminals the BSU/ESU sequences are silently ignored.
 * <p>
 * Run with: mvn exec:java -pl terminal-tty \
 * -Dexec.mainClass="org.aesh.terminal.tty.example.SynchronizedOutputExample"
 */
public class SynchronizedOutputExample {

    private static volatile boolean running = true;
    private static volatile boolean syncEnabled = true;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final Color[] BAR_COLORS = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    };

    private static final String[] SPINNER = { "\u2580", "\u2584", "\u2588", "\u2584" };

    public static void main(String[] args) {
        TerminalConnection connection = null;
        Attributes savedAttributes = null;
        int frame = 0;
        try {
            connection = new TerminalConnection();
            savedAttributes = connection.enterRawMode();

            boolean terminalSupportsSync = connection.supportsSynchronizedOutput();

            connection.setSignalHandler(signal -> {
                if (signal == Signal.INT) {
                    running = false;
                }
            });

            connection.setStdinHandler(input -> {
                if (input != null && input.length > 0) {
                    int key = input[0];
                    if (key == 's' || key == 'S') {
                        syncEnabled = !syncEnabled;
                    } else if (key == 'q' || key == 'Q') {
                        running = false;
                    }
                }
            });

            connection.openNonBlocking();

            Size size = connection.size();
            int width = size.getWidth();
            int height = size.getHeight();

            connection.write(ANSI.CURSOR_HIDE);
            connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);

            frame = 0;
            long lastTime = System.currentTimeMillis();

            // Simulated metrics that change every frame
            double[] cpuCores = new double[6];
            double networkIn = 0;
            double networkOut = 0;
            int requestCount = 0;

            StringBuilder buffer = new StringBuilder(8192);

            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastTime < 40) { // ~25 fps for visible tearing
                    Thread.sleep(5);
                    continue;
                }
                lastTime = now;

                // Update simulated metrics
                for (int i = 0; i < cpuCores.length; i++) {
                    cpuCores[i] = 20 + 60 * Math.abs(Math.sin((frame + i * 7) * 0.05));
                }
                networkIn = 50 + 40 * Math.sin(frame * 0.03);
                networkOut = 30 + 25 * Math.sin(frame * 0.04 + 1);
                requestCount = 100 + (int) (80 * Math.sin(frame * 0.02));

                buffer.setLength(0);

                // --- Begin frame ---
                if (syncEnabled) {
                    buffer.append(ANSI.MODE_2026_ENABLE);
                }

                // Clear screen
                buffer.append("\u001B[2J");

                // Title bar
                moveCursor(buffer, 1, 1);
                String syncLabel = syncEnabled ? "ON " : "OFF";
                String title = " System Dashboard    Sync: " + syncLabel
                        + "    [S] toggle sync  [Q] quit ";
                TerminalString titleStr = new TerminalString(
                        padRight(title, width),
                        new TerminalColor(Color.WHITE, Color.BLUE, Color.Intensity.BRIGHT));
                buffer.append(titleStr.toString());

                // Clock
                moveCursor(buffer, 3, 3);
                String clock = LocalTime.now().format(TIME_FMT);
                TerminalString clockStr = new TerminalString(
                        "Time: " + clock,
                        new TerminalColor(Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(clockStr.toString());

                // Frame counter
                moveCursor(buffer, 3, 35);
                buffer.append("Frame: ").append(frame);
                buffer.append("  FPS: ~25");

                // Spinner
                moveCursor(buffer, 3, Math.max(1, width - 10));
                TerminalString spinStr = new TerminalString(
                        SPINNER[frame % SPINNER.length],
                        new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(spinStr.toString());

                // Sync status indicator
                moveCursor(buffer, 5, 3);
                if (terminalSupportsSync) {
                    TerminalString supportStr = new TerminalString(
                            "Terminal supports Mode 2026 (synchronized output)",
                            new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT));
                    buffer.append(supportStr.toString());
                } else {
                    TerminalString noSupportStr = new TerminalString(
                            "Terminal does not advertise Mode 2026 support (sequences are still safe to send)",
                            new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.BRIGHT));
                    buffer.append(noSupportStr.toString());
                }

                // Section: CPU Usage
                moveCursor(buffer, 7, 3);
                TerminalString cpuHeader = new TerminalString(
                        "CPU Usage",
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(ANSI.BOLD).append(cpuHeader.toString()).append(ANSI.RESET);

                int barWidth = Math.min(40, width - 20);
                for (int i = 0; i < cpuCores.length; i++) {
                    moveCursor(buffer, 8 + i, 3);
                    buffer.append("Core ").append(i).append(": ");
                    drawBar(buffer, cpuCores[i], 100, barWidth, BAR_COLORS[i % BAR_COLORS.length]);
                    buffer.append(String.format(" %5.1f%%", cpuCores[i]));
                }

                // Section: Network I/O
                int netRow = 8 + cpuCores.length + 1;
                moveCursor(buffer, netRow, 3);
                TerminalString netHeader = new TerminalString(
                        "Network I/O",
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(ANSI.BOLD).append(netHeader.toString()).append(ANSI.RESET);

                moveCursor(buffer, netRow + 1, 3);
                buffer.append("  In:  ");
                drawBar(buffer, networkIn, 100, barWidth, Color.GREEN);
                buffer.append(String.format(" %5.1f MB/s", networkIn));

                moveCursor(buffer, netRow + 2, 3);
                buffer.append("  Out: ");
                drawBar(buffer, networkOut, 100, barWidth, Color.CYAN);
                buffer.append(String.format(" %5.1f MB/s", networkOut));

                // Section: Request rate sparkline
                int sparkRow = netRow + 4;
                moveCursor(buffer, sparkRow, 3);
                TerminalString reqHeader = new TerminalString(
                        "Request Rate",
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(ANSI.BOLD).append(reqHeader.toString()).append(ANSI.RESET);

                moveCursor(buffer, sparkRow + 1, 3);
                buffer.append("  ").append(requestCount).append(" req/s  ");
                // Mini sparkline using block characters
                char[] sparks = { '\u2581', '\u2582', '\u2583', '\u2584', '\u2585', '\u2586', '\u2587', '\u2588' };
                for (int i = 0; i < 30; i++) {
                    double val = Math.abs(Math.sin((frame - 30 + i) * 0.08));
                    int idx = (int) (val * (sparks.length - 1));
                    Color sparkColor = val > 0.7 ? Color.RED : (val > 0.4 ? Color.YELLOW : Color.GREEN);
                    TerminalString spark = new TerminalString(
                            String.valueOf(sparks[idx]),
                            new TerminalColor(sparkColor, Color.DEFAULT, Color.Intensity.BRIGHT));
                    buffer.append(spark.toString());
                }

                // Section: Process table (many rows to stress rendering)
                int tableRow = sparkRow + 3;
                moveCursor(buffer, tableRow, 3);
                TerminalString tableHeader = new TerminalString(
                        "Active Processes",
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(ANSI.BOLD).append(tableHeader.toString()).append(ANSI.RESET);

                moveCursor(buffer, tableRow + 1, 3);
                TerminalString colHeader = new TerminalString(
                        String.format("  %-8s %-20s %8s %8s %8s", "PID", "NAME", "CPU%", "MEM%", "STATUS"),
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT));
                buffer.append(colHeader.toString());

                String[] processNames = {
                        "java", "node", "nginx", "postgres", "redis",
                        "elasticsearch", "kafka", "zookeeper"
                };
                String[] statuses = { "running", "running", "running", "sleeping", "running",
                        "running", "idle", "running" };

                int maxProcs = Math.min(processNames.length, height - tableRow - 4);
                for (int i = 0; i < maxProcs; i++) {
                    moveCursor(buffer, tableRow + 2 + i, 3);
                    double cpu = 5 + 30 * Math.abs(Math.sin((frame + i * 13) * 0.04));
                    double mem = 10 + 20 * Math.abs(Math.cos((frame + i * 9) * 0.03));
                    Color statusColor = statuses[i].equals("running") ? Color.GREEN
                            : (statuses[i].equals("idle") ? Color.YELLOW : Color.CYAN);
                    String line = String.format("  %-8d %-20s %7.1f%% %7.1f%% ",
                            1000 + i * 111, processNames[i], cpu, mem);
                    buffer.append(line);
                    TerminalString statusStr = new TerminalString(
                            String.format("%8s", statuses[i]),
                            new TerminalColor(statusColor, Color.DEFAULT, Color.Intensity.BRIGHT));
                    buffer.append(statusStr.toString());
                }

                // Footer
                moveCursor(buffer, height, 1);
                TerminalString footer = new TerminalString(
                        padRight(" Press [S] to toggle synchronized output, [Q] to quit", width),
                        new TerminalColor(Color.WHITE, Color.BLUE, Color.Intensity.BRIGHT));
                buffer.append(footer.toString());

                buffer.append(ANSI.RESET);

                // --- End frame ---
                if (syncEnabled) {
                    buffer.append(ANSI.MODE_2026_DISABLE);
                }

                // Write entire frame in one call
                connection.write(buffer.toString());

                frame++;
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.write(ANSI.MODE_2026_DISABLE + ANSI.CURSOR_SHOW + "\u001B[2J\u001B[H");
                    connection.write("Synchronized output example finished.\n");
                    connection.write("Rendered " + frame + " frames.\n");
                    if (savedAttributes != null) {
                        connection.setAttributes(savedAttributes);
                    }
                    connection.close();
                } catch (Exception ignore) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    private static void drawBar(StringBuilder buffer, double value, double max, int width, Color color) {
        int filled = (int) ((value / max) * width);
        filled = Math.max(0, Math.min(filled, width));

        TerminalString filledStr = new TerminalString(
                repeat('\u2588', filled),
                new TerminalColor(color, Color.DEFAULT, Color.Intensity.BRIGHT));
        buffer.append(filledStr.toString());

        TerminalString emptyStr = new TerminalString(
                repeat('\u2591', width - filled),
                new TerminalColor(Color.DEFAULT, Color.DEFAULT, Color.Intensity.NORMAL));
        buffer.append(emptyStr.toString());
    }

    private static String repeat(char c, int count) {
        if (count <= 0)
            return "";
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width)
            return s.substring(0, width);
        char[] pad = new char[width - s.length()];
        java.util.Arrays.fill(pad, ' ');
        return s + new String(pad);
    }

    private static void moveCursor(StringBuilder buffer, int row, int col) {
        buffer.append("\u001B[").append(row).append(';').append(col).append('H');
    }
}
