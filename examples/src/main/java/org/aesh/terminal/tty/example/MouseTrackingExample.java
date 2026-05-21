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

import org.aesh.terminal.tty.MouseEvent;
import org.aesh.terminal.tty.MouseTracking;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Example demonstrating mouse tracking support.
 * <p>
 * Enables SGR mouse tracking and displays mouse events in real-time.
 * Click anywhere in the terminal to see press/release events.
 * Move the mouse to see motion events. Use scroll wheel to see
 * scroll events. Press Ctrl+C to exit.
 * <p>
 * Run with:
 * <pre>
 * mvn compile -pl examples -am -Pexamples
 * java -cp examples/target/classes:readline/target/classes:terminal-api/target/classes:terminal-tty/target/classes:terminal-detect/target/classes:readline-api/target/classes \
 *   org.aesh.readline.example.MouseTrackingExample
 * </pre>
 */
public class MouseTrackingExample {

    public static void main(String... args) throws IOException {
        TerminalConnection connection = new TerminalConnection();

        // Enter raw mode and enable alternate screen
        connection.enterRawMode();
        connection.write("\033[?1049h");  // alternate screen
        connection.write("\033[2J");       // clear
        connection.write("\033[?25l");     // hide cursor

        // Enable mouse tracking with ANY_MOTION protocol + SGR encoding
        MouseTracking.enable(connection, MouseTracking.Protocol.ANY_MOTION);
        MouseTracking.enableEncoding(connection, MouseTracking.Encoding.SGR);

        // Draw header
        Size size = connection.size();
        drawHeader(connection, size);
        drawFooter(connection, size);

        // Register mouse handler
        connection.setMouseHandler(event -> {
            drawEvent(connection, event, size);
            drawMarker(connection, event);
        });

        // Handle Ctrl+C to exit
        connection.setSignalHandler(signal -> {
            cleanup(connection);
            System.exit(0);
        });

        // Handle resize
        connection.setSizeHandler(newSize -> {
            connection.write("\033[2J");
            drawHeader(connection, newSize);
            drawFooter(connection, newSize);
        });

        // Block on input
        connection.openBlocking();
    }

    private static void drawHeader(TerminalConnection connection, Size size) {
        connection.write("\033[1;1H");
        connection.write("\033[1m\033[44m\033[97m");  // bold, blue bg, white
        StringBuilder header = new StringBuilder(" Mouse Tracking Demo ");
        while (header.length() < size.getWidth()) {
            header.append(' ');
        }
        connection.write(header.toString());
        connection.write("\033[0m");
    }

    private static void drawFooter(TerminalConnection connection, Size size) {
        connection.write("\033[" + size.getHeight() + ";1H");
        connection.write("\033[1m\033[44m\033[97m");
        StringBuilder footer = new StringBuilder(" Press Ctrl+C to exit ");
        while (footer.length() < size.getWidth()) {
            footer.append(' ');
        }
        connection.write(footer.toString());
        connection.write("\033[0m");
    }

    private static void drawEvent(TerminalConnection connection, MouseEvent event, Size size) {
        // Display event info at row 3
        connection.write("\033[3;1H\033[K");  // clear line
        connection.write(String.format(" Event: %-8s  Button: %-10s  Position: (%3d, %3d)",
                event.type(), event.button(), event.x(), event.y()));

        // Display modifiers at row 4
        connection.write("\033[4;1H\033[K");
        StringBuilder mods = new StringBuilder(" Modifiers:");
        if (event.shift()) mods.append(" Shift");
        if (event.alt()) mods.append(" Alt");
        if (event.ctrl()) mods.append(" Ctrl");
        if (!event.shift() && !event.alt() && !event.ctrl()) mods.append(" (none)");
        connection.write(mods.toString());
    }

    private static void drawMarker(TerminalConnection connection, MouseEvent event) {
        if (event.x() < 1 || event.y() < 1) return;

        // Draw a marker at the mouse position
        connection.write("\033[" + event.y() + ";" + event.x() + "H");
        switch (event.type()) {
            case PRESS:
                connection.write("\033[91m\033[1m*\033[0m");  // bright red bold
                break;
            case RELEASE:
                connection.write("\033[92m.\033[0m");  // green
                break;
            case DRAG:
                connection.write("\033[93m+\033[0m");  // yellow
                break;
            case MOVE:
                connection.write("\033[90m\u00b7\033[0m");  // gray dot
                break;
            case SCROLL:
                connection.write(event.button() == MouseEvent.Button.SCROLL_UP
                        ? "\033[96m^\033[0m"   // cyan up
                        : "\033[96mv\033[0m"); // cyan down
                break;
        }
    }

    private static void cleanup(TerminalConnection connection) {
        try {
            MouseTracking.disable(connection, MouseTracking.Protocol.ANY_MOTION);
            MouseTracking.disableEncoding(connection, MouseTracking.Encoding.SGR);
            connection.write("\033[?25h");     // show cursor
            connection.write("\033[?1049l");   // main screen
        } catch (Exception e) {
            // ignore on cleanup
        }
    }
}
