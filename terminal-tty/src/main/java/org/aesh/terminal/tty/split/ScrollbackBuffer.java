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
package org.aesh.terminal.tty.split;

import java.util.ArrayList;
import java.util.List;

/**
 * A circular buffer that stores lines for a screen region's scrollback history.
 * <p>
 * When the buffer reaches its maximum capacity, the oldest lines are dropped.
 * Thread-safe for concurrent reads and writes.
 */
public class ScrollbackBuffer {

    private final String[] lines;
    private final int capacity;
    private int head; // index of the oldest line
    private int size; // number of lines currently stored

    /**
     * Creates a scrollback buffer with the specified capacity.
     *
     * @param maxLines maximum number of lines to store
     */
    public ScrollbackBuffer(int maxLines) {
        this.capacity = maxLines;
        this.lines = new String[maxLines];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Add a line to the buffer. If the buffer is full, the oldest line is dropped.
     *
     * @param line the line to add
     */
    public synchronized void addLine(String line) {
        int idx = (head + size) % capacity;
        lines[idx] = line;
        if (size < capacity) {
            size++;
        } else {
            // Buffer full — advance head (drop oldest)
            head = (head + 1) % capacity;
        }
    }

    /**
     * Get the last N lines from the buffer (most recent at the end).
     *
     * @param count maximum number of lines to return
     * @return list of lines, oldest first
     */
    public synchronized List<String> getLastLines(int count) {
        int n = Math.min(count, size);
        List<String> result = new ArrayList<>(n);
        int start = (head + size - n) % capacity;
        for (int i = 0; i < n; i++) {
            result.add(lines[(start + i) % capacity]);
        }
        return result;
    }

    /**
     * Returns the number of lines currently stored.
     *
     * @return the line count
     */
    public synchronized int size() {
        return size;
    }

    /**
     * Clear all stored lines.
     */
    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            lines[i] = null;
        }
        head = 0;
        size = 0;
    }
}
