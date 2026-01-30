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
package org.aesh.terminal;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Signal;

/**
 * Decodes terminal input events, separating signals (INT, SUSP, EOF) from regular input.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class EventDecoder implements Consumer<int[]> {

    private final int intr;
    private final int susp;
    private final int eof;

    private Consumer<Signal> signalHandler;
    private Consumer<int[]> inputHandler;

    private Queue<int[]> inputQueue = new ArrayDeque<>(10);

    /**
     * Create a new EventDecoder with default control character values.
     * Default values: INTR=3 (Ctrl+C), EOF=4 (Ctrl+D), SUSP=26 (Ctrl+Z).
     */
    public EventDecoder() {
        intr = 3;
        eof = 4;
        susp = 26;
    }

    /**
     * Create a new EventDecoder with custom control character values.
     *
     * @param intr the interrupt character code (typically Ctrl+C = 3)
     * @param eof the end-of-file character code (typically Ctrl+D = 4)
     * @param susp the suspend character code (typically Ctrl+Z = 26)
     */
    public EventDecoder(int intr, int eof, int susp) {
        this.intr = intr;
        this.eof = eof;
        this.susp = susp;
    }

    /**
     * Create a new EventDecoder using control characters from terminal attributes.
     * Falls back to default values if the attributes do not specify valid control characters.
     *
     * @param attributes the terminal attributes to extract control characters from
     */
    public EventDecoder(Attributes attributes) {
        this.intr = attributes.getControlChar(Attributes.ControlChar.VINTR) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VINTR)
                : 3;
        this.eof = attributes.getControlChar(Attributes.ControlChar.VEOF) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VEOF)
                : 4;
        this.susp = attributes.getControlChar(Attributes.ControlChar.VSUSP) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VSUSP)
                : 26;
    }

    /**
     * Get the current signal handler.
     *
     * @return the signal handler, or null if not set
     */
    public Consumer<Signal> getSignalHandler() {
        return signalHandler;
    }

    /**
     * Set the signal handler that will be called when signals are detected in input.
     *
     * @param signalHandler the handler to process signals
     */
    public void setSignalHandler(Consumer<Signal> signalHandler) {
        this.signalHandler = signalHandler;
    }

    /**
     * Get the current input handler.
     *
     * @return the input handler, or null if not set
     */
    public Consumer<int[]> getInputHandler() {
        return inputHandler;
    }

    /**
     * Set the input handler that will receive non-signal input.
     * Any queued input will be delivered to the handler immediately.
     *
     * @param inputHandler the handler to process input as code point arrays
     */
    public void setInputHandler(Consumer<int[]> inputHandler) {
        this.inputHandler = inputHandler;
        checkQueue();
    }

    private void checkQueue() {
        while (inputHandler != null && !inputQueue.isEmpty())
            inputHandler.accept(inputQueue.poll());
    }

    /**
     * Process input, separating signals from regular input.
     * Signal characters are extracted and sent to the signal handler,
     * while remaining input is sent to the input handler.
     *
     * @param input the input code points to process
     */
    @Override
    public void accept(int[] input) {
        if (signalHandler != null) {
            int index = 0;
            while (index < input.length) {
                int val = input[index];
                Signal event = null;
                if (val == intr) {
                    event = Signal.INT;
                } else if (val == susp) {
                    event = Signal.SUSP;
                } else if (val == eof) {
                    event = Signal.EOF;
                }
                if (event != null) {
                    if (signalHandler != null) {
                        if (inputHandler != null) {
                            int[] a = new int[index];
                            if (index > 0) {
                                System.arraycopy(input, 0, a, 0, index);
                                inputHandler.accept(a);
                            }
                        }
                        signalHandler.accept(event);
                        int[] a = new int[input.length - index - 1];
                        System.arraycopy(input, index + 1, a, 0, a.length);
                        input = a;
                        index = 0;
                        continue;
                    }
                }
                index++;
            }
        }
        if (input.length > 0) {
            if (inputHandler != null)
                inputHandler.accept(input);
            else
                inputQueue.add(input);
        }
    }
}
