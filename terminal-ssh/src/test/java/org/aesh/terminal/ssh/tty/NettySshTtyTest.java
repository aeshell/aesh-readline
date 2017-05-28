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
package org.aesh.terminal.ssh.tty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.aesh.terminal.Connection;
import org.aesh.terminal.ssh.TtyCommand;
import org.aesh.terminal.ssh.netty.NettyIoServiceFactoryFactory;
import org.aesh.terminal.ssh.netty.NettyIoSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettySshTtyTest extends SshTtyTestBase {

  private EventLoopGroup eventLoopGroup;

  @Before
  public void before() {
    eventLoopGroup = new NioEventLoopGroup();
  }

  @After
  public void after() throws Exception {
    eventLoopGroup.shutdownGracefully();
  }

  @Override
  protected SshServer createServer() {
    SshServer sshd = SshServer.setUpDefaultServer();
    sshd.setIoServiceFactoryFactory(new NettyIoServiceFactoryFactory(eventLoopGroup));
    return sshd;
  }

  @Override
  protected TtyCommand createConnection(Consumer<Connection> onConnect) {
    return new TtyCommand(charset, onConnect) {
      @Override
      public void execute(Runnable task) {
        Session session = this.session.getSession();
        NettyIoSession ioSession = (NettyIoSession) session.getIoSession();
        ioSession.execute(task);
      }
    };
  }

  @Override
  protected void assertThreading(Thread connThread, Thread schedulerThread) throws Exception {
    assertTrue(connThread.getName().startsWith("nioEventLoopGroup"));
    assertEquals(connThread, schedulerThread);
  }
}
