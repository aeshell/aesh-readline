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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.provider.TerminalProvider;
import org.aesh.terminal.tty.impl.ExternalTerminal;
import org.aesh.terminal.tty.impl.WinExternalTerminal;
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
     * <p>
     * For system terminals (stdin/stdout), discovers providers via
     * {@link ServiceLoader} and tries them in priority order. Falls back
     * to {@link ExternalTerminal} if no provider succeeds.
     *
     * @return a new Terminal instance
     * @throws IOException if an I/O error occurs while creating the terminal
     */
    public Terminal build() throws IOException {
        String name = this.name;
        if (name == null) {
            name = "Aesh console";
        }
        if (isSystemTerminal()) {
            return buildSystemTerminal(name);
        } else {
            return buildExternalTerminal(name);
        }
    }

    /**
     * Whether we should try to create a system terminal (connected to
     * the local console) vs an external terminal (explicit streams).
     */
    private boolean isSystemTerminal() {
        return (system != null && system)
                || (system == null
                        && (in == null || in == System.in)
                        && (out == null || out == System.out));
    }

    /**
     * Builds a system terminal using the SPI provider discovery.
     * Discovers all {@link TerminalProvider} implementations via ServiceLoader,
     * filters by {@link TerminalProvider#isSupported()}, sorts by priority
     * (highest first), and tries each until one succeeds.
     */
    private Terminal buildSystemTerminal(String name) throws IOException {
        // Discover and sort providers
        List<TerminalProvider> providers = new ArrayList<>();
        for (TerminalProvider provider : ServiceLoader.load(TerminalProvider.class)) {
            if (provider.isSupported()) {
                providers.add(provider);
                LOGGER.log(Level.FINE, "Found supported terminal provider: {0} (priority={1})",
                        new Object[] { provider.name(), provider.priority() });
            } else {
                LOGGER.log(Level.FINE, "Terminal provider not supported: {0}", provider.name());
            }
        }
        providers.sort(Comparator.comparingInt(TerminalProvider::priority).reversed());

        // Try each provider in priority order
        IOException lastException = null;
        for (TerminalProvider provider : providers) {
            try {
                Terminal terminal = provider.createTerminal(name, type, nativeSignals);
                LOGGER.log(Level.FINE, "Created terminal using provider: {0}", provider.name());
                return terminal;
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Provider {0} failed: {1}",
                        new Object[] { provider.name(), e.getMessage() });
                lastException = e;
            }
        }

        // No SPI provider succeeded — fall back to external terminal
        LOGGER.log(Level.FINE, "No terminal provider succeeded, falling back to external terminal");
        return buildExternalTerminal(name);
    }

    /**
     * Builds an external terminal with explicit input/output streams.
     * Used when stdin/stdout are redirected or no system terminal is available.
     */
    private Terminal buildExternalTerminal(String name) throws IOException {
        InputStream inputStream = (in == null) ? System.in : in;
        OutputStream outputStream = (out == null) ? System.out : out;
        if (OSUtils.IS_WINDOWS) {
            return new WinExternalTerminal(name, type, inputStream, outputStream);
        }
        return new ExternalTerminal(name, type, inputStream, outputStream);
    }
}
