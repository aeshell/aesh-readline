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
package org.aesh.terminal.telnet.netty;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.aesh.terminal.Connection;
import org.aesh.terminal.telnet.TelnetTtyConnection;
import org.aesh.terminal.utils.Helper;

/**
 * Bootstrap class for starting a Telnet TTY server using Netty.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyTelnetTtyBootstrap {

    private final NettyTelnetBootstrap telnet;
    private boolean outBinary;
    private boolean inBinary;
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * Creates a new NettyTelnetTtyBootstrap with default settings.
     */
    public NettyTelnetTtyBootstrap() {
        this.telnet = new NettyTelnetBootstrap();
    }

    /**
     * Returns the host address the server will bind to.
     *
     * @return the host address
     */
    public String getHost() {
        return telnet.getHost();
    }

    /**
     * Sets the host address the server will bind to.
     *
     * @param host the host address
     * @return this bootstrap instance for method chaining
     */
    public NettyTelnetTtyBootstrap setHost(String host) {
        telnet.setHost(host);
        return this;
    }

    /**
     * Returns the port number the server will listen on.
     *
     * @return the port number
     */
    public int getPort() {
        return telnet.getPort();
    }

    /**
     * Sets the port number the server will listen on.
     *
     * @param port the port number
     * @return this bootstrap instance for method chaining
     */
    public NettyTelnetTtyBootstrap setPort(int port) {
        telnet.setPort(port);
        return this;
    }

    /**
     * Returns whether binary mode is enabled for output.
     *
     * @return true if binary mode is enabled for output
     */
    public boolean isOutBinary() {
        return outBinary;
    }

    /**
     * Enable or disable the TELNET BINARY option on output.
     *
     * @param outBinary true to require the client to receive binary
     * @return this object
     */
    public NettyTelnetTtyBootstrap setOutBinary(boolean outBinary) {
        this.outBinary = outBinary;
        return this;
    }

    /**
     * Returns whether binary mode is enabled for input.
     *
     * @return true if binary mode is enabled for input
     */
    public boolean isInBinary() {
        return inBinary;
    }

    /**
     * Enable or disable the TELNET BINARY option on input.
     *
     * @param inBinary true to require the client to emit binary
     * @return this object
     */
    public NettyTelnetTtyBootstrap setInBinary(boolean inBinary) {
        this.inBinary = inBinary;
        return this;
    }

    /**
     * Returns the charset used for encoding/decoding.
     *
     * @return the charset
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Sets the charset to use for encoding/decoding.
     *
     * @param charset the charset
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Starts the telnet TTY server asynchronously.
     *
     * @param factory the connection handler factory
     * @return a future that completes when the server has started
     */
    public CompletableFuture<?> start(Consumer<Connection> factory) {
        CompletableFuture<?> fut = new CompletableFuture<>();
        start(factory, Helper.startedHandler(fut));
        return fut;
    }

    /**
     * Stops the telnet TTY server asynchronously.
     *
     * @return a future that completes when the server has stopped
     */
    public CompletableFuture<?> stop() {
        CompletableFuture<?> fut = new CompletableFuture<>();
        stop(Helper.stoppedHandler(fut));
        return fut;
    }

    /**
     * Starts the telnet TTY server with a callback handler.
     *
     * @param factory the connection handler factory
     * @param doneHandler the handler called when start completes (with null on success, or an exception on failure)
     */
    public void start(Consumer<Connection> factory, Consumer<Throwable> doneHandler) {
        telnet.start(() -> new TelnetTtyConnection(inBinary, outBinary, charset, factory), doneHandler);
    }

    /**
     * Stops the telnet TTY server with a callback handler.
     *
     * @param doneHandler the handler called when stop completes (with null on success, or an exception on failure)
     */
    public void stop(Consumer<Throwable> doneHandler) {
        telnet.stop(doneHandler);
    }
}
