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
import java.lang.reflect.Method;
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

    /**
     * Creates a new {@code FfmTerminalProvider}.
     */
    public FfmTerminalProvider() {
    }

    private static final Logger LOGGER = LoggerUtil.getLogger(FfmTerminalProvider.class.getName());

    // Cached FfmPty reflection handles. Looked up once on first use —
    // TerminalBuilder probes isSupported() on every provider and then
    // calls createTerminal() on the winner, so uncached lookups would
    // repeat Class.forName + getMethod on every terminal creation.
    // A benign race may repeat the lookup; all threads compute the same values.
    private static volatile Class<?> cachedFfmPty;
    private static volatile Method cachedIsNativeAccessEnabled;
    private static volatile Method cachedCurrent;

    /**
     * Resolve and cache the FfmPty reflection handles.
     *
     * @return true if FfmPty is available (Java 22+)
     */
    private static boolean resolveFfmPty() {
        if (cachedFfmPty != null) {
            return true;
        }
        try {
            Class<?> clazz = Class.forName("org.aesh.terminal.tty.impl.FfmPty");
            Method access = clazz.getMethod("isNativeAccessEnabled");
            Method current = clazz.getMethod("current");
            cachedIsNativeAccessEnabled = access;
            cachedCurrent = current;
            cachedFfmPty = clazz;
            return true;
        } catch (Exception e) {
            // Pre-Java 22: FfmPty class doesn't exist
            return false;
        }
    }

    @Override
    public String name() {
        return "ffm";
    }

    @Override
    public boolean isSupported() {
        // FFM PTY is only for POSIX (not Windows, not Cygwin)
        if (OSUtils.IS_WINDOWS || OSUtils.IS_CYGWIN) {
            return false;
        }
        // Check if native access is enabled. Without it, creating FFM
        // downcall handles triggers a JVM warning (and will be blocked
        // in a future JDK release). Falls back silently to ExecPty.
        // The check is delegated to FfmPty.isNativeAccessEnabled() in
        // the java22 MRJAR overlay via reflection — cached on first use,
        // with no overhead from loading FFM/Linker classes.
        if (!resolveFfmPty()) {
            return false;
        }
        try {
            return (Boolean) cachedIsNativeAccessEnabled.invoke(null);
        } catch (Exception e) {
            return false;
        }
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
        if (!resolveFfmPty()) {
            throw new IOException("FFM PTY not available (requires Java 22+)");
        }
        try {
            Pty pty = (Pty) cachedCurrent.invoke(null);
            LOGGER.log(Level.FINE, "Using FFM-based PTY");
            return new PosixSysTerminal(name, type, pty, nativeSignals);
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
