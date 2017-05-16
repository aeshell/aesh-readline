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
package org.aesh.readline;

import org.aesh.terminal.Terminal;
import org.aesh.readline.terminal.impl.LineDisciplineTerminal;
import org.aesh.readline.tty.terminal.TerminalConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TestTerminal {

    private Readline readline;
    private ByteArrayOutputStream byteArrayOutputStream;
    private TerminalConnection connection;
    private LineDisciplineTerminal terminal;
    private List<String> output;
    private Prompt prompt;

    public TestTerminal() {
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();

            terminal = new LineDisciplineTerminal("aesh-test", "term",
                    byteArrayOutputStream);

            connection = new TerminalConnection(terminal);
            output = new ArrayList<>();
            prompt = new Prompt(": ");
            readline = new Readline();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(String input) {
        input.codePoints().forEach(value -> {
            try {
                terminal.processInputByte(value);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void write(int in) {
        try {
            terminal.processInputByte(in);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(int[] in) {
        try {
            for(int i : in)
                terminal.processInputByte(i);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void read(Consumer<String> handler) {
        readline.readline(connection, prompt, handler);
    }

    public void start() {
        connection.openBlocking();
    }

    public String getLine() {
        if(output.size() > 0)
            return output.remove(0);
        else
            return null;
    }

    public String getOutput() {
        return byteArrayOutputStream.toString();
    }

    public void close() {
        try {
            terminal.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Terminal getTerminal() {
        return terminal;
    }
}
