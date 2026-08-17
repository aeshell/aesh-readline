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
package org.aesh.terminal.tty;

import java.util.function.Consumer;

/**
 * Processes TTY output by converting line feeds to carriage return + line feed sequences.
 * This is equivalent to 'stty onlcr'.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyOutputMode implements Consumer<int[]> {

    private final Consumer<int[]> readHandler;

    /**
     * Create a new TTY output mode processor.
     *
     * @param readHandler the handler to receive processed output
     */
    public TtyOutputMode(Consumer<int[]> readHandler) {
        this.readHandler = readHandler;
    }

    @Override
    public void accept(int[] data) {
        if (readHandler == null || data.length == 0) {
            return;
        }

        // Count newlines to determine if substitution is needed
        int nlCount = 0;
        for (int cp : data) {
            if (cp == '\n') {
                nlCount++;
            }
        }

        // Fast path: no newlines, forward as-is (no allocation)
        if (nlCount == 0) {
            readHandler.accept(data);
            return;
        }

        // Build single output array with \n -> \r\n substitution.
        // This produces one downstream accept() call instead of 2*N+1
        // (where N is the number of newlines).
        int[] out = new int[data.length + nlCount];
        int j = 0;
        for (int cp : data) {
            if (cp == '\n') {
                out[j++] = '\r';
                out[j++] = '\n';
            } else {
                out[j++] = cp;
            }
        }
        readHandler.accept(out);
    }
}
