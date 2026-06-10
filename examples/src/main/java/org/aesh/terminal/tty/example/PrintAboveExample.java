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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Demonstrates Connection.printAbove() — printing text above the current
 * prompt without disrupting the user's input.
 * <p>
 * A background thread prints a timestamped notification every 3 seconds.
 * The user can type at the prompt normally — their input is preserved
 * across printAbove calls.
 * <p>
 * Commands:
 * <ul>
 *   <li><b>start</b> — start background notifications (default: on)</li>
 *   <li><b>stop</b> — stop background notifications</li>
 *   <li><b>msg &lt;text&gt;</b> — print a custom message above the prompt</li>
 *   <li><b>exit</b> — quit</li>
 * </ul>
 */
public class PrintAboveExample {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile boolean running = true;
    private static volatile boolean notificationsEnabled = true;

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        connection.enterRawMode();

        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                running = false;
                connection.close();
            }
        });

        connection.setCloseHandler(v -> running = false);

        // Background thread that prints notifications above the prompt
        AtomicInteger counter = new AtomicInteger(0);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notification-thread");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (notificationsEnabled && running) {
                int n = counter.incrementAndGet();
                String time = LocalTime.now().format(TIME_FMT);
                connection.printAbove("\033[33m[" + time + "]\033[0m Notification #" + n
                        + " — your input is preserved!");
            }
        }, 3, 3, TimeUnit.SECONDS);

        connection.write("=== printAbove() Demo ===\n");
        connection.write("A background thread prints notifications every 3 seconds.\n");
        connection.write("Type normally — your input won't be disrupted.\n");
        connection.write("Commands: start, stop, msg <text>, exit\n\n");

        Readline readline = ReadlineBuilder.builder().enableHistory(true).build();
        read(connection, readline);
        connection.openBlocking();

        scheduler.shutdownNow();
    }

    private static void read(TerminalConnection connection, Readline readline) {
        if (!running) return;
        readline.readline(connection, "\033[36m>\033[0m ", line -> {
            if (line == null || "exit".equals(line)) {
                connection.close();
                return;
            }

            if ("stop".equals(line)) {
                notificationsEnabled = false;
                connection.printAbove("\033[31m[Notifications stopped]\033[0m");
            } else if ("start".equals(line)) {
                notificationsEnabled = true;
                connection.printAbove("\033[32m[Notifications started]\033[0m");
            } else if (line.startsWith("msg ")) {
                String msg = line.substring(4);
                connection.printAbove("\033[35m[Custom]\033[0m " + msg);
            } else if (!line.isEmpty()) {
                connection.printAbove("You typed: " + line);
            }

            read(connection, readline);
        });
    }
}
