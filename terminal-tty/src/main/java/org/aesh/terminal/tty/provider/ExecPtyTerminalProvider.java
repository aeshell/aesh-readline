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
package org.aesh.terminal.tty.provider;

import java.io.IOException;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.provider.TerminalProvider;
import org.aesh.terminal.tty.impl.ExecPty;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.utils.OSUtils;

/**
 * Terminal provider using exec-based PTY (stty/tty commands).
 * <p>
 * Works on all POSIX systems with Java 8+. Lower priority than the
 * FFM provider since it spawns external processes for terminal control.
 */
public class ExecPtyTerminalProvider implements TerminalProvider {

    /**
     * Creates a new {@code ExecPtyTerminalProvider}.
     */
    public ExecPtyTerminalProvider() {
    }

    @Override
    public String name() {
        return "exec";
    }

    @Override
    public boolean isSupported() {
        return !OSUtils.IS_WINDOWS && !OSUtils.IS_CYGWIN;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Terminal createTerminal(String name, String type, boolean nativeSignals) throws IOException {
        if (type == null) {
            type = System.getenv("TERM");
        }
        return new PosixSysTerminal(name, type, ExecPty.current(), nativeSignals);
    }
}
