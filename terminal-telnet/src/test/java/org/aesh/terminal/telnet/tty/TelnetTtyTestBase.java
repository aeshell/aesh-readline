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
package org.aesh.terminal.telnet.tty;

import org.aesh.terminal.Connection;
import org.aesh.terminal.telnet.TelnetClientRule;
import org.aesh.terminal.telnet.TelnetHandler;
import org.aesh.terminal.telnet.TelnetServerRule;
import org.aesh.terminal.telnet.TelnetTtyConnection;
import org.aesh.terminal.tty.TtyTestBase;
import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.SimpleOptionHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;
import org.junit.Rule;

import java.io.Closeable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TelnetTtyTestBase extends TtyTestBase {

  protected boolean binary;
  private WindowSizeOptionHandler wsHandler;

  @Rule
  public TelnetServerRule server = new TelnetServerRule(serverFactory());

  @Rule
  public TelnetClientRule client = new TelnetClientRule();

  protected abstract Function<Supplier<TelnetHandler>, Closeable> serverFactory();

  @Override
  public boolean checkDisconnected() {
    return client.checkDisconnected();
  }

  protected void server(Consumer<Connection> onConnect) {
    server.start(() -> new TelnetTtyConnection(binary, binary, charset, onConnect));
  }

  @Override
  protected void resize(int width, int height) {
  }

  @Override
  protected void assertConnect(String term) throws Exception {
    client.setOptionHandler(new EchoOptionHandler(false, false, true, true));
    if (binary) {
      client.setOptionHandler(new SimpleOptionHandler(0, false, false, true, true));
    }
    if (term != null) {
      client.setOptionHandler(new TerminalTypeOptionHandler(term, false, false, true, false));
    }
    client.connect("localhost", 4000);
  }

  @Override
  protected void assertWrite(String s) throws Exception {
    client.write(s.getBytes(charset));
    client.flush();
  }

  protected final void assertWriteln(String s) throws Exception {
    assertWrite(s + (binary ? "\r" : "\r\n"));
  }

  @Override
  protected String assertReadString(int len) throws Exception {
    return client.assertReadString(len);
  }

  @Override
  protected void assertDisconnect(boolean clean) throws Exception {
    client.disconnect(clean);
  }

  @Override
  public void testSize() throws Exception {
    wsHandler = new WindowSizeOptionHandler(80, 24, false, false, true, true);
    client.setOptionHandler(wsHandler);
    super.testSize();
  }

  @Override
  public void testResize() throws Exception {
    // Cannot be tested with this client that does not support resize
  }
}
