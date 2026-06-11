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

import java.util.ArrayList;
import java.util.List;

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.readline.completion.Completion;
import org.aesh.terminal.tty.ScreenRegion;
import org.aesh.terminal.tty.SplitScreen;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Demonstrates split-screen mode (experimental).
 * <p>
 * The terminal is split into two regions:
 * <ul>
 *   <li>Top (2/3): simulated log output scrolling independently</li>
 *   <li>Bottom (1/3): readline prompt for user commands</li>
 * </ul>
 * <p>
 * Commands: log &lt;msg&gt;, clear, exit
 */
public class SplitScreenExample {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        connection.enterRawMode();
        connection.setCloseHandler(v -> running = false);

        // Split the screen: top 2/3 for logs, bottom 1/3 for readline
        SplitScreen split = connection.splitScreen(0.67);
        ScreenRegion logRegion = split.topRegion();

        // Background thread simulates log output
        AtomicInteger logCount = new AtomicInteger(0);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-simulator");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                int n = logCount.incrementAndGet();
                String time = LocalTime.now().format(TIME_FMT);
                String[] levels = {"INFO", "DEBUG", "WARN", "INFO", "INFO"};
                String[] sources = {"io.quarkus.build", "io.netty.channel", "org.hibernate",
                        "io.quarkus.dev", "io.smallrye.config"};
                String level = levels[n % levels.length];
                String source = sources[n % sources.length];
                String color = "WARN".equals(level) ? "\033[33m" : "\033[37m";
                logRegion.writeln(color + "[" + time + "] " + level + "  [" + source + "] "
                        + "Log message #" + n + "\033[0m");
            }
        }, 1, 1, TimeUnit.SECONDS);

        Readline readline = ReadlineBuilder.builder().enableHistory(true).build();
        read(connection, readline, logRegion, getCompletions());
        connection.openBlocking();

        scheduler.shutdownNow();
    }

    private static List<Completion> getCompletions() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            String buf = co.getBuffer();
            for (String cmd : new String[]{"log", "clear", "above", "exit", "help"}) {
                if (cmd.startsWith(buf)) {
                    co.addCompletionCandidate(cmd);
                }
            }
        });
        return completions;
    }

    private static void read(TerminalConnection connection, Readline readline,
            ScreenRegion logRegion, List<Completion> completions) {
        if (!running) return;
        readline.readline(connection, "$ ", line -> {
            if (line == null || "exit".equals(line)) {
                connection.close();
                return;
            }

            if (line.startsWith("log ")) {
                logRegion.writeln("[USER] " + line.substring(4));
            } else if ("clear".equals(line)) {
                logRegion.clear();
            } else if (line.startsWith("above ")) {
                // Test printAbove routing — should go to top region
                connection.printAbove("[ABOVE] " + line.substring(6));
            } else if ("help".equals(line)) {
                logRegion.writeln("Commands: log <msg>, clear, above <msg>, exit, help");
                logRegion.writeln("Try tab completion and Ctrl+R fuzzy search");
            } else if (!line.isEmpty()) {
                logRegion.writeln("[CMD] Unknown: " + line);
            }

            read(connection, readline, logRegion, completions);
        }, completions);
    }
}
