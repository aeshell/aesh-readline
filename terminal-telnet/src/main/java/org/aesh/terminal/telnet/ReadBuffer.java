/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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

package org.aesh.terminal.telnet;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Buffer for queuing and dispatching terminal input data to a read handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ReadBuffer implements Consumer<int[]> {

    private final Queue<int[]> queue = new ArrayDeque<>(10);
    private final Executor executor;
    private volatile Consumer<int[]> readHandler;

    /**
     * Creates a new ReadBuffer with the specified executor.
     *
     * @param executor the executor used for dispatching data to the read handler
     */
    public ReadBuffer(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void accept(int[] data) {
        queue.add(data);
        while (readHandler != null && !queue.isEmpty()) {
            data = queue.poll();
            if (data != null) {
                readHandler.accept(data);
            }
        }
    }

    /**
     * Returns the current read handler.
     *
     * @return the read handler, or null if none is set
     */
    public Consumer<int[]> getReadHandler() {
        return readHandler;
    }

    /**
     * Sets the read handler for receiving buffered data.
     * When a handler is set, any queued data will be drained to the handler.
     *
     * @param readHandler the handler to receive data, or null to clear the handler
     */
    public void setReadHandler(final Consumer<int[]> readHandler) {
        if (readHandler != null) {
            if (this.readHandler != null) {
                this.readHandler = readHandler;
            } else {
                ReadBuffer.this.readHandler = readHandler;
                drainQueue();
            }
        } else {
            this.readHandler = null;
        }
    }

    private void drainQueue() {
        if (!queue.isEmpty() && readHandler != null) {
            executor.execute(() -> {
                if (readHandler != null) {
                    final int[] data = queue.poll();
                    if (data != null) {
                        readHandler.accept(data);
                        drainQueue();
                    }
                }
            });
        }
    }
}
