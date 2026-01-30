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
package org.aesh.terminal.tty;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.tty.impl.CygwinPty;
import org.aesh.terminal.tty.impl.ExecPty;
import org.aesh.terminal.tty.impl.ExternalTerminal;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.tty.impl.Pty;
import org.aesh.terminal.tty.impl.WinExternalTerminal;
import org.aesh.terminal.tty.impl.WinSysTerminal;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.OSUtils;

/**
 * Builder for creating Terminal instances with configurable input/output streams and settings.
 *
 * @author <a href="mailto:spederse@redhat.com">Stale W. Pedersen</a>
 */
public final class TerminalBuilder {

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalBuilder.class.getName());

    /**
     * Creates and returns a default system Terminal.
     *
     * @return a new Terminal instance
     * @throws IOException if an I/O error occurs while creating the terminal
     */
    public static Terminal console() throws IOException {
        return builder().build();
    }

    /**
     * Creates a new TerminalBuilder instance.
     *
     * @return a new TerminalBuilder
     */
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

    private TerminalBuilder apply(Consumer<TerminalBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Sets the terminal name.
     *
     * @param name the terminal name
     * @return this builder for method chaining
     */
    public TerminalBuilder name(String name) {
        return apply(c -> c.name = name);
    }

    /**
     * Sets the input stream for the terminal.
     *
     * @param in the input stream
     * @return this builder for method chaining
     */
    public TerminalBuilder input(InputStream in) {
        return apply(c -> c.in = in);
    }

    /**
     * Sets the output stream for the terminal.
     *
     * @param out the output stream
     * @return this builder for method chaining
     */
    public TerminalBuilder output(OutputStream out) {
        return apply(c -> c.out = out);
    }

    /**
     * Sets whether to use the system terminal.
     *
     * @param system true to use system terminal, false otherwise
     * @return this builder for method chaining
     */
    public TerminalBuilder system(boolean system) {
        return apply(c -> c.system = system);
    }

    /**
     * Sets whether to use native signal handling.
     *
     * @param nativeSignals true to enable native signals, false otherwise
     * @return this builder for method chaining
     */
    public TerminalBuilder nativeSignals(boolean nativeSignals) {
        return apply(c -> c.nativeSignals = nativeSignals);
    }

    /**
     * Sets the terminal type (e.g., "xterm", "ansi", "dumb").
     *
     * @param type the terminal type
     * @return this builder for method chaining
     */
    public TerminalBuilder type(String type) {
        return apply(c -> c.type = type);
    }

    /**
     * Builds and returns a Terminal instance with the configured settings.
     *
     * @return a new Terminal instance
     * @throws IOException if an I/O error occurs while creating the terminal
     */
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
                } catch (IOException ioe) {
                    //we might have a windows terminal created from cygwin..
                    return createWindowsTerminal(name);
                }
            } else if (OSUtils.IS_WINDOWS) {
                return createWindowsTerminal(name);
            } else {
                String type = this.type;
                if (type == null) {
                    type = System.getenv("TERM");
                }
                Pty pty = null;
                try {
                    pty = ExecPty.current();
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Failed to get a local tty", e);
                }
                if (pty != null) {
                    return new PosixSysTerminal(name, type, pty, nativeSignals);
                } else {
                    return new ExternalTerminal(name, type, (in == null) ? System.in : in, (out == null) ? System.out : out);
                }
            }
        } else {
            return new ExternalTerminal(name, type, (in == null) ? System.in : in,
                    (out == null) ? System.out : out);
        }
    }

    private Terminal createWindowsTerminal(String name) throws IOException {
        try {
            Console console = System.console();
            if (console != null && isTerminal(console)) // a native terminal, not redirects etc
                return new WinSysTerminal(name, nativeSignals);
            else {
                return new WinExternalTerminal(name, type, (in == null) ? System.in : in,
                        (out == null) ? System.out : out);
            }
        } catch (IOException e) {
            return new WinExternalTerminal(name, type, (in == null) ? System.in : in,
                    (out == null) ? System.out : out);
        }
    }

    // Console.isTerminal() was introduced in Java 22
    private static boolean isTerminal(Console console) {
        if (console == null) {
            return false;
        }
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            try {
                return (boolean) isTerminal.invoke(console);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to invoke System.console().isTerminal() via Reflection API", e);
                return false;
            }
        } catch (NoSuchMethodException e) {
            return true; // for Java <= 21
        }
    }
}
