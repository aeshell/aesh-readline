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
package org.aesh.terminal.tty.example;

import java.io.IOException;

import org.aesh.terminal.ssh.ShellExample;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Runs the ShellExample directly on a local terminal using TerminalConnection.
 * <p>
 * This provides a simple interactive shell with commands like echo, sleep,
 * help, exit, cursor, keyscan, linescan, top, and window.
 * <p>
 * Try pressing Ctrl+R for fuzzy history search after entering a few commands.
 */
public class ShellExampleTty {

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        new ShellExample("> ").accept(connection);
    }
}
