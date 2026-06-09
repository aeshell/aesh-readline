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
import org.aesh.terminal.tty.TtyDetect;
import org.aesh.terminal.tty.impl.WinSysTerminal;
import org.aesh.terminal.utils.OSUtils;

/**
 * Terminal provider for Windows system console (JNI or FFM).
 * <p>
 * Only supported on Windows with a real console (not redirected).
 * The import of {@link WinSysTerminal} is safe on non-Windows platforms
 * because Java only loads the class on first active use (inside
 * {@link #createTerminal}), which is gated by {@link #isSupported()}.
 */
public class WinSysTerminalProvider implements TerminalProvider {

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public boolean isSupported() {
        if (!OSUtils.IS_WINDOWS || OSUtils.IS_CYGWIN) {
            return false;
        }
        // Check for a real console, not piped/redirected
        if (System.console() == null) {
            return false;
        }
        String term = System.getenv("TERM");
        if ("dumb".equals(term)) {
            return false;
        }
        return TtyDetect.isStdinTty();
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Terminal createTerminal(String name, String type, boolean nativeSignals) throws IOException {
        return new WinSysTerminal(name, nativeSignals);
    }
}
