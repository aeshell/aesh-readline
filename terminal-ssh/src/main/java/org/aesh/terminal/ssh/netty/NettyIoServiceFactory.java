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
package org.aesh.terminal.ssh.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoConnector;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.util.CloseableUtils;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyIoServiceFactory extends CloseableUtils.AbstractCloseable implements IoServiceFactory {

  final NettyIoHandlerBridge handlerBridge;
  final EventLoopGroup eventLoopGroup;
  final boolean closeEventLoopGroup;

  public NettyIoServiceFactory() {
    this(null);
  }

  public NettyIoServiceFactory(EventLoopGroup group) {
    this(group, new NettyIoHandlerBridge());
  }

  public NettyIoServiceFactory(EventLoopGroup group, NettyIoHandlerBridge handlerBridge) {
    this.handlerBridge = handlerBridge;
    this.closeEventLoopGroup = group == null;
    this.eventLoopGroup = group == null ? new NioEventLoopGroup() : group;
  }

  @Override
  public IoConnector createConnector(IoHandler handler) {
    throw new UnsupportedOperationException("Only implement server for now");
  }

  @Override
  public IoAcceptor createAcceptor(IoHandler handler) {
    return new NettyIoAcceptor(this, handler);
  }

  @Override
  protected CloseFuture doCloseGracefully() {
    if (closeEventLoopGroup) {
      eventLoopGroup.shutdownGracefully().addListener((Future<Object> fut) -> {
        closeFuture.setClosed();
      });
    } else {
      closeFuture.setClosed();
    }
    return closeFuture;
  }

  @Override
  protected void doCloseImmediately() {
    doCloseGracefully();
    super.doCloseImmediately();
  }
}
