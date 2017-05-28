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
package org.aesh.terminal.ssh;

import com.jcraft.jsch.JSchException;
import org.aesh.terminal.TestBase;
import org.aesh.terminal.ssh.netty.AsyncAuth;
import org.aesh.terminal.ssh.netty.AsyncUserAuthServiceFactory;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerConnectionServiceFactory;
import org.apache.sshd.util.EchoShellFactory;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class AsyncAuthTestBase extends TestBase {

  SshServer server;

  private PasswordAuthenticator authenticator;

  public void startServer() throws Exception {
    startServer(null);
  }

  public void startServer(Integer timeout) throws Exception {
    if (server != null) {
      throw failure("Server already started");
    }
    server = SshServer.setUpDefaultServer();
    if (timeout != null) {
      server.setProperties(Collections.singletonMap(FactoryManager.AUTH_TIMEOUT, timeout.toString()));
    }
    server.setPort(5000);
    server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));
    server.setPasswordAuthenticator((username, password, sess) -> authenticator.authenticate(username, password, sess));
    server.setShellFactory(new EchoShellFactory());
    server.setServiceFactories(Arrays.asList(ServerConnectionServiceFactory.INSTANCE, AsyncUserAuthServiceFactory.INSTANCE));
    server.start();
  }

  @After
  public void stopServer() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testSyncAuthFailed() throws Exception {
    startServer();
    authenticator = (username, password, sess) -> false;
    assertFalse(authenticate());
  }

  @Test
  public void testSyncAuthSucceeded() throws Exception {
    startServer();
    authenticator = (username, password, sess) -> true;
    assertTrue(authenticate());
  }

  @Test
  public void testAsyncAuthFailed() throws Exception {
    startServer();
    authenticator = (username, password, sess) -> {
      AsyncAuth auth = new AsyncAuth();
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignore) {
          } finally {
            auth.setAuthed(false);
          }
        }
      }.start();
      throw auth;
    };
    assertFalse(authenticate());
  }

  @Test
  public void testAsyncAuthSucceeded() throws Exception {
    startServer();
    authenticator = (username, password, sess) -> {
      AsyncAuth auth = new AsyncAuth();
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignore) {
          } finally {
            auth.setAuthed(true);
          }
        }
      }.start();
      throw auth;
    };
    assertTrue(authenticate());
  }

  @Test
  public void testAsyncAuthTimeout() throws Exception {
    startServer(500);
    authenticator = (username, password, sess) -> {
      throw new AsyncAuth();
    };
    try {
      authenticate();
    } catch (JSchException e) {
      assertTrue("Unexpected failure " + e.getMessage(), e.getMessage().startsWith("SSH_MSG_DISCONNECT"));
    }
  }

  @Test
  public void testAsyncAuthSucceededAfterTimeout() throws Exception {
    startServer(500);
    authenticator = (username, password, sess) -> {
      AsyncAuth auth = new AsyncAuth();
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ignore) {
          } finally {
            auth.setAuthed(true);
          }
        }
      }.start();
      throw auth;
    };
    try {
      authenticate();
    } catch (JSchException e) {
      assertTrue("Unexpected failure " + e.getMessage(), e.getMessage().startsWith("SSH_MSG_DISCONNECT"));
    }
  }

  protected abstract boolean authenticate() throws Exception;

}
