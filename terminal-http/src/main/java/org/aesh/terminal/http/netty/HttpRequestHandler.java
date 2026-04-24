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
package org.aesh.terminal.http.netty;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.http.HttpTtyConnection;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Netty channel handler for processing HTTP requests and serving static resources.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String wsUri;
    private final String resourcePath;

    private static final Logger LOGGER = Logger.getLogger(HttpRequestHandler.class.getName());
    private static final Map<String, String> CONTENT_TYPES;

    static {
        CONTENT_TYPES = new HashMap<>();
        CONTENT_TYPES.put("html", "text/html");
        CONTENT_TYPES.put("js", "application/javascript");
        CONTENT_TYPES.put("css", "text/css");
    }

    /**
     * Creates a new HTTP request handler with the specified WebSocket URI.
     * Uses the default resource path for static files.
     *
     * @param wsUri the URI path for WebSocket upgrade requests
     */
    public HttpRequestHandler(String wsUri) {
        this(wsUri, "/org/aesh/terminal/http");
    }

    /**
     * Creates a new HTTP request handler with the specified WebSocket URI and resource path.
     *
     * @param wsUri the URI path for WebSocket upgrade requests
     * @param resourcePath the classpath resource path for static files, or null to disable static file serving
     */
    public HttpRequestHandler(String wsUri, String resourcePath) {
        this.wsUri = wsUri;
        this.resourcePath = resourcePath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (wsUri.equalsIgnoreCase(request.uri())) {
            ctx.fireChannelRead(request.retain());
        } else {
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            HttpResponse response = new DefaultHttpResponse(request.protocolVersion(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);

            // If static file serving is disabled, return 404
            if (resourcePath == null) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                ctx.write(response);
                ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                future.addListener(ChannelFutureListener.CLOSE);
                return;
            }

            String path = request.uri();
            if ("/".equals(path)) {
                path = "/index.html";
            }
            URL res = HttpTtyConnection.class.getResource(resourcePath + path);
            try {
                if (res != null) {
                    DefaultFullHttpResponse fullResp = new DefaultFullHttpResponse(request.protocolVersion(),
                            HttpResponseStatus.OK);
                    InputStream in = res.openStream();
                    byte[] tmp = new byte[256];
                    for (int l = 0; l != -1; l = in.read(tmp)) {
                        fullResp.content().writeBytes(tmp, 0, l);
                    }
                    int li = path.lastIndexOf('.');
                    if (li != -1 && li != path.length() - 1) {
                        String ext = path.substring(li + 1);
                        String contentType = CONTENT_TYPES.get(ext);
                        if (contentType != null) {
                            fullResp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
                        }
                    }
                    response = fullResp;
                } else {
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to serve static resource", e);
            } finally {
                ctx.write(response);
                ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        ctx.writeAndFlush(response);
    }

    /**
     * Handles exceptions caught during channel processing by logging and closing the context.
     *
     * @param ctx the channel handler context
     * @param cause the exception that was caught
     */
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.log(Level.WARNING, "Exception caught during io, closing.", cause);
        ctx.close();
    }
}
