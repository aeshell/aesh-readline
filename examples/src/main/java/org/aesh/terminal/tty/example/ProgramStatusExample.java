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

import org.aesh.terminal.TerminalFeatures;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ProgramStatus;

/**
 * Example program demonstrating OSC 7501 program status reporting.
 * <p>
 * A program reports what it is doing and the terminal decides how to show
 * it: a tab indicator, an inbox entry, a notification, or nothing at all.
 * Reporting is safe everywhere: terminals without support ignore unknown
 * escape sequences, so no support query is required before writing.
 * <p>
 * This demo fakes a two-region deploy: progress on one child record,
 * a permission block on the other, then completion. Every report replaces
 * its record completely, so repeated fields stay repeated.
 */
public class ProgramStatusExample {

    public ProgramStatusExample() {
    }

    /**
     * Main entry point for the program status example.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        TerminalConnection connection;
        try {
            connection = new TerminalConnection();
        } catch (IOException e) {
            System.err.println("Error creating terminal connection: " + e.getMessage());
            System.exit(1);
            return;
        }
        try {
            runExample(connection);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connection.close();
        }
    }

    private static void runExample(TerminalConnection connection) throws InterruptedException {
        // Live queries need the reader pump; reports alone do not.
        // openNonBlocking only submits the pump worker, so wait until it
        // reports reading before querying. The query still degrades to
        // unknown gracefully when the reader never starts.
        connection.openNonBlocking();
        long deadline = System.currentTimeMillis() + 5000;
        while (!connection.reading() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        TerminalFeatures terminal = connection.terminal();
        System.out.println("terminfo Pst hint: " + terminal.hasProgramStatusHint());
        System.out.println("live support query: " + terminal.queryProgramStatusSupport(500));

        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.WORKING)
                .app("deploy")
                .message("Deploying v2.4.1")
                .build());
        for (int progress = 0; progress <= 100; progress += 50) {
            terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.WORKING)
                    .id("deploy/us-east")
                    .title("US East")
                    .progress(progress)
                    .message("Pushing image")
                    .build());
            Thread.sleep(200);
        }
        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.DONE)
                .id("deploy/us-east")
                .title("US East")
                .message("Healthy")
                .build());
        terminal.clearProgramStatus("deploy/us-east");

        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.BLOCKED)
                .kind(ProgramStatus.BlockedKind.PERMISSION)
                .id("deploy/eu-west")
                .title("EU West")
                .message("Approve deploy to eu-west (production)?")
                .build());
        Thread.sleep(500);
        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.WORKING)
                .id("deploy/eu-west")
                .title("EU West")
                .message("Deploying")
                .build());
        Thread.sleep(200);
        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.DONE)
                .id("deploy/eu-west")
                .title("EU West")
                .message("Healthy")
                .build());
        terminal.clearProgramStatus("deploy/eu-west");

        // Done survives the next prompt on purpose: the result is ready
        // and the user has not seen it yet. Never overwrite it with idle.
        terminal.writeProgramStatus(ProgramStatus.builder(ProgramStatus.State.DONE)
                .app("deploy")
                .message("Deployed to 2 regions")
                .build());
        System.out.println("Done. The root done record stays until replaced or cleared.");
    }
}
