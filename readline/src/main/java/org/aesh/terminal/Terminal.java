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

import org.aesh.terminal.impl.NativeSignalHandler;
import org.aesh.tty.Signal;
import org.aesh.tty.Size;
import org.aesh.tty.Capability;

import java.io.Closeable;
import java.io.Flushable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;


public interface Terminal extends Closeable, Flushable {

    String getName();

    //
    // Signal support
    //
    interface SignalHandler {

        SignalHandler SIG_DFL = NativeSignalHandler.SIG_DFL;
        SignalHandler SIG_IGN = NativeSignalHandler.SIG_IGN;

        void handle(Signal signal);
    }

    SignalHandler handle(Signal signal, SignalHandler handler);

    void raise(Signal signal);

    PrintWriter writer();

    InputStream input();

    OutputStream output();

    //
    // Pty settings
    //
    Attributes enterRawMode();

    boolean echo();

    boolean echo(boolean echo);

    Attributes getAttributes();

    void setAttributes(Attributes attr);

    Size getSize();

    default int getWidth() {
        return getSize().getWidth();
    }

    default int getHeight() {
        return getSize().getHeight();
    }

    void flush();

    //
    // Infocmp capabilities
    //

    String getType();

    boolean puts(Capability capability, Object... params);

    boolean getBooleanCapability(Capability capability);

    Integer getNumericCapability(Capability capability);

    String getStringCapability(Capability capability);

}
