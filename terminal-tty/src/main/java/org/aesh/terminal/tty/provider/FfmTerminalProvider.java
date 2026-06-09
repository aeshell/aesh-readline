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
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.provider.TerminalProvider;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.tty.impl.Pty;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.OSUtils;

/**
 * Terminal provider using FFM-based PTY (Java 22+, POSIX systems).
 * <p>
 * Loads {@code FfmPty} via reflection (multi-release JAR) to avoid
 * compile-time dependency on Java 22+ APIs.
 */
public class FfmTerminalProvider implements TerminalProvider {

    private static final Logger LOGGER = LoggerUtil.getLogger(FfmTerminalProvider.class.getName());

    @Override
    public String name() {
        return "ffm";
    }

    @Override
    public boolean isSupported() {
        // FFM PTY is only for POSIX (not Windows, not Cygwin)
        return !OSUtils.IS_WINDOWS && !OSUtils.IS_CYGWIN;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Terminal createTerminal(String name, String type, boolean nativeSignals) throws IOException {
        if (type == null) {
            type = System.getenv("TERM");
        }
        try {
            Class<?> ffmPtyClass = Class.forName("org.aesh.terminal.tty.impl.FfmPty");
            Pty pty = (Pty) ffmPtyClass.getMethod("current").invoke(null);
            LOGGER.log(Level.FINE, "Using FFM-based PTY");
            return new PosixSysTerminal(name, type, pty, nativeSignals);
        } catch (ClassNotFoundException e) {
            throw new IOException("FFM PTY not available (requires Java 22+)", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("FFM PTY initialization failed", cause != null ? cause : e);
        } catch (Exception e) {
            throw new IOException("FFM PTY initialization failed", e);
        }
    }
}
