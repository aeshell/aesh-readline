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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.aesh.terminal.Connection;
import org.aesh.terminal.ssh.TtyCommand;
import org.aesh.terminal.utils.Helper;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.netty.NettyIoServiceFactoryFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Bootstrap class for starting an SSH TTY server using Netty.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettySshTtyBootstrap {

    private String host;
    private int port;
    private Charset charset;
    private EventLoopGroup parentGroup;
    private EventLoopGroup childGroup;
    private SshServer server;
    private KeyPairProvider keyPairProvider;
    private PasswordAuthenticator passwordAuthenticator;
    private PublickeyAuthenticator publicKeyAuthenticator;

    /**
     * Creates a new NettySshTtyBootstrap with default settings.
     * Defaults to localhost:5000 with UTF-8 charset.
     */
    public NettySshTtyBootstrap() {
        this.host = "localhost";
        this.port = 5000;
        this.charset = StandardCharsets.UTF_8;
        this.parentGroup = new NioEventLoopGroup(1);
        this.childGroup = new NioEventLoopGroup();
        this.keyPairProvider = new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath());
        this.passwordAuthenticator = (username, password, session) -> true;
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
     * Sets the host address for the SSH server to bind to.
     *
     * @param host the host address
     * @return this bootstrap for method chaining
     */
    public NettySshTtyBootstrap setHost(String host) {
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
     * Sets the port for the SSH server to listen on.
     *
     * @param port the port number
     * @return this bootstrap for method chaining
     */
    public NettySshTtyBootstrap setPort(int port) {
        this.port = port;
        return this;
    }

    /**
     * Sets the password authenticator for SSH connections.
     *
     * @param passwordAuthenticator the authenticator to validate username/password
     * @return this bootstrap for method chaining
     */
    public NettySshTtyBootstrap setPasswordAuthenticator(PasswordAuthenticator passwordAuthenticator) {
        this.passwordAuthenticator = passwordAuthenticator;
        return this;
    }

    /**
     * Sets the public key authenticator for SSH connections.
     *
     * @param publicKeyAuthenticator the authenticator to validate public keys
     * @return this bootstrap for method chaining
     */
    public NettySshTtyBootstrap setPublicKeyAuthenticator(PublickeyAuthenticator publicKeyAuthenticator) {
        this.publicKeyAuthenticator = publicKeyAuthenticator;
        return this;
    }

    /**
     * Starts the SSH server asynchronously.
     *
     * @param handler the connection handler for new SSH sessions
     * @return a CompletableFuture that completes when the server has started
     * @throws Exception if the server fails to start
     */
    public CompletableFuture<Void> start(Consumer<Connection> handler) throws Exception {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        start(handler, Helper.startedHandler(fut));
        return fut;
    }

    /**
     * Returns the key pair provider used for SSH host keys.
     *
     * @return the key pair provider
     */
    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    /**
     * Sets the key pair provider for SSH host keys.
     *
     * @param keyPairProvider the key pair provider
     * @return this bootstrap for method chaining
     */
    public NettySshTtyBootstrap setKeyPairProvider(KeyPairProvider keyPairProvider) {
        this.keyPairProvider = keyPairProvider;
        return this;
    }

    /**
     * Returns the character set used for encoding/decoding.
     *
     * @return the charset
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * Sets the character set for encoding/decoding.
     *
     * @param charset the charset to use
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Starts the SSH server with a callback for completion notification.
     *
     * @param factory the connection handler factory for new SSH sessions
     * @param doneHandler callback invoked with null on success, or the exception on failure
     */
    public void start(Consumer<Connection> factory, Consumer<Throwable> doneHandler) {
        server = SshServer.setUpDefaultServer();
        server.setIoServiceFactoryFactory(new NettyIoServiceFactoryFactory(childGroup));
        server.setPort(port);
        server.setHost(host);
        server.setKeyPairProvider(keyPairProvider);
        server.setPasswordAuthenticator(passwordAuthenticator);
        server.setPublickeyAuthenticator(publicKeyAuthenticator);
        server.setShellFactory(channelSession -> new TtyCommand(charset, factory));
        try {
            server.start();
        } catch (Exception e) {
            doneHandler.accept(e);
            return;
        }
        doneHandler.accept(null);
    }

    /**
     * Stops the SSH server asynchronously.
     *
     * @return a CompletableFuture that completes when the server has stopped
     * @throws InterruptedException if interrupted while stopping
     */
    public CompletableFuture<Void> stop() throws InterruptedException {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        stop(Helper.stoppedHandler(fut));
        return fut;
    }

    /**
     * Stops the SSH server with a callback for completion notification.
     *
     * @param doneHandler callback invoked with null on success, or the exception on failure
     */
    public void stop(Consumer<Throwable> doneHandler) {
        if (server != null) {
            try {
                server.stop();
            } catch (IOException e) {
                doneHandler.accept(e);
                return;
            }
            doneHandler.accept(null);
        } else {
            doneHandler.accept(new IllegalStateException("Server not started"));
        }
    }
}
