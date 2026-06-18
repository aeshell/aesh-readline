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

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.TestBase;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerConnectionServiceFactory;
import org.apache.sshd.server.session.ServerUserAuthServiceFactory;
import org.apache.sshd.util.test.EchoShellFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for synchronous and asynchronous SSH password authentication.
 * <p>
 * Each test takes ~2s due to SSHD's built-in auth failure delay — this is
 * intentional SSH protocol behavior to prevent brute-force attacks and cannot
 * be reduced without patching the SSHD server internals.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AsyncAuthTest extends TestBase {

    SshServer server;
    int port;

    private PasswordAuthenticator authenticator;

    public void startServer() throws Exception {
        startServer(null);
    }

    public void startServer(Integer timeout) throws Exception {
        if (server != null) {
            throw failure("Server already started");
        }
        port = findAvailablePort();
        server = SshServer.setUpDefaultServer();
        if (timeout != null) {
            server.getProperties().put(CoreModuleProperties.AUTH_TIMEOUT.getName(), timeout.toString());
        }
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));
        server.setPasswordAuthenticator((username, password, sess) -> authenticator.authenticate(username, password, sess));
        server.setShellFactory(new EchoShellFactory());
        server.setServiceFactories(
                Arrays.asList(ServerConnectionServiceFactory.INSTANCE, ServerUserAuthServiceFactory.INSTANCE));
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
        Assert.assertFalse(authenticate());
    }

    @Test
    public void testSyncAuthSucceeded() throws Exception {
        startServer();
        authenticator = (username, password, sess) -> true;
        Assert.assertTrue(authenticate());
    }

    @Test
    public void testAsyncAuthFailed() throws Exception {
        startServer();
        authenticator = (username, password, sess) -> {
            AsyncAuthException auth = new AsyncAuthException();
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {
                } finally {
                    auth.setAuthed(false);
                }
            }).start();
            throw auth;
        };
        Assert.assertFalse(authenticate());
    }

    @Test
    public void testAsyncAuthSucceeded() throws Exception {
        startServer();
        authenticator = (username, password, sess) -> {
            AsyncAuthException auth = new AsyncAuthException();
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignore) {
                } finally {
                    auth.setAuthed(true);
                }
            }).start();
            throw auth;
        };
        Assert.assertTrue(authenticate());
    }

    @Test
    public void testAsyncAuthTimeout() throws Exception {
        startServer(500);
        authenticator = (username, password, sess) -> {
            throw new AsyncAuthException();
        };
        Assert.assertFalse(authenticate());
    }

    @Test
    public void testAsyncAuthSucceededAfterTimeout() throws Exception {
        startServer(500);
        authenticator = (username, password, sess) -> {
            AsyncAuthException auth = new AsyncAuthException();
            new Thread(() -> {
                try {
                    // Sleep past the 500ms server auth timeout
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                } finally {
                    auth.setAuthed(true);
                }
            }).start();
            throw auth;
        };
        Assert.assertFalse(authenticate());
    }

    @Test
    public void testHostKeyPersistence() throws Exception {
        File keyFile = new File("hostkey-test.ser");
        keyFile.deleteOnExit();
        try {
            // First server start generates the host key
            Assert.assertFalse("Key file should not exist yet", keyFile.exists());
            port = findAvailablePort();
            server = SshServer.setUpDefaultServer();
            server.setPort(port);
            server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(keyFile.toPath()));
            authenticator = (username, password, sess) -> true;
            server.setPasswordAuthenticator((username, password, sess) -> authenticator.authenticate(username, password, sess));
            server.setShellFactory(new EchoShellFactory());
            server.setServiceFactories(
                    Arrays.asList(ServerConnectionServiceFactory.INSTANCE, ServerUserAuthServiceFactory.INSTANCE));
            server.start();

            // Connect and verify the key file was created
            Assert.assertTrue("Should authenticate successfully", authenticate());
            server.stop();
            server = null;

            Assert.assertTrue("Host key file should exist after first server start", keyFile.exists());
            Assert.assertTrue("Host key file should not be empty", keyFile.length() > 0);

            // Second server start should reuse the existing key
            long keySize = keyFile.length();
            port = findAvailablePort();
            server = SshServer.setUpDefaultServer();
            server.setPort(port);
            server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(keyFile.toPath()));
            server.setPasswordAuthenticator((username, password, sess) -> true);
            server.setShellFactory(new EchoShellFactory());
            server.setServiceFactories(
                    Arrays.asList(ServerConnectionServiceFactory.INSTANCE, ServerUserAuthServiceFactory.INSTANCE));
            server.start();

            Assert.assertTrue("Should authenticate with reused key", authenticate());
            Assert.assertEquals("Host key file size should be unchanged (reused, not regenerated)",
                    keySize, keyFile.length());
        } finally {
            keyFile.delete();
        }
    }

    protected boolean authenticate() {
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();
            ClientSession sess = client
                    .connect("whatever", "localhost", port)
                    .verify(TimeUnit.SECONDS.toMillis(5))
                    .getSession();
            // Disable key identity so the client uses password auth,
            // but still negotiates all default auth methods (publickey,
            // keyboard-interactive, password) to exercise the full path.
            sess.setKeyIdentityProvider(null);
            sess.addPasswordIdentity("whocares");
            sess.auth().verify(TimeUnit.SECONDS.toMillis(5));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
