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

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;


public interface Terminal extends Closeable {

    String getName();

    // Signal support
    interface SignalHandler {
        void handle(Signal signal);
    }

    SignalHandler handle(Signal signal, SignalHandler handler);

    void raise(Signal signal);

    InputStream input();

    OutputStream output();

    //set echo attribute on terminal
    boolean echo();

    boolean echo(boolean echo);

    Attributes getAttributes();

    void setAttributes(Attributes attr);

    Size getSize();

    // Infocmp capabilities
    Device device();

}
