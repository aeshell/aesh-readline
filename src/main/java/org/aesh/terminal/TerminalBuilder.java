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

import org.aesh.terminal.impl.CygwinPty;
import org.aesh.terminal.impl.ExecPty;
import org.aesh.terminal.impl.ExternalTerminal;
import org.aesh.terminal.impl.PosixSysTerminal;
import org.aesh.terminal.impl.Pty;
import org.aesh.terminal.impl.WinSysTerminal;
import org.aesh.terminal.utils.OSUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

public final class TerminalBuilder {

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
    private String encoding;
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

    public TerminalBuilder encoding(String encoding) {
        return apply(c -> c.encoding = encoding);
    }

    public Terminal build() throws IOException {
        String name = this.name;
        if (name == null) {
            name = "Aesh console";
        }
        String encoding = this.encoding;
        if (encoding == null) {
            encoding = Charset.defaultCharset().name();
        }
        if ((system != null && system)
                || (system == null
                    && (in == null || in == System.in)
                    && (out == null || out == System.out))) {
            //
            // Cygwin support
            //
            if (OSUtils.IS_CYGWIN) {
                String type = this.type;
                if (type == null) {
                    type = System.getenv("TERM");
                }
                Pty pty = CygwinPty.current();
                return new PosixSysTerminal(name, type, pty, encoding, nativeSignals);
            }
            else if (OSUtils.IS_WINDOWS) {
                try {
                    return new WinSysTerminal(name, nativeSignals);
                }
                catch(IOException e) {
                    return new ExternalTerminal(name, type, in, out, encoding);
                }
            }
            else if(OSUtils.IS_HPUX) {
                //TODO: need to parse differently than "normal" PosixSysTerminals...
                // just fallback to ExternalTerminal for now
                return new ExternalTerminal(name, type, System.in, System.out, encoding);
            }
            else {
                String type = this.type;
                if (type == null) {
                    type = System.getenv("TERM");
                }
                Pty pty = null;
                try {
                    pty = ExecPty.current();
                } catch (IOException e) {
                    // Ignore
                }
                if (pty != null) {
                    return new PosixSysTerminal(name, type, pty, encoding, nativeSignals);
                } else {
                    return new ExternalTerminal(name, type, System.in, System.out, encoding);
                }
            }
        } else {
            return new ExternalTerminal(name, type, in, out, encoding);
        }
    }
}
