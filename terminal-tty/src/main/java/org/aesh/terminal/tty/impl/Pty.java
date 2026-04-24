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
package org.aesh.terminal.tty.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;

/**
 * Represents a pseudo-terminal (PTY) providing master/slave input and output streams.
 * A PTY allows a program to interact with a terminal device, providing both
 * the master side (for the controlling process) and the slave side (for the terminal).
 */
public interface Pty extends Closeable {

    /**
     * Returns the master side input stream.
     *
     * @return the master input stream
     */
    InputStream getMasterInput();

    /**
     * Returns the master side output stream.
     *
     * @return the master output stream
     */
    OutputStream getMasterOutput();

    /**
     * Returns the slave side input stream.
     *
     * @return the slave input stream
     */
    InputStream getSlaveInput();

    /**
     * Returns the slave side output stream.
     *
     * @return the slave output stream
     */
    OutputStream getSlaveOutput();

    /**
     * Returns the current terminal attributes.
     *
     * @return the terminal attributes
     * @throws IOException if an I/O error occurs
     */
    Attributes getAttr() throws IOException;

    /**
     * Sets the terminal attributes.
     *
     * @param attr the attributes to set
     * @throws IOException if an I/O error occurs
     */
    void setAttr(Attributes attr) throws IOException;

    /**
     * Returns the current terminal size.
     *
     * @return the terminal size
     * @throws IOException if an I/O error occurs
     */
    Size getSize() throws IOException;

}
