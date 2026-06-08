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

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Demonstrates terminal focus tracking.
 * <p>
 * Switch between terminal windows to see focus gained/lost events.
 * The prompt changes to indicate the current focus state.
 * <p>
 * Press Ctrl+D or type "exit" to quit.
 * <p>
 * Note: if running inside tmux, focus events require
 * {@code set -g focus-events on} in tmux.conf.
 */
public class FocusTrackingExample {

    private static boolean focused = true;
    private static volatile boolean stopped = false;
    private static volatile boolean interrupted = false;

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        Attributes savedAttr = connection.enterRawMode();

        // Signal handler sets a flag — Readline's INT handler calls this
        // before finish(""), so the requestHandler can check the flag
        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                interrupted = true;
            }
        });

        connection.setCloseHandler(v -> {
            stopped = true;
            try {
                connection.terminal().disableFocusTracking();
                connection.setAttributes(savedAttr);
            } catch (Exception | java.io.IOError ignored) {
            }
        });

        // Enable focus tracking
        connection.terminal().enableFocusTracking(isFocused -> {
            focused = isFocused;
            if (isFocused) {
                connection.write("\r\033[2K\033[32m[FOCUSED]\033[0m Switch to another window to see focus lost\r\n");
            } else {
                connection.write("\r\033[2K\033[31m[UNFOCUSED]\033[0m Switch back to see focus gained\r\n");
            }
        });

        connection.write("=== Focus Tracking Example ===\n");
        connection.write("Switch between terminal windows to see focus events.\n");
        connection.write("Type 'exit' or press Ctrl+D to quit.\n\n");

        Readline readline = ReadlineBuilder.builder().enableHistory(true).build();
        read(connection, readline);
        connection.openBlocking();
    }

    private static void read(TerminalConnection connection, Readline readline) {
        if (stopped) {
            return;
        }
        String prompt = focused ? "\033[32m>\033[0m " : "\033[31m>\033[0m ";
        readline.readline(connection, prompt, line -> {
            // Ctrl+D (EOF) or "exit"
            if (line == null || "exit".equals(line)) {
                connection.close();
                return;
            }
            // Ctrl+C sets interrupted flag, finish("") delivers empty string
            if (interrupted) {
                interrupted = false;
                connection.close();
                return;
            }
            if (!line.isEmpty()) {
                connection.write("  -> " + line + "\n");
            }
            read(connection, readline);
        });
    }
}
