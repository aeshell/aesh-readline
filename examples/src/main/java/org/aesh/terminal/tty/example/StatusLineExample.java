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
import org.aesh.terminal.tty.StatusLine;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Demonstrates StatusLine — persistent status lines between scrolling
 * output and the readline prompt.
 * <p>
 * Simulates a build tool with:
 * <ul>
 *   <li>A build status line (updates as "build" progresses)</li>
 *   <li>A test status line (shows test results)</li>
 *   <li>Log output scrolling above via printAbove()</li>
 *   <li>Readline prompt at the bottom for user commands</li>
 * </ul>
 * <p>
 * Commands: status, build, clear, exit
 */
public class StatusLineExample {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        connection.enterRawMode();

        connection.setCloseHandler(v -> running = false);

        // Register two status lines with different priorities
        StatusLine buildStatus = connection.registerStatusLine(100);
        StatusLine testStatus = connection.registerStatusLine(200);

        buildStatus.setMessage("\033[33m[Build]\033[0m Idle");
        testStatus.setMessage("\033[36m[Tests]\033[0m 0 passed, 0 failed");

        // Background thread simulates build activity
        AtomicInteger buildCount = new AtomicInteger(0);
        AtomicInteger logCount = new AtomicInteger(0);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "build-simulator");
            t.setDaemon(true);
            return t;
        });

        // Simulate log output every 2 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                int n = logCount.incrementAndGet();
                String time = LocalTime.now().format(TIME_FMT);
                connection.printAbove("[" + time + "] Compiling module-" + n + "...");
            }
        }, 2, 2, TimeUnit.SECONDS);

        // Update build status every 3 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                int n = buildCount.incrementAndGet();
                String[] phases = {"Compiling", "Linking", "Optimizing", "Packaging"};
                String phase = phases[n % phases.length];
                buildStatus.setMessage("\033[33m[Build]\033[0m " + phase + "... (" + n + " steps)");
                testStatus.setMessage("\033[36m[Tests]\033[0m " + (n * 3) + " passed, "
                        + (n % 3 == 0 ? "1" : "0") + " failed");
            }
        }, 3, 3, TimeUnit.SECONDS);

        connection.write("=== StatusLine Demo ===\n");
        connection.write("Two status lines are shown above the prompt.\n");
        connection.write("Log output scrolls above the status lines.\n");
        connection.write("Commands: status, build, clear, exit\n\n");

        Readline readline = ReadlineBuilder.builder().enableHistory(true).build();
        read(connection, readline, buildStatus, testStatus);
        connection.openBlocking();

        scheduler.shutdownNow();
    }

    private static void read(TerminalConnection connection, Readline readline,
            StatusLine buildStatus, StatusLine testStatus) {
        if (!running) return;
        readline.readline(connection, "\033[32m$\033[0m ", line -> {
            if (line == null || "exit".equals(line)) {
                buildStatus.close();
                testStatus.close();
                connection.close();
                return;
            }

            if ("status".equals(line)) {
                connection.printAbove("Build: " + buildStatus.getMessage());
                connection.printAbove("Tests: " + testStatus.getMessage());
            } else if ("build".equals(line)) {
                buildStatus.setMessage("\033[32m[Build]\033[0m Complete!");
                testStatus.setMessage("\033[32m[Tests]\033[0m All passed!");
            } else if ("clear".equals(line)) {
                buildStatus.setMessage("\033[33m[Build]\033[0m Idle");
                testStatus.setMessage("\033[36m[Tests]\033[0m 0 passed, 0 failed");
            } else if (!line.isEmpty()) {
                connection.printAbove("Unknown command: " + line);
            }

            read(connection, readline, buildStatus, testStatus);
        });
    }
}
