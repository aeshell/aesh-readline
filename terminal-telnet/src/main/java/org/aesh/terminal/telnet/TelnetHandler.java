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

/**
 * The handler that defines the callbacks for a telnet connection.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TelnetHandler {

    /** Constructor. */
    public TelnetHandler() {
    }

    /**
     * The telnet connection opened.
     *
     * @param conn the connection
     */
    protected void onOpen(TelnetConnection conn) {
    }

    /**
     * The telnet connection closed.
     */
    protected void onClose() {
    }

    /**
     * Process data sent by the client.
     *
     * @param data the data
     */
    protected void onData(byte[] data) {
    }

    /**
     * Called when the terminal window size changes.
     *
     * @param width the new terminal width in columns
     * @param height the new terminal height in rows
     */
    protected void onSize(int width, int height) {
    }

    /**
     * Called when the terminal type is received from the client.
     *
     * @param terminalType the terminal type string (e.g., "xterm", "vt100")
     */
    protected void onTerminalType(String terminalType) {
    }

    /**
     * Called when a telnet command is received.
     *
     * @param command the command byte
     */
    protected void onCommand(byte command) {
    }

    /**
     * Called when the NAWS (Negotiate About Window Size) option state changes.
     *
     * @param naws true if the client supports NAWS, false otherwise
     */
    protected void onNAWS(boolean naws) {
    }

    /**
     * Called when the ECHO option state changes.
     *
     * @param echo true if echo is enabled, false otherwise
     */
    protected void onEcho(boolean echo) {
    }

    /**
     * Called when the SGA (Suppress Go Ahead) option state changes.
     *
     * @param sga true if SGA is enabled, false otherwise
     */
    protected void onSGA(boolean sga) {
    }

    /**
     * Called when the send binary mode state changes.
     *
     * @param binary true if binary mode is enabled for sending, false otherwise
     */
    protected void onSendBinary(boolean binary) {
    }

    /**
     * Called when the receive binary mode state changes.
     *
     * @param binary true if binary mode is enabled for receiving, false otherwise
     */
    protected void onReceiveBinary(boolean binary) {
    }

}
