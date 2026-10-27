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
package org.aesh.terminal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.StatusLine;

/**
 * Abstract base class for {@link Connection} implementations that use an
 * {@link EventDecoder} for signal and input handling.
 * <p>
 * Provides the common handler plumbing (signal, stdin, stdout, size, close,
 * attributes, reading state) so that subclasses only need to implement
 * device/transport-specific methods.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public abstract class AbstractConnection implements Connection {

    /** Field. */
    protected EventDecoder eventDecoder;
    /** Field. */
    protected Consumer<int[]> stdout;
    /** Field. */
    protected Consumer<Size> sizeHandler;
    /** Field. */
    protected Consumer<Void> closeHandler;
    /** Field. */
    protected Attributes attributes;
    /** Field. */
    protected volatile boolean reading;

    private volatile EndpointContext endpointContext;
    /** Guarded by this monitor; context reads use only the volatile snapshot. */
    private boolean endpointContextClosed;

    @Override
    public EndpointContext endpointContext() {
        EndpointContext current = endpointContext;
        return current == null ? EndpointContext.unknown() : current;
    }

    /**
     * Replace this endpoint's creator-supplied facts without discovery.
     * This does not attach capabilities or grant query permissions.
     *
     * Updates after endpoint invalidation are ignored.
     *
     * @param context the immutable facts, or null to restore unknown facts
     * @since 3.18.8
     */
    public synchronized void setEndpointContext(EndpointContext context) {
        if (!endpointContextClosed) {
            endpointContext = context;
        }
    }

    /**
     * Capture a size already reported by this connection's transport.
     * This performs no size query and calls no application handler.
     * Updates after endpoint invalidation are ignored.
     *
     * @param dimensions the transport's current size
     * @param source OBSERVED for local resize, NEGOTIATED for remote resize
     */
    protected final synchronized void updateEndpointDimensions(Size dimensions, EndpointContext.Source source) {
        if (!endpointContextClosed) {
            endpointContext = endpointContext().withDimensions(dimensions, source);
        }
    }

    /**
     * Declare a locally opened endpoint with already-observed stream facts.
     * Only the local system-terminal setup calls this: arbitrary supplied
     * streams and externally provided terminals stay unknown unless their
     * creator declares them. Performs no probing; the fd facts must come
     * from observations already available during setup. Dimensions are left
     * absent: setup performs no size query, the first resize report fills
     * them. Updates after endpoint invalidation are ignored.
     *
     * @param stdinTerminal observed stdin terminal status
     * @param stdoutTerminal observed stdout terminal status
     * @param stderrTerminal observed stderr terminal status
     * @param consoleObserved true only when the opened native-console
     *        transport demonstrably owns this endpoint
     */
    public final void declareLocalEndpoint(boolean stdinTerminal, boolean stdoutTerminal,
            boolean stderrTerminal, boolean consoleObserved) {
        setEndpointContext(EndpointContext.builder()
                .locality(EndpointContext.Locality.LOCAL)
                .input(observedTerminal(stdinTerminal, consoleObserved))
                .output(observedTerminal(stdoutTerminal, consoleObserved))
                .error(observedTerminal(stderrTerminal, consoleObserved))
                .build());
    }

    /**
     * Declare an accepted remote endpoint. The negotiated channel is a
     * terminal for input and output; error-stream and native-console facts
     * stay unknown. Performs no probing. Updates after endpoint
     * invalidation are ignored.
     */
    public final void declareRemoteEndpoint() {
        EndpointContext.StreamFacts negotiated = EndpointContext.StreamFacts.of(
                EndpointContext.Truth.YES, EndpointContext.Source.NEGOTIATED,
                EndpointContext.Truth.UNKNOWN, EndpointContext.Source.UNKNOWN);
        setEndpointContext(EndpointContext.builder()
                .locality(EndpointContext.Locality.REMOTE)
                .input(negotiated)
                .output(negotiated)
                .build());
    }

    private static EndpointContext.StreamFacts observedTerminal(boolean terminal,
            boolean consoleObserved) {
        return EndpointContext.StreamFacts.of(
                terminal ? EndpointContext.Truth.YES : EndpointContext.Truth.NO,
                EndpointContext.Source.OBSERVED,
                consoleObserved ? EndpointContext.Truth.YES : EndpointContext.Truth.UNKNOWN,
                consoleObserved ? EndpointContext.Source.OBSERVED : EndpointContext.Source.UNKNOWN);
    }

    /**
     * Invalidate live endpoint facts and dispose attached subscriptions during
     * transport close. This does not signal that input ownership or native
     * cleanup has been released. Previously captured endpoint and capability
     * snapshots remain historical values. Listener close runs outside the
     * connection monitor.
     */
    protected final void closeEndpointContext() {
        TerminalDetection attached;
        synchronized (this) {
            endpointContextClosed = true;
            endpointContext = null;
            attached = detection;
            detection = null;
        }
        if (attached != null) {
            attached.close();
        }
    }

    /** Constructor. */
    protected AbstractConnection() {
    }

    private TerminalFeatures terminalFeatures;

    /**
     * Cached OSC 7501 support for this connection. TerminalFeatures reads
     * and writes it so detection results survive repeated terminal() calls;
     * the interface default builds a fresh TerminalFeatures per call, which
     * keeps only instance state.
     */
    private volatile Boolean programStatusSupport;

    @Override
    public TerminalFeatures terminal() {
        if (terminalFeatures == null) {
            terminalFeatures = new TerminalFeatures(this);
        }
        return terminalFeatures;
    }

    /**
     * Cached OSC 7501 support, or null while unqueried or last inconclusive.
     *
     * @return the cached support
     */
    Boolean programStatusSupport() {
        return programStatusSupport;
    }

    /**
     * Store a conclusive OSC 7501 support answer.
     *
     * @param support the support answer, never null here
     */
    void setProgramStatusSupport(Boolean support) {
        programStatusSupport = support;
    }

    /**
     * Endpoint-scoped detection owner attached to this connection, or null
     * when unattached. {@link TerminalFeatures} prefers attached facts over
     * process-global defaults and declines live queries the attached source
     * leaves unknown. Null preserves the legacy answers bit for bit.
     */
    private volatile TerminalDetection detection;

    /**
     * Attach an endpoint-scoped detection owner to this connection.
     * The owner is retained by reference; a pending producer keeps
     * filling it. Passing null clears the attachment and restores
     * the legacy process-global answers. Replacing or closing the
     * attachment closes subscriptions made through the previous owner,
     * outside the connection monitor. Use a distinct owner for each
     * independently closed connection.
     *
     * @param detection the owner to attach, or null to clear
     */
    public void attachDetection(TerminalDetection detection) {
        TerminalDetection previous;
        boolean closeNew = false;
        synchronized (this) {
            previous = this.detection;
            if (endpointContextClosed) {
                this.detection = null;
                closeNew = detection != null;
            } else {
                this.detection = detection;
            }
        }
        if (previous != null && previous != detection) {
            previous.close();
        }
        if (closeNew) {
            detection.close();
        }
    }

    /**
     * The attached detection owner, or null when unattached.
     *
     * @return the attached owner, or null
     */
    public TerminalDetection attachedDetection() {
        return detection;
    }

    /**
     * Return leftover query bytes to the decoder queue when no input
     * handler is installed to receive them.
     *
     * @param leftovers the bytes to keep for a later handler
     */
    void requeueInput(int[] leftovers) {
        if (eventDecoder != null) {
            eventDecoder.requeueInput(leftovers);
        }
    }

    @Override
    public Consumer<Signal> signalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> stdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    @Override
    public Consumer<Size> sizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> closeHandler() {
        return closeHandler;
    }

    @Override
    public void setThemeChangeHandler(Consumer<TerminalTheme> handler) {
        eventDecoder.setThemeChangeHandler(handler);
    }

    @Override
    public Consumer<TerminalTheme> themeChangeHandler() {
        return eventDecoder.getThemeChangeHandler();
    }

    @Override
    public void setMouseHandler(java.util.function.Consumer<org.aesh.terminal.tty.MouseEvent> handler) {
        eventDecoder.setMouseHandler(handler);
    }

    @Override
    public java.util.function.Consumer<org.aesh.terminal.tty.MouseEvent> mouseHandler() {
        return eventDecoder.getMouseHandler();
    }

    @Override
    public void setFocusHandler(java.util.function.Consumer<Boolean> handler) {
        eventDecoder.setFocusHandler(handler);
    }

    @Override
    public java.util.function.Consumer<Boolean> focusHandler() {
        return eventDecoder.getFocusHandler();
    }

    /** Handler for printAbove requests. */
    private volatile Consumer<String> printAboveHandler;

    /** Registered status lines, sorted by priority. */
    private final CopyOnWriteArrayList<StatusLineImpl> statusLines = new CopyOnWriteArrayList<>();

    @Override
    public StatusLine registerStatusLine(int priority) {
        StatusLineImpl sl = new StatusLineImpl(priority, this);
        statusLines.add(sl);
        // Sort by priority (lowest first = top, highest last = bottom near prompt)
        statusLines.sort((a, b) -> Integer.compare(a.priority, b.priority));
        // Trigger a redraw if a printAbove handler is active
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
        return sl;
    }

    /**
     * Returns the current status line messages in display order
     * (lowest priority first).
     *
     * @return unmodifiable list of active status line messages
     */
    public List<String> getStatusMessages() {
        if (statusLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new java.util.ArrayList<>();
        for (StatusLineImpl sl : statusLines) {
            String msg = sl.message;
            if (msg != null && !msg.isEmpty()) {
                messages.add(msg);
            }
        }
        return Collections.unmodifiableList(messages);
    }

    void removeStatusLine(StatusLineImpl sl) {
        statusLines.remove(sl);
        // Trigger a redraw
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
    }

    void statusLineUpdated() {
        // Trigger a redraw when a status line message changes
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
    }

    private static class StatusLineImpl implements StatusLine {
        final int priority;
        volatile String message;
        volatile boolean closed;
        private final AbstractConnection connection;

        StatusLineImpl(int priority, AbstractConnection connection) {
            this.priority = priority;
            this.connection = connection;
        }

        @Override
        public void setMessage(String message) {
            if (!closed) {
                this.message = message;
                connection.statusLineUpdated();
            }
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                connection.removeStatusLine(this);
            }
        }
    }

    @Override
    public void setPrintAboveHandler(Consumer<String> handler) {
        this.printAboveHandler = handler;
    }

    @Override
    public Consumer<String> printAboveHandler() {
        return printAboveHandler;
    }

    @Override
    public boolean reading() {
        return reading;
    }

    @Override
    public Attributes attributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attr) {
        this.attributes = attr;
    }
}
