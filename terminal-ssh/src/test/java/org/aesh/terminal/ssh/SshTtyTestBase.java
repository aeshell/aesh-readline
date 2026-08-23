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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.terminal.Connection;
import org.aesh.terminal.ssh.netty.NettySshTtyBootstrap;
import org.aesh.terminal.tty.TtyTestBase;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.After;

/**
 * Base class for running the standard TtyTestBase suite over SSH transport.
 */
public class SshTtyTestBase extends TtyTestBase {

    private NettySshTtyBootstrap bootstrap;
    private SshClient client;
    private ClientSession session;
    private ChannelShell channel;
    private OutputStream pipedIn;
    private ByteArrayOutputStream pipedOut;
    private int port;

    @Override
    protected void server(Consumer<Connection> onConnect) {
        try {
            port = findAvailablePort();
        } catch (IOException e) {
            throw failure(e);
        }
        bootstrap = new NettySshTtyBootstrap()
                .host("localhost")
                .port(port);
        try {
            bootstrap.start(onConnect).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw failure(e);
        }
    }

    @Override
    protected void assertConnect(String term) throws Exception {
        client = SshClient.setUpDefaultClient();
        client.start();
        session = client.connect("user", "localhost", port)
                .verify(10, TimeUnit.SECONDS)
                .getClientSession();
        session.addPasswordIdentity("password");
        CoreModuleProperties.PREFERRED_AUTHS.set(session, "password");
        session.auth().verify(10, TimeUnit.SECONDS);

        channel = session.createShellChannel();
        if (term != null) {
            channel.setPtyType(term);
        }
        pipedOut = new ByteArrayOutputStream();
        channel.setOut(pipedOut);
        channel.setErr(new NoCloseOutputStream(System.err));
        channel.open().verify(10, TimeUnit.SECONDS);
        pipedIn = channel.getInvertedIn();
    }

    @Override
    protected void assertWrite(String s) throws Exception {
        pipedIn.write(s.getBytes(charset));
        pipedIn.flush();
    }

    @Override
    protected void assertWriteln(String s) throws Exception {
        assertWrite(s + "\r");
    }

    @Override
    protected String assertReadString(int len) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            synchronized (pipedOut) {
                byte[] data = pipedOut.toByteArray();
                if (data.length >= len) {
                    String result = new String(data, 0, len, StandardCharsets.UTF_8);
                    // Remove consumed bytes
                    byte[] remaining = new byte[data.length - len];
                    System.arraycopy(data, len, remaining, 0, remaining.length);
                    pipedOut.reset();
                    pipedOut.write(remaining);
                    return result;
                }
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw failure("Timed out reading " + len + " chars, got " + pipedOut.size());
            }
            Thread.sleep(Math.min(10, remaining));
        }
    }

    @Override
    protected void assertDisconnect(boolean clean) throws Exception {
        if (clean) {
            channel.close(false).await(5, TimeUnit.SECONDS);
        } else {
            session.disconnect(0, "test disconnect");
        }
    }

    @Override
    public boolean checkDisconnected() {
        return channel != null && channel.isClosed();
    }

    @Override
    protected void resize(int width, int height) throws Exception {
        // SSH channel supports resize via window-change request
        channel.sendWindowChange(width, height);
    }

    @Override
    public void testDifferentCharset() {
        // SSH always uses UTF-8 encoding, charset cannot be changed per-connection
    }

    @After
    public void afterSsh() {
        if (channel != null) {
            try {
                channel.close(true).await(5, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignore) {
            }
        }
        if (client != null) {
            try {
                client.stop();
            } catch (Exception ignore) {
            }
        }
        if (bootstrap != null) {
            try {
                bootstrap.stop().get(10, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        }
    }
}
