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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.aesh.terminal.TestBase;
import org.aesh.terminal.telnet.netty.TelnetChannelHandler;
import org.junit.rules.ExternalResource;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TelnetServerRule extends ExternalResource {

    protected Closeable server;
    private int port;

    /**
     * Creates a TelnetServerRule that allocates a dynamic port.
     */
    public TelnetServerRule() {
        try {
            this.port = TestBase.findAvailablePort();
        } catch (IOException e) {
            throw new RuntimeException("Cannot find available port", e);
        }
    }

    /**
     * Returns the port the server binds to.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    @Override
    protected void after() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Starts a Netty-based telnet server with the given handler factory.
     *
     * @param telnetFactory supplier for creating TelnetHandler instances
     */
    public final void start(Supplier<TelnetHandler> telnetFactory) {
        if (server != null) {
            throw TestBase.failure("Already a server");
        }
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        TelnetChannelHandler handler = new TelnetChannelHandler(telnetFactory);
                        p.addLast(handler);
                    }
                });
        try {
            b.bind("localhost", port).sync();
        } catch (InterruptedException e) {
            bossGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            throw TestBase.failure(e);
        }
        server = () -> {
            try {
                bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }
}
