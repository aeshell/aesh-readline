/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal.telnet;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.aesh.terminal.utils.Helper;

/**
 * Abstract base class for bootstrapping a Telnet server.
 * Provides configuration for host and port binding, as well as start/stop lifecycle methods.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TelnetBootstrap {

    private String host = "localhost";
    private int port = 4000;

    /**
     * Returns the host address the server will bind to.
     *
     * @return the host address
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host address the server will bind to.
     *
     * @param host the host address
     * @return this bootstrap instance for method chaining
     */
    public TelnetBootstrap setHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Returns the port number the server will listen on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port number the server will listen on.
     *
     * @param port the port number
     * @return this bootstrap instance for method chaining
     */
    public TelnetBootstrap setPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Starts the telnet server asynchronously.
     *
     * @param factory the factory to create telnet handlers for each connection
     * @return a future that completes when the server has started
     */
    public CompletableFuture<?> start(Supplier<TelnetHandler> factory) {
        CompletableFuture<?> fut = new CompletableFuture<>();
        start(factory, Helper.startedHandler(fut));
        return fut;
    }

    /**
     * Stops the telnet server asynchronously.
     *
     * @return a future that completes when the server has stopped
     */
    public CompletableFuture<?> stop() {
        CompletableFuture<?> fut = new CompletableFuture<>();
        stop(Helper.stoppedHandler(fut));
        return fut;
    }

    /**
     * Start the telnet server
     *
     * @param factory the telnet handler factory
     * @param doneHandler the done handler
     */
    public abstract void start(Supplier<TelnetHandler> factory, Consumer<Throwable> doneHandler);

    /**
     * Stops the telnet server with a callback handler.
     *
     * @param doneHandler the handler called when stop completes (with null on success, or an exception on failure)
     */
    public abstract void stop(Consumer<Throwable> doneHandler);

}
