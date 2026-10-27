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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalSnapshot;
import org.aesh.terminal.detect.TerminalSubscription;
import org.aesh.terminal.tty.StatusLine;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Two consumers sharing one scoped capability result: a logger and a TUI
 * status line. Change the terminal theme to see both receive the same revision.
 * Type {@code exit} to close the application scope.
 * <p>
 * Subscription is passive. The application explicitly enables and disables
 * protocol notifications. Closing the scope on shutdown or dev-mode restart
 * unsubscribes both consumers before their executor stops.
 */
public class TerminalSubscriptionExample {

    private static final Logger LOGGER = Logger.getLogger(TerminalSubscriptionExample.class.getName());

    public static void main(String[] args) throws IOException {
        final TerminalConnection connection = new TerminalConnection();
        final AtomicBoolean running = new AtomicBoolean(true);
        final StatusLine themeStatus = connection.registerStatusLine(100);
        ExecutorService updates = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "terminal-capability-updates");
            thread.setDaemon(true);
            return thread;
        });
        connection.setCloseHandler(value -> running.set(false));
        connection.enterRawMode();
        // Initialize the connection-owned passive source. This starts no probe.
        connection.terminal();
        try (TerminalDetection owner = connection.attachedDetection();
                TerminalSubscription logging = owner.subscribe(new Consumer<TerminalSnapshot>() {
                    @Override
                    public void accept(TerminalSnapshot snapshot) {
                        LOGGER.info("terminal revision " + snapshot.revision()
                                + ", theme " + snapshot.theme());
                    }
                }, updates);
                TerminalSubscription tui = owner.subscribe(new Consumer<TerminalSnapshot>() {
                    @Override
                    public void accept(TerminalSnapshot snapshot) {
                        // Retain or render this immutable snapshot. Never start
                        // another probe to fill unknown RGB values.
                        themeStatus.setMessage("Terminal theme: " + snapshot.theme()
                                + ", revision: " + snapshot.revision());
                    }
                }, updates)) {
            // This owner operation sends the protocol enable sequence. Merely
            // subscribing above sent nothing and opened no input reader.
            connection.terminal().enableThemeChangeNotification();
            connection.write("Logging and TUI share capability revisions. Type exit to quit.\n");
            Readline readline = ReadlineBuilder.builder().enableHistory(false).build();
            read(connection, readline, running);
            connection.openBlocking();
        } finally {
            updates.shutdownNow();
            shutdown(connection, running);
            themeStatus.close();
            connection.close();
        }
    }

    private static void read(final TerminalConnection connection, final Readline readline,
            final AtomicBoolean running) {
        if (!running.get()) {
            return;
        }
        readline.readline(connection, "$ ", line -> {
            if (line == null || "exit".equals(line)) {
                shutdown(connection, running);
                return;
            }
            if (!line.isEmpty()) {
                connection.printAbove("Unknown command: " + line);
            }
            read(connection, readline, running);
        });
    }

    private static void shutdown(TerminalConnection connection, AtomicBoolean running) {
        if (running.compareAndSet(true, false)) {
            // Notification ownership stays with the application, not with
            // individual logging or TUI subscribers.
            connection.terminal().disableThemeChangeNotification();
            connection.close();
        }
    }
}
