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

import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.FactoryManagerUtils;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.CloseableUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.session.ServerSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AsyncUserAuthService extends CloseableUtils.AbstractCloseable implements Service  {

    public static final int DEFAULT_MAX_AUTH_REQUESTS = 20;
    private final ServerSession session;
    private List<NamedFactory<UserAuth>> userAuthFactories;
    private List<List<String>> authMethods;
    private String authUserName;
    private String authMethod;
    private String authService;
    private UserAuth currentAuth;
    private AsyncAuth async;

    private int maxAuthRequests;
    private int nbAuthRequests;

    private static final Logger LOGGER = Logger.getLogger(AsyncUserAuthService.class.getName());


    public AsyncUserAuthService(Session s) throws SshException {
        ValidateUtils.checkTrue(s instanceof ServerSession, "Server side service used on client side");
        if (s.isAuthenticated()) {
            throw new SshException("Session already authenticated");
        }

        this.session = (ServerSession) s;
        maxAuthRequests = session.getIntProperty(ServerFactoryManager.MAX_AUTH_REQUESTS, DEFAULT_MAX_AUTH_REQUESTS);

        ServerFactoryManager manager = getFactoryManager();
        userAuthFactories = new ArrayList<>(manager.getUserAuthFactories());
        // Get authentication methods
        authMethods = new ArrayList<>();

        String mths = FactoryManagerUtils.getString(manager, ServerFactoryManager.AUTH_METHODS);
        if (GenericUtils.isEmpty(mths)) {
            for (NamedFactory<UserAuth> uaf : manager.getUserAuthFactories()) {
                authMethods.add(new ArrayList<>(Collections.singletonList(uaf.getName())));
            }
        }
        else {
            for (String mthl : mths.split("\\s")) {
                authMethods.add(new ArrayList<>(Arrays.asList(mthl.split(","))));
            }
        }
        // Verify all required methods are supported
        for (List<String> l : authMethods) {
            for (String m : l) {
                NamedFactory<UserAuth> factory = NamedResource.Utils.findByName(m, String.CASE_INSENSITIVE_ORDER, userAuthFactories);
                if (factory == null) {
                    throw new SshException("Configured method is not supported: " + m);
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Authorized authentication methods: "+ NamedResource.Utils.getNames(userAuthFactories));
        }
    }

    @Override
    public void start() {
        // do nothing
    }

    @Override
    public ServerSession getSession() {
        return session;
    }

    @Override
    public void process(int cmd, Buffer buffer) throws Exception {
        Boolean authed = Boolean.FALSE;

        if (cmd == SshConstants.SSH_MSG_USERAUTH_REQUEST) {
            LOGGER.fine("Received SSH_MSG_USERAUTH_REQUEST");
            if (this.currentAuth != null) {
                this.currentAuth.destroy();
                this.currentAuth = null;
            }

            String username = buffer.getString();
            String service = buffer.getString();
            String method = buffer.getString();
            if (this.authUserName == null || this.authService == null) {
                this.authUserName = username;
                this.authService = service;
            }
            else if (!this.authUserName.equals(username) || !this.authService.equals(service)) {
                session.disconnect(SshConstants.SSH2_DISCONNECT_PROTOCOL_ERROR,
                        "Change of username or service is not allowed (" + this.authUserName + ", " + this.authService + ") -> ("
                                + username + ", " + service + ")");
                return;
            }
            // TODO: verify that the service is supported
            this.authMethod = method;
            if (nbAuthRequests++ > maxAuthRequests) {
                session.disconnect(SshConstants.SSH2_DISCONNECT_PROTOCOL_ERROR, "Too many authentication failures");
                return;
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Authenticating user '{"+username+"}' with service '{"+service+"}' and method '{"+method+"}'");
            }

            NamedFactory<UserAuth> factory = NamedResource.Utils.findByName(method, String.CASE_INSENSITIVE_ORDER, userAuthFactories);
            if (factory != null) {
                currentAuth = factory.create();
                try {
                    authed = currentAuth.auth(session, username, service, buffer);
                } catch (Exception e) {
                    if (asyncAuth(buffer, e)) {
                        return;
                    }

                    // Continue
                    LOGGER.fine("Authentication failed: "+ e.getMessage());
                }
            }
        }
        else {
            assert async == null;
            if (this.currentAuth == null) {
                // This should not happen
                throw new IllegalStateException("No current authentication mechanism for cmd=" + (cmd & 0xFF));
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Received authentication message: "+ Integer.valueOf(cmd & 0xFF));
            }
            buffer.rpos(buffer.rpos() - 1);
            try {
                authed = currentAuth.next(buffer);
            } catch (Exception e) {
                if (asyncAuth(buffer, e)) {
                    return;
                }

                // Continue
                LOGGER.log(Level.FINE, "Failed ({}) to authenticate: "+ e.getClass().getSimpleName(), e.getMessage());
            }
        }

        if (authed == null) {
            // authentication is still ongoing
            LOGGER.fine("Authentication not finished");
        }
        else {
            sendAuthenticationResult(buffer, authed);
        }
    }

    private boolean asyncAuth(Buffer buffer, Exception e) {
        if (e instanceof AsyncAuth) {
            async = (AsyncAuth) e;
            async.setListener(authenticated -> {
                async = null;
                try {
                    sendAuthenticationResult(buffer, authenticated);
                } catch (Exception e1) {
                    // HANDLE THIS BETTER
                    e1.printStackTrace();
                }
            });
            return true;
        }
        else {
            return false;
        }
    }

    private void sendAuthenticationResult(Buffer buffer, boolean authed) throws Exception {
        if (authed) {
            LOGGER.fine("Authentication succeeded");
            String username = currentAuth.getUserName();

            boolean success = false;
            for (List<String> l : authMethods) {
                if ((GenericUtils.size(l) > 0) && l.get(0).equals(authMethod)) {
                    l.remove(0);
                    success |= l.isEmpty();
                }
            }

            if (success) {
                FactoryManager manager = getFactoryManager();
                Integer maxSessionCount = FactoryManagerUtils.getInteger(manager, ServerFactoryManager.MAX_CONCURRENT_SESSIONS);
                if (maxSessionCount != null) {
                    int currentSessionCount = session.getActiveSessionCountForUser(username);
                    if (currentSessionCount >= maxSessionCount) {
                        session.disconnect(SshConstants.SSH2_DISCONNECT_SERVICE_NOT_AVAILABLE,
                                "Too many concurrent connections (" + currentSessionCount + ") - max. allowed: " + maxSessionCount);
                        return;
                    }
                }

                String welcomeBanner = FactoryManagerUtils.getString(manager, ServerFactoryManager.WELCOME_BANNER);
                if (welcomeBanner != null) {
                    buffer = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_BANNER);
                    buffer.putString(welcomeBanner);
                    buffer.putString("en");
                    session.writePacket(buffer);
                }

                buffer = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_SUCCESS);
                session.setUsername(username);
                session.setAuthenticated();
                session.startService(authService);

                // Important: write packet after setting service, otherwise this service may process wrong messages
                session.writePacket(buffer);
                session.resetIdleTimeout();
                LOGGER.fine("Session {"+username+"}@{+"+session.getIoSession().getRemoteAddress()+"} authenticated");

            }
            else {
                buffer = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_FAILURE);
                StringBuilder sb = new StringBuilder();
                for (List<String> l : authMethods) {
                    if (GenericUtils.size(l) > 0) {
                        if (sb.length() > 0) {
                            sb.append(",");
                        }
                        sb.append(l.get(0));
                    }
                }
                buffer.putString(sb.toString());
                buffer.putBoolean(true);
                session.writePacket(buffer);
            }

            currentAuth.destroy();
            currentAuth = null;
        }
        else {
            LOGGER.fine("Authentication failed");

            buffer = session.createBuffer(SshConstants.SSH_MSG_USERAUTH_FAILURE);
            StringBuilder sb = new StringBuilder();
            for (List<String> l : authMethods) {
                if (GenericUtils.size(l) > 0) {
                    String m = l.get(0);
                    if (!"none".equals(m)) {
                        if (sb.length() > 0) {
                            sb.append(",");
                        }
                        sb.append(l.get(0));
                    }
                }
            }
            buffer.putString(sb.toString());
            buffer.putByte((byte) 0);
            session.writePacket(buffer);

            if (currentAuth != null) {
                currentAuth.destroy();
                currentAuth = null;
            }
        }
    }

    private ServerFactoryManager getFactoryManager() {
        return session.getFactoryManager();
    }
}
