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
package org.aesh.terminal.http.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aesh.terminal.http.server.TaskStatusUpdateEvent;
import org.aesh.terminal.http.utils.ObjectWrapper;
import org.aesh.terminal.http.utils.Wait;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 * @see "https://github.com/undertow-io/undertow/blob/5bdddf327209a4abf18792e78148863686c26e9b/websockets-jsr/src/test/java/io/undertow/websockets/jsr/test/BinaryEndpointTest.java"
 */
public class Client {

    public static final String WEB_SOCKET_TERMINAL_PATH = "/socket/term";
    public static final String WEB_SOCKET_LISTENER_PATH = "/socket/process-status-updates";

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
    private Consumer<Session> onOpenConsumer;
    private Consumer<String> onStringMessageConsumer;
    private Consumer<byte[]> onBinaryMessageConsumer;
    private Consumer<CloseReason> onCloseConsumer;
    private Consumer<Throwable> onErrorConsumer;

    public Endpoint connect(String websocketUrl) throws Exception {
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI(websocketUrl));
        return endpoint;
    }

    public void close() throws Exception {
        LOGGER.log(Level.INFO, "Client is closing connection.");
        endpoint.session.close();
//        endpoint.closeLatch.await(10, TimeUnit.SECONDS);
    }

    public void onOpen(Consumer<Session> onOpen) {
        onOpenConsumer = onOpen;
    }

    public void onStringMessage(Consumer<String> onStringMessage) {
        onStringMessageConsumer = onStringMessage;
    }

    public void onBinaryMessage(Consumer<byte[]> onBinaryMessage) {
        onBinaryMessageConsumer = onBinaryMessage;
    }

    public void onClose(Consumer<CloseReason> onClose) {
        onCloseConsumer = onClose;
    }

    public void onError(Consumer<Throwable> onError) {
        onErrorConsumer = onError;
    }

    public RemoteEndpoint.Basic getRemoteEndpoint() {
        return endpoint.session.getBasicRemote();
    }

    public class ProgramaticClientEndpoint extends Endpoint {
        volatile Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            LOGGER.log(Level.FINE, "Client received open.");
            this.session = session;

            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    LOGGER.log(Level.FINEST, "Client received text MESSAGE: {}", message);
                    if (onStringMessageConsumer != null) {
                        onStringMessageConsumer.accept(message);
                    }
                }
            });
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] bytes) {
                    LOGGER.log(Level.FINEST, "Client received binary MESSAGE: {}", new String(bytes));
                    if (onBinaryMessageConsumer != null) {
                        onBinaryMessageConsumer.accept(bytes);
                    }
                }
            });
            if (onOpenConsumer != null) {
                onOpenConsumer.accept(session);
            }
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOGGER.log(Level.SEVERE, "Client received close.");
            onCloseConsumer.accept(closeReason);
        }

        @Override
        public void onError(Session session, Throwable thr) {
            if (onErrorConsumer != null) {
                onErrorConsumer.accept(thr);
            } else {
                LOGGER.log(Level.SEVERE, "No error handler defined. Received error was: ", thr);
            }
        }
    }

    public static Client initializeDefault() {
        Client client = new Client();

        Consumer<Session> onOpen = (session) -> {
            LOGGER.info("Client connection opened.");
        };

        Consumer<CloseReason> onClose = (closeReason) -> {
            LOGGER.info("Client connection closed. " + closeReason);
        };

        client.onOpen(onOpen);
        client.onClose(onClose);

        return client;
    }


    public static Client connectStatusListenerClient(String webSocketUrl, Consumer<TaskStatusUpdateEvent> onStatusUpdate) {
        Client client = Client.initializeDefault();
        Consumer<String> responseConsumer = (text) -> {
            LOGGER.log(Level.FINE, "Decoding response: {}", text);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonObject = null;
            try {
                jsonObject = mapper.readTree(text);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Cannot read JSON string: " + text, e);
            }
            try {
                TaskStatusUpdateEvent taskStatusUpdateEvent = TaskStatusUpdateEvent.fromJson(jsonObject.get("event").toString());
                onStatusUpdate.accept(taskStatusUpdateEvent);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Cannot deserialize TaskStatusUpdateEvent.", e);
            }
        };
        client.onStringMessage(responseConsumer);

        client.onClose(closeReason -> {
        });

        try {
            client.connect(webSocketUrl + "/");
        } catch (Exception e) {
            throw new AssertionError("Failed to connect to remote client.", e);
        }
        return client;
    }

    public static Client connectCommandExecutingClient(String webSocketUrl, Optional<Consumer<String>> responseDataConsumer) throws InterruptedException, TimeoutException {
        ObjectWrapper<Boolean> connected = new ObjectWrapper<>(false);

        Client client = Client.initializeDefault();
        Consumer<byte[]> responseConsumer = (bytes) -> {
            String responseData = new String(bytes);
            if ("% ".equals(responseData)) { //TODO use events
                connected.set(true);
            } else {
                responseDataConsumer.ifPresent((rdc) -> rdc.accept(responseData));
                ;
            }
        };
        client.onBinaryMessage(responseConsumer);

        client.onClose(closeReason -> {
        });

        try {
            client.connect(webSocketUrl + "/");
        } catch (Exception e) {
            throw new AssertionError("Failed to connect to remote client.", e);
        }
        Wait.forCondition(() -> connected.get(), 5, ChronoUnit.SECONDS, "Client was not connected within given timeout.");
        return client;
    }

    public static void executeRemoteCommand(Client client, String command) {
        LOGGER.info("Executing remote command ...");
        RemoteEndpoint.Basic remoteEndpoint = client.getRemoteEndpoint();
        String data = "{\"action\":\"read\",\"data\":\"" + command + "\\r\\n\"}";
        try {
            remoteEndpoint.sendBinary(ByteBuffer.wrap(data.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
