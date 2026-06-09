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
package org.aesh.terminal.provider;

import java.io.IOException;

import org.aesh.terminal.Terminal;

/**
 * Service Provider Interface for terminal implementations.
 * <p>
 * Providers are discovered via {@link java.util.ServiceLoader} and selected
 * based on {@link #isSupported()} and {@link #priority()}. The provider with
 * the highest priority that reports itself as supported is used first. If it
 * fails to create a terminal, the next provider is tried.
 * <p>
 * Built-in providers include:
 * <ul>
 * <li>FFM-based PTY (Java 22+, POSIX) — priority 100</li>
 * <li>Windows system console (JNI/FFM) — priority 100</li>
 * <li>Cygwin PTY — priority 75</li>
 * <li>Exec-based PTY (POSIX) — priority 50</li>
 * <li>External terminal (fallback) — priority 10</li>
 * </ul>
 * <p>
 * Third-party implementations can provide custom terminal backends by
 * implementing this interface and registering via
 * {@code META-INF/services/org.aesh.terminal.provider.TerminalProvider}.
 */
public interface TerminalProvider {

    /**
     * Returns the name of this provider, for logging and diagnostics.
     *
     * @return the provider name
     */
    String name();

    /**
     * Whether this provider is supported in the current environment.
     * <p>
     * This should be a fast check based on OS detection, Java version,
     * available classes, etc. It should NOT attempt to open a terminal.
     *
     * @return true if this provider can potentially create a terminal
     */
    boolean isSupported();

    /**
     * The priority of this provider. Higher values are preferred.
     * <p>
     * When multiple providers are supported, the one with the highest
     * priority is tried first. If it fails, the next is tried.
     * <p>
     * Recommended ranges:
     * <ul>
     * <li>100+ — native/FFM providers (best performance)</li>
     * <li>50-99 — process-based providers (exec/JNI)</li>
     * <li>1-49 — fallback providers (dumb terminals, external)</li>
     * </ul>
     *
     * @return the priority (higher = preferred)
     */
    int priority();

    /**
     * Creates a system terminal.
     * <p>
     * This is called for terminals connected to the local console
     * (stdin/stdout). The provider should create and return a fully
     * initialized terminal, or throw {@link IOException} if it cannot.
     *
     * @param name the terminal name (for display/logging)
     * @param type the terminal type (e.g., "xterm-256color"), or null to auto-detect
     * @param nativeSignals whether to enable native signal handling
     * @return a new Terminal instance
     * @throws IOException if the terminal cannot be created
     */
    Terminal createTerminal(String name, String type, boolean nativeSignals) throws IOException;
}
