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
package org.aesh.terminal.tty.utils;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Utility for executing external processes and capturing their output.
 *
 * @author Ståle W. Pedersen
 */
public final class ProcessHelper {

    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private ProcessHelper() {
    }

    /**
     * Execute a command and capture its output.
     *
     * @param command the command and arguments
     * @return the process result
     */
    public static ProcessResult execute(String... command) {
        return execute(DEFAULT_TIMEOUT_MS, command);
    }

    /**
     * Execute a command with a timeout and capture its output.
     *
     * @param timeoutMs timeout in milliseconds
     * @param command the command and arguments
     * @return the process result
     */
    public static ProcessResult execute(long timeoutMs, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, output.toString().trim());
            }

            return new ProcessResult(process.exitValue(), output.toString().trim());
        } catch (Exception e) {
            return new ProcessResult(-1, "");
        }
    }

    /**
     * Result of a process execution.
     */
    public static final class ProcessResult {
        private final int exitCode;
        private final String output;

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        /**
         * Method.
         *
         * @return the result
         */
        public int exitCode() {
            return exitCode;
        }

        /**
         * Method.
         *
         * @return the result
         */
        public String output() {
            return output;
        }

        /**
         * Method.
         *
         * @return the result
         */
        public boolean success() {
            return exitCode == 0;
        }
    }
}
