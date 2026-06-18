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
import org.aesh.terminal.tty.impl.CygwinPty;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.utils.OSUtils;

/**
 * Terminal provider for Cygwin/MSYS2 environments on Windows.
 * <p>
 * Uses a POSIX-style PTY via Cygwin's /dev/tty.
 */
public class CygwinTerminalProvider implements TerminalProvider {

    /**
     * Creates a new {@code CygwinTerminalProvider}.
     */
    public CygwinTerminalProvider() {
    }

    @Override
    public String name() {
        return "cygwin";
    }

    @Override
    public boolean isSupported() {
        return OSUtils.IS_CYGWIN;
    }

    @Override
    public int priority() {
        return 75;
    }

    @Override
    public Terminal createTerminal(String name, String type, boolean nativeSignals) throws IOException {
        if (type == null) {
            type = System.getenv("TERM");
        }
        return new PosixSysTerminal(name, type, CygwinPty.current(), nativeSignals);
    }
}
