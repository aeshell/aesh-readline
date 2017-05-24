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
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.io.IoServiceFactoryFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyIoServiceFactoryFactory implements IoServiceFactoryFactory {

  final EventLoopGroup eventLoopGroup;
  final NettyIoHandlerBridge handlerBridge;

  public NettyIoServiceFactoryFactory() {
    this.eventLoopGroup = null;
    this.handlerBridge = new NettyIoHandlerBridge();
  }

  public NettyIoServiceFactoryFactory(EventLoopGroup eventLoopGroup) {
    this.eventLoopGroup = eventLoopGroup;
    this.handlerBridge = new NettyIoHandlerBridge();
  }

  public NettyIoServiceFactoryFactory(EventLoopGroup eventLoopGroup, NettyIoHandlerBridge handlerBridge) {
    this.eventLoopGroup = eventLoopGroup;
    this.handlerBridge = handlerBridge;
  }

  @Override
  public IoServiceFactory create(FactoryManager manager) {
    return new NettyIoServiceFactory(eventLoopGroup, handlerBridge);
  }

}
