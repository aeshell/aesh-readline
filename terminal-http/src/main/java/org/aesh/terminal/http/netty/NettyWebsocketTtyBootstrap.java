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
package org.aesh.terminal.http.netty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.aesh.terminal.Connection;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;

/**
 * Convenience class for quickly starting a Netty Tty server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyWebsocketTtyBootstrap {

    private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private String host;
    private int port;
    private EventLoopGroup group;
    private Channel channel;

    /**
     * Creates a new bootstrap with default host (localhost) and port (8080).
     */
    public NettyWebsocketTtyBootstrap() {
        this.host = "localhost";
        this.port = 8080;
    }

    /**
     * Returns the host address the server will bind to.
     *
     * @return the host address
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host address for the server to bind to.
     *
     * @param host the host address
     * @return this bootstrap instance for method chaining
     */
    public NettyWebsocketTtyBootstrap setHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Returns the port the server will listen on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port for the server to listen on.
     *
     * @param port the port number
     * @return this bootstrap instance for method chaining
     */
    public NettyWebsocketTtyBootstrap setPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Starts the server asynchronously with callback-based completion notification.
     *
     * @param handler the handler to invoke for each new connection
     * @param doneHandler the callback invoked when startup completes (with null on success, or the error)
     */
    public void start(Consumer<Connection> handler, Consumer<Throwable> doneHandler) {
        group = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new TtyServerInitializer(channelGroup, handler));

        ChannelFuture f = b.bind(host, port);
        f.addListener(abc -> {
            if (abc.isSuccess()) {
                channel = f.channel();
                doneHandler.accept(null);
            } else {
                doneHandler.accept(abc.cause());
            }
        });
    }

    /**
     * Starts the server asynchronously and returns a CompletableFuture.
     *
     * @param handler the handler to invoke for each new connection
     * @return a CompletableFuture that completes when the server has started
     * @throws Exception if an error occurs during startup
     */
    public CompletableFuture<Void> start(Consumer<Connection> handler) throws Exception {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        start(handler, startedHandler(fut));
        return fut;
    }

    /**
     * Stops the server asynchronously with callback-based completion notification.
     *
     * @param doneHandler the callback invoked when shutdown completes
     */
    public void stop(Consumer<Throwable> doneHandler) {
        CountDownLatch latch = new CountDownLatch(1);
        if (channel != null) {
            channel.close().addListener((Future<Void> f) -> latch.countDown());
        }
        channelGroup.close().addListener((Future<Void> f) -> {
            latch.await();
            doneHandler.accept(f.cause());
        });
    }

    /**
     * Stops the server asynchronously and returns a CompletableFuture.
     *
     * @return a CompletableFuture that completes when the server has stopped
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public CompletableFuture<Void> stop() throws InterruptedException {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        stop(stoppedHandler(fut));
        return fut;
    }

    private Consumer<Throwable> startedHandler(CompletableFuture<?> fut) {
        return err -> {
            if (err == null) {
                fut.complete(null);
            } else {
                fut.completeExceptionally(err);
            }
        };
    }

    private Consumer<Throwable> stoppedHandler(CompletableFuture<?> fut) {
        return err -> {
            fut.complete(null);
        };
    }
}
