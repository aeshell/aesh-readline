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

import org.aesh.terminal.tty.Signal;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class EventDecoder implements Consumer<int[]> {

    private final int intr;
    private final int susp;
    private final int eof;

    private Consumer<Signal> signalHandler;
    private Consumer<int[]> inputHandler;

    public EventDecoder() {
        intr = 3;
        eof = 4;
        susp = 26;
    }

    public EventDecoder(int intr, int eof, int susp) {
        this.intr = intr;
        this.eof = eof;
        this.susp = susp;
    }

    public EventDecoder(Attributes attributes) {
        this.intr = attributes.getControlChar(Attributes.ControlChar.VINTR) > 0 ? attributes.getControlChar(Attributes.ControlChar.VINTR) : 3;
        this.eof = attributes.getControlChar(Attributes.ControlChar.VEOF) > 0 ? attributes.getControlChar(Attributes.ControlChar.VEOF) : 4;
        this.susp = attributes.getControlChar(Attributes.ControlChar.VSUSP) > 0 ? attributes.getControlChar(Attributes.ControlChar.VSUSP) : 26;
    }

    public Consumer<Signal> getSignalHandler() {
        return signalHandler;
    }

    public void setSignalHandler(Consumer<Signal> signalHandler) {
        this.signalHandler = signalHandler;
    }

    public Consumer<int[]> getInputHandler() {
        return inputHandler;
    }

    public void setInputHandler(Consumer<int[]> inputHandler) {
        this.inputHandler = inputHandler;
     }

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
        if (input.length > 0 && inputHandler != null)
            inputHandler.accept(input);
    }
}
