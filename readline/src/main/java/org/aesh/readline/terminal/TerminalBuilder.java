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
package org.aesh.readline.terminal;

import org.aesh.readline.terminal.impl.CygwinPty;
import org.aesh.readline.terminal.impl.ExecPty;
import org.aesh.readline.terminal.impl.ExternalTerminal;
import org.aesh.readline.terminal.impl.PosixSysTerminal;
import org.aesh.readline.terminal.impl.Pty;
import org.aesh.readline.terminal.impl.WinExternalTerminal;
import org.aesh.readline.terminal.impl.WinSysTerminal;
import org.aesh.utils.OSUtils;
import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aesh.terminal.Terminal;

public final class TerminalBuilder {

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalBuilder.class.getName());

    public static Terminal console() throws IOException {
        return builder().build();
    }

    public static TerminalBuilder builder() {
        return new TerminalBuilder();
    }

    private String name;
    private InputStream in;
    private OutputStream out;
    private String type;
    private Boolean system;
    private boolean nativeSignals = true;

    private TerminalBuilder() {
    }

    private TerminalBuilder apply(Consumer<TerminalBuilder> consumer)  {
        consumer.accept(this);
        return this;
    }

    public TerminalBuilder name(String name) {
        return apply(c -> c.name = name);
    }

    public TerminalBuilder input(InputStream in) {
        return apply(c -> c.in = in);
    }

    public TerminalBuilder output(OutputStream out) {
        return apply(c -> c.out = out);
    }

    public TerminalBuilder system(boolean system) {
        return apply(c -> c.system = system);
    }

    public TerminalBuilder nativeSignals(boolean nativeSignals) {
        return apply(c -> c.nativeSignals = nativeSignals);
    }

    public TerminalBuilder type(String type) {
        return apply(c -> c.type = type);
    }

    public Terminal build() throws IOException {
        String name = this.name;
        if (name == null) {
            name = "Aesh console";
        }
        if ((system != null && system)
                || (system == null
                    && (in == null || in == System.in)
                    && (out == null || out == System.out))) {

            // Cygwin support
            if (OSUtils.IS_CYGWIN) {
                String type = this.type;
                if (type == null) {
                    type = System.getenv("TERM");
                }
                try {
                    Pty pty = CygwinPty.current();
                    return new PosixSysTerminal(name, type, pty, nativeSignals);
                }
                catch(IOException ioe) {
                    //we might have a windows terminal created from cygwin..
                    return createWindowsTerminal(name);
                }
            }
            else if (OSUtils.IS_WINDOWS) {
                return createWindowsTerminal(name);
            }
            else {
                String type = this.type;
                if (type == null) {
                    type = System.getenv("TERM");
                }
                Pty pty = null;
                try {
                    pty = ExecPty.current();
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to get a local tty", e);
                }
                if (pty != null) {
                    return new PosixSysTerminal(name, type, pty, nativeSignals);
                } else {
                    return new ExternalTerminal(name, type, (in == null) ? System.in : in, (out == null) ? System.out : out);
                }
            }
        }
        else {
            return new ExternalTerminal(name, type, (in == null) ? System.in : in,
                    (out == null) ? System.out : out);
        }
    }

    private Terminal createWindowsTerminal(String name) throws IOException {
        try {
            //if console != null its a native terminal, not redirects etc
            if(System.console() != null)
                return new WinSysTerminal(name, nativeSignals);
            else {
                return new WinExternalTerminal(name, type, (in == null) ? System.in : in,
                        (out == null) ? System.out : out);
            }
        }
        catch(IOException e) {
            return new WinExternalTerminal(name, type, (in == null) ? System.in : in,
                    (out == null) ? System.out : out);
        }
    }
}
