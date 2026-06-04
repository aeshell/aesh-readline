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
package org.aesh.readline.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.readline.util.FileAccessPermission;
import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.Parser;

/**
 * Read the history file at init and writeToStdOut to it at shutdown
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class FileHistory extends InMemoryHistory {

    private final File historyFile;
    private final FileAccessPermission historyFilePermission;
    private final boolean logging;
    private static final Logger LOGGER = LoggerUtil.getLogger(FileHistory.class.getName());

    /**
     * Creates a FileHistory with the specified file and maximum size.
     *
     * @param file the history file
     * @param maxSize the maximum number of history entries
     */
    public FileHistory(File file, int maxSize) {
        this(file, maxSize, false);
    }

    /**
     * Creates a FileHistory with the specified file, maximum size, and logging option.
     *
     * @param file the history file
     * @param maxSize the maximum number of history entries
     * @param logging whether to log warnings and errors
     */
    public FileHistory(File file, int maxSize, boolean logging) {
        this(file, maxSize, null, logging);
    }

    /**
     * Creates a FileHistory with full configuration options.
     *
     * @param file the history file
     * @param maxSize the maximum number of history entries
     * @param historyFilePermission the file access permissions to set on the history file
     * @param logging whether to log warnings and errors
     */
    public FileHistory(File file, int maxSize, FileAccessPermission historyFilePermission,
            boolean logging) {
        super(maxSize);
        this.logging = logging;
        historyFile = file;
        this.historyFilePermission = historyFilePermission;
        readFile();
    }

    // Separator between timestamp and command in the history file.
    // Using a character unlikely to appear at the start of a command.
    private static final char TIMESTAMP_SEPARATOR = '\u0000';

    /**
     * Read specified history file to history buffer.
     * <p>
     * Supports two formats:
     * <ul>
     * <li>New format: {@code <epoch_millis>\0<command>} — timestamp preserved</li>
     * <li>Legacy format: {@code <command>} — timestamp set to file modification time</li>
     * </ul>
     */
    private void readFile() {
        if (historyFile.exists()) {
            long fallbackTimestamp = historyFile.lastModified();
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int sepIdx = line.indexOf(TIMESTAMP_SEPARATOR);
                    if (sepIdx > 0) {
                        // New format: timestamp\0command
                        try {
                            long timestamp = Long.parseLong(line.substring(0, sepIdx));
                            String command = line.substring(sepIdx + 1);
                            pushWithTimestamp(Parser.toCodePoints(command), timestamp);
                            continue;
                        } catch (NumberFormatException e) {
                            // Not a valid timestamp — treat as legacy format
                        }
                    }
                    // Legacy format: plain command
                    pushWithTimestamp(Parser.toCodePoints(line), fallbackTimestamp);
                }
            } catch (FileNotFoundException ignored) {
                //AESH-205
            } catch (IOException e) {
                if (logging)
                    LOGGER.log(Level.WARNING, "Failed to read from history file, ", e);
            }
        }
    }

    /**
     * Write the content of the history buffer to file.
     * <p>
     * Uses the new format: {@code <epoch_millis>\0<command>} per line.
     * This preserves timestamps across sessions.
     *
     * @throws IOException io
     */
    private void writeFile() throws IOException {
        historyFile.delete();
        List<Long> timestamps = getTimestamps();
        try (FileWriter fw = new FileWriter(historyFile)) {
            for (int i = 0; i < size(); i++) {
                long ts = (timestamps != null && i < timestamps.size()) ? timestamps.get(i) : System.currentTimeMillis();
                fw.write(String.valueOf(ts));
                fw.write(TIMESTAMP_SEPARATOR);
                fw.write(Parser.fromCodePoints(get(i)));
                fw.write(Config.getLineSeparator());
            }
        }
        if (historyFilePermission != null) {
            historyFile.setReadable(false, false);
            historyFile.setReadable(historyFilePermission.isReadable(), historyFilePermission.isReadableOwnerOnly());
            historyFile.setWritable(false, false);
            historyFile.setWritable(historyFilePermission.isWritable(), historyFilePermission.isWritableOwnerOnly());
            historyFile.setExecutable(false, false);
            historyFile.setExecutable(historyFilePermission.isExecutable(),
                    historyFilePermission.isExecutableOwnerOnly());
        }
    }

    @Override
    public void stop() {
        try {
            writeFile();
        } catch (IOException e) {
            if (logging)
                LOGGER.log(Level.WARNING, "Failed when trying to write history file", e);
        }
    }

}
