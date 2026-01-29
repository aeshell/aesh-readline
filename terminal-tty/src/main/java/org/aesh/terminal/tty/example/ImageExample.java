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

import org.aesh.terminal.Attributes;
import org.aesh.terminal.image.ImageProtocol;
import org.aesh.terminal.image.TerminalImage;
import org.aesh.terminal.image.TerminalImageBuilder;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;

/**
 * Example demonstrating terminal image display.
 * <p>
 * Usage: java ImageExample [image_path]
 * <p>
 * If no image path is provided, a simple test pattern is generated.
 */
public class ImageExample {

    public static void main(String[] args) {
        try {
            TerminalConnection conn = new TerminalConnection();

            // Enter raw mode for immediate key handling
            Attributes savedAttributes = conn.enterRawMode();

            // Handle Ctrl+C signal
            conn.setSignalHandler(signal -> {
                if (signal == Signal.INT) {
                    conn.setAttributes(savedAttributes);
                    conn.write(ANSI.RESET);
                    conn.write("\nInterrupted!\n");
                    conn.close();
                }
            });

            // Clear screen and move cursor to home position
            conn.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
            conn.write("\u001B[H"); // Move cursor to home (1,1)

            // Check for image support
            ImageProtocol protocol = conn.device().getImageProtocol();

            conn.write("\033[1;36m=== Terminal Image Example ===\033[0m\n\n");
            conn.write("Terminal type: " + conn.device().type() + "\n");
            conn.write("Image protocol: " + protocol + "\n\n");

            if (protocol == ImageProtocol.NONE) {
                conn.write("\033[1;33mNo image protocol support detected.\033[0m\n");
                conn.write("\nSupported terminals:\n");
                conn.write("  - Kitty, Ghostty (use Kitty graphics protocol)\n");
                conn.write("  - iTerm2, WezTerm, VSCode, Tabby, Hyper (use iTerm2 protocol)\n");
                conn.write("  - Konsole (partial Kitty support)\n");
                conn.write("  - mlterm, foot, contour (use Sixel protocol)\n");
                conn.write("\nNote: Alacritty does not support terminal graphics.\n");
                conn.write("\nPress any key to exit...\n");
            } else {
                // Get or generate image data
                byte[] imageData;
                String filename;

                if (args.length > 0) {
                    Path imagePath = Paths.get(args[0]);
                    if (!Files.exists(imagePath)) {
                        conn.write("\033[1;31mFile not found: " + args[0] + "\033[0m\n");
                        conn.write("\nPress any key to exit...\n");
                        setupExitHandler(conn, savedAttributes);
                        conn.openBlocking();
                        return;
                    }
                    imageData = Files.readAllBytes(imagePath);
                    filename = imagePath.getFileName().toString();
                    conn.write("Loading image: " + filename + " (" + imageData.length + " bytes)\n\n");
                } else {
                    // Generate a simple test PNG
                    imageData = generateTestPng();
                    filename = "test.png";
                    conn.write("No image specified, using generated test pattern.\n\n");
                }

                // Build and display the image
                TerminalImage image = TerminalImageBuilder.builder(conn.device())
                        .data(imageData)
                        .filename(filename)
                        .widthCells(40)
                        .build();

                if (image != null) {
                    conn.write(image.encode());
                    conn.write("\n\n");
                    conn.write("\033[1;32mImage displayed successfully!\033[0m\n");
                } else {
                    conn.write("\033[1;31mFailed to create image.\033[0m\n");
                }

                conn.write("\nPress any key to exit...\n");
            }

            setupExitHandler(conn, savedAttributes);
            conn.openBlocking();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupExitHandler(TerminalConnection conn, Attributes savedAttributes) {
        conn.setStdinHandler(input -> {
            if (input != null && input.length > 0) {
                conn.setAttributes(savedAttributes);
                conn.write(ANSI.RESET);
                conn.write("Goodbye!\n");
                conn.close();
            }
        });
    }

    /**
     * Generate a minimal valid PNG for testing.
     * Creates a small 8x8 pixel pattern.
     */
    private static byte[] generateTestPng() {
        // This is a pre-computed minimal PNG with a simple pattern
        // For real usage, you'd use ImageIO or similar
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x08, // 8x8 pixels
                0x08, 0x02, 0x00, 0x00, 0x00, 0x4B, 0x6D, 0x29, // 8-bit RGB
                (byte) 0xDE,
                0x00, 0x00, 0x00, 0x31, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
                0x78, (byte) 0x9C, 0x62, (byte) 0xFC, (byte) 0xCF, 0x00, 0x04, (byte) 0x8C,
                0x19, (byte) 0xFE, 0x03, 0x31, 0x23, 0x03, 0x03, 0x03,
                0x23, 0x03, 0x23, 0x10, 0x33, 0x32, 0x30, 0x30,
                0x30, 0x32, 0x30, 0x02, 0x31, 0x23, 0x03, 0x03,
                0x03, 0x23, 0x03, 0x23, 0x10, 0x33, 0x00, 0x00,
                0x00, (byte) 0xAF, 0x00, 0x1F, (byte) 0xF6, (byte) 0xB7, (byte) 0xEC, 0x11,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND chunk
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
