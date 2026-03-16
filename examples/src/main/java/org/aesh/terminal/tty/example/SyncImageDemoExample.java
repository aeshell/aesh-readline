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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.formatting.Color;
import org.aesh.terminal.formatting.TerminalColor;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.image.ImageProtocol;
import org.aesh.terminal.image.TerminalImage;
import org.aesh.terminal.image.TerminalImageBuilder;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;

/**
 * Demonstrates Mode 2026 (synchronized output) combined with inline image
 * display. The image bounces around the screen like a screensaver, making
 * tearing very obvious when synchronized output is disabled.
 * <p>
 * Usage: mvn exec:java -pl terminal-tty \
 * -Dexec.mainClass="org.aesh.terminal.tty.example.SyncImageDemoExample" \
 * -Dexec.args="/tmp/gutta_brett.jpg"
 * <p>
 * Keys:
 * <ul>
 * <li>[S] toggle synchronized output on/off</li>
 * <li>[+]/[-] zoom image in/out</li>
 * <li>[&lt;]/[&gt;] decrease/increase speed</li>
 * <li>[Q] quit</li>
 * </ul>
 */
public class SyncImageDemoExample {

    private static volatile boolean running = true;
    private static volatile boolean syncEnabled = true;
    private static volatile int imageCells = 30;
    private static volatile double speed = 1.0;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String[] SPINNER = { "\u25DC", "\u25DD", "\u25DE", "\u25DF" };

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: SyncImageDemoExample <image-path>");
            System.err.println("  e.g. SyncImageDemoExample /tmp/image.jpg");
            return;
        }

        Path imagePath = Paths.get(args[0]);
        if (!Files.exists(imagePath)) {
            System.err.println("File not found: " + args[0]);
            return;
        }

        TerminalConnection connection = null;
        Attributes savedAttributes = null;
        int frame = 0;
        try {
            connection = new TerminalConnection();
            savedAttributes = connection.enterRawMode();

            boolean terminalSupportsSync = connection.terminal().supportsSynchronizedOutput();
            ImageProtocol imageProtocol = connection.device().getImageProtocol();

            byte[] imageData = Files.readAllBytes(imagePath);
            String filename = imagePath.getFileName().toString();
            long fileSize = imageData.length;

            connection.setSignalHandler(signal -> {
                if (signal == Signal.INT)
                    running = false;
            });

            connection.setStdinHandler(input -> {
                if (input != null && input.length > 0) {
                    int key = input[0];
                    if (key == 's' || key == 'S')
                        syncEnabled = !syncEnabled;
                    else if (key == 'q' || key == 'Q')
                        running = false;
                    else if (key == '+' || key == '=')
                        imageCells = Math.min(60, imageCells + 5);
                    else if (key == '-' || key == '_')
                        imageCells = Math.max(10, imageCells - 5);
                    else if (key == '>' || key == '.')
                        speed = Math.min(5.0, speed + 0.5);
                    else if (key == '<' || key == ',')
                        speed = Math.max(0.5, speed - 0.5);
                }
            });

            connection.openNonBlocking();

            Size size = connection.size();
            int width = size.getWidth();
            int height = size.getHeight();

            connection.write(ANSI.CURSOR_HIDE);

            StringBuilder buffer = new StringBuilder(16384);
            long lastTime = System.currentTimeMillis();

            // Bouncing image position and velocity
            // Use doubles for smooth sub-cell movement
            double imgCol = 3;
            double imgRow = 3;
            double velCol = 1.2;
            double velRow = 0.6;

            // Approximate image height in cells (assume ~aspect ratio 3:2 photo)
            int imageHeightCells = imageCells * 2 / 3;

            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastTime < 40) { // ~25 fps for visible tearing
                    Thread.sleep(5);
                    continue;
                }
                lastTime = now;

                // Recalculate image height based on current zoom
                imageHeightCells = Math.max(3, imageCells * 2 / 3);

                // Bounce boundaries: row 2 (below title) to height-1 (above footer)
                int minCol = 1;
                int maxCol = Math.max(1, width - imageCells);
                int minRow = 2;
                int maxRow = Math.max(2, height - imageHeightCells - 1);

                // Update position
                imgCol += velCol * speed;
                imgRow += velRow * speed;

                // Bounce off edges
                if (imgCol <= minCol) {
                    imgCol = minCol;
                    velCol = Math.abs(velCol);
                } else if (imgCol >= maxCol) {
                    imgCol = maxCol;
                    velCol = -Math.abs(velCol);
                }
                if (imgRow <= minRow) {
                    imgRow = minRow;
                    velRow = Math.abs(velRow);
                } else if (imgRow >= maxRow) {
                    imgRow = maxRow;
                    velRow = -Math.abs(velRow);
                }

                int curCol = (int) imgCol;
                int curRow = (int) imgRow;

                buffer.setLength(0);

                // --- Begin synchronized frame ---
                if (syncEnabled)
                    buffer.append(ANSI.MODE_2026_ENABLE);

                // Clear screen
                buffer.append("\u001B[2J");

                // ===== Title bar =====
                moveCursor(buffer, 1, 1);
                String syncLabel = syncEnabled ? "ON " : "OFF";
                String title = " Bouncing Image    Sync: " + syncLabel
                        + "    [S] toggle  [+/-] zoom  [</>] speed  [Q] quit ";
                TerminalString titleStr = new TerminalString(
                        padRight(title, width),
                        new TerminalColor(Color.WHITE, Color.BLUE, Color.Intensity.BRIGHT));
                buffer.append(titleStr.toString());

                // ===== Image at bouncing position =====
                moveCursor(buffer, curRow, curCol);

                if (imageProtocol != ImageProtocol.NONE) {
                    TerminalImage image = TerminalImageBuilder.builder(imageProtocol)
                            .data(imageData)
                            .filename(filename)
                            .widthCells(imageCells)
                            .preserveAspectRatio(true)
                            .build();

                    if (image != null) {
                        buffer.append(image.encode());
                    }
                } else {
                    // No image protocol — draw a bouncing box placeholder
                    TerminalColor borderColor = new TerminalColor(
                            Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT);
                    int boxW = Math.min(imageCells, width - curCol);
                    int boxH = Math.max(3, imageHeightCells);
                    moveCursor(buffer, curRow, curCol);
                    buffer.append(new TerminalString(
                            "\u250C" + repeat('\u2500', Math.max(0, boxW - 2)) + "\u2510", borderColor));
                    for (int i = 1; i < boxH - 1; i++) {
                        moveCursor(buffer, curRow + i, curCol);
                        buffer.append(new TerminalString(
                                "\u2502" + repeat(' ', Math.max(0, boxW - 2)) + "\u2502", borderColor));
                    }
                    // Label in center
                    int labelRow = curRow + boxH / 2;
                    String label = filename;
                    if (label.length() > boxW - 4)
                        label = label.substring(0, boxW - 4);
                    int labelCol = curCol + Math.max(1, (boxW - label.length()) / 2);
                    moveCursor(buffer, labelRow, labelCol);
                    buffer.append(new TerminalString(label,
                            new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT)));
                    moveCursor(buffer, curRow + boxH - 1, curCol);
                    buffer.append(new TerminalString(
                            "\u2514" + repeat('\u2500', Math.max(0, boxW - 2)) + "\u2518", borderColor));
                }

                // ===== Info overlay (top-right corner) =====
                int infoCol = Math.max(1, width - 35);
                moveCursor(buffer, 2, infoCol);
                buffer.append(new TerminalString(
                        String.format(" %s  %s ", filename, formatSize(fileSize)),
                        new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT)));
                moveCursor(buffer, 3, infoCol);
                buffer.append(new TerminalString(
                        String.format(" Protocol: %-8s  Zoom: %2d ", imageProtocol, imageCells),
                        new TerminalColor(Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT)));
                moveCursor(buffer, 4, infoCol);
                String syncStatus = terminalSupportsSync ? "Mode 2026" : "N/A";
                buffer.append(new TerminalString(
                        String.format(" Sync hw: %-10s Speed: %.1fx ", syncStatus, speed),
                        new TerminalColor(Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT)));

                // ===== Footer / status bar =====
                moveCursor(buffer, height, 1);
                String spinner = SPINNER[frame % SPINNER.length];
                String clock = LocalTime.now().format(TIME_FMT);
                String footer = " " + spinner + " " + clock + "  Frame: " + frame
                        + "  Sync: " + syncLabel
                        + "  |  Press [S] to toggle — watch for tearing!";
                TerminalString footerStr = new TerminalString(
                        padRight(footer, width),
                        new TerminalColor(Color.WHITE, Color.BLUE, Color.Intensity.BRIGHT));
                buffer.append(footerStr.toString());

                buffer.append(ANSI.RESET);

                // --- End synchronized frame ---
                if (syncEnabled)
                    buffer.append(ANSI.MODE_2026_DISABLE);

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
                    connection.write("Bouncing image demo finished. Rendered " + frame + " frames.\n");
                    if (savedAttributes != null)
                        connection.setAttributes(savedAttributes);
                    connection.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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
