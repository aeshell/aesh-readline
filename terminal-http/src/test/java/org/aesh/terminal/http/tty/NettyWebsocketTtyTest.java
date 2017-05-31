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
package org.aesh.terminal.http.tty;

import org.aesh.terminal.Connection;
import org.aesh.terminal.http.netty.NettyWebsocketTtyBootstrap;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyWebsocketTtyTest extends WebsocketTtyTestBase {

  private NettyWebsocketTtyBootstrap bootstrap;

  @Override
  protected void server(Consumer<Connection> onConnect) {
    if (bootstrap != null) {
      throw failure("Server already started");
    }
    bootstrap = new NettyWebsocketTtyBootstrap().setHost("localhost").setPort(8080);
    try {
      bootstrap.start(onConnect).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw failure(e);
    }
  }

  public void after() throws Exception {
    if (bootstrap != null) {
      bootstrap.stop().get(10, TimeUnit.SECONDS);
      bootstrap = null;
    }
  }
}
