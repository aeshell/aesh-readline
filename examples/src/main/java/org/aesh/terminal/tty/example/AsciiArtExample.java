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

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Key;
import org.aesh.terminal.formatting.Color;
import org.aesh.terminal.formatting.TerminalColor;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;

/**
 * Example application demonstrating terminal-api and terminal-tty usage.
 * Displays ASCII art with colors and waits for user input to exit.
 *
 * Run with: mvn exec:java -pl terminal-tty \
 * -Dexec.mainClass="org.aesh.terminal.tty.example.AsciiArtExample"
 */
public class AsciiArtExample {

    public AsciiArtExample() {
    }

    private static final String[] AESH_LOGO = {
            "     _    _____ ____  _   _ ",
            "    / \\  | ____/ ___|| | | |",
            "   / _ \\ |  _| \\___ \\| |_| |",
            "  / ___ \\| |___ ___) |  _  |",
            " /_/   \\_\\_____|____/|_| |_|",
            ""
    };

    private static final String[] COFFEE_CUP = {
            "        ( (",
            "         ) )",
            "      ........",
            "      |      |]",
            "      \\      /",
            "       `----'",
            ""
    };

    /**
     * Main entry point for the ASCII art example.
     *
     * @param args command line arguments (not used)
     */
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

            // Print header
            printColoredLine(conn, "Terminal API Demo", Color.CYAN, true);
            conn.write("\n");

            // Print terminal info
            Size size = conn.size();
            conn.write("Terminal size: " + size.getWidth() + "x" + size.getHeight() + "\n");
            conn.write("Device: " + conn.device().type() + "\n");
            conn.write("Color depth: " + conn.terminal().colorDepth() + "\n");
            conn.write("\n");

            // Print ASCII art logo with color
            printColoredLine(conn, "Welcome to:", Color.WHITE, false);
            for (String line : AESH_LOGO) {
                TerminalString ts = new TerminalString(line,
                        new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT));
                conn.write(ts.toString());
                conn.write("\n");
            }
            conn.write(ANSI.RESET);

            conn.write("\n");

            // Print coffee cup
            printColoredLine(conn, "Have some coffee while you wait:", Color.YELLOW, false);
            for (String line : COFFEE_CUP) {
                TerminalString ts = new TerminalString(line,
                        new TerminalColor(Color.RED, Color.DEFAULT, Color.Intensity.BRIGHT));
                conn.write(ts.toString());
                conn.write("\n");
            }
            conn.write(ANSI.RESET);

            conn.write("\n");

            // Print instructions
            printColoredLine(conn, "Press any key to exit (or Ctrl+C)", Color.MAGENTA, false);
            conn.write("\n");

            // Set up input handler to exit on any key press
            conn.setStdinHandler(input -> {
                if (input != null && input.length > 0) {
                    Key key = Key.findStartKey(input);
                    if (key != null) {
                        conn.write("\nYou pressed: " + key.name() + "\n");
                    } else if (Key.isPrintable(input)) {
                        conn.write("\nKey pressed non-mapped: " + (char) input[0] + " \n");
                    }
                    // Restore attributes before closing
                    conn.setAttributes(savedAttributes);
                    conn.write(ANSI.RESET);
                    conn.write("Goodbye!\n");
                    conn.close();
                }
            });

            // Block and wait for input
            conn.openBlocking();

        } catch (IOException e) {
            System.err.println("Failed to initialize terminal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prints a line of text with the specified color and optional bold styling.
     *
     * @param connection the terminal connection to write to
     * @param text the text to print
     * @param color the foreground color to use
     * @param bold if true, the text will be printed in bold
     */
    private static void printColoredLine(Connection connection, String text, Color color, boolean bold) {
        TerminalString ts = new TerminalString(text,
                new TerminalColor(color, Color.DEFAULT, Color.Intensity.BRIGHT));
        if (bold) {
            connection.write(ANSI.BOLD);
        }
        connection.write(ts.toString());
        connection.write(ANSI.RESET);
        connection.write("\n");
    }
}
