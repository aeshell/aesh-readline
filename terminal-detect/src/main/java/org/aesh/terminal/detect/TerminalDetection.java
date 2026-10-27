/*
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
package org.aesh.terminal.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Passive capability consumption scoped to one terminal/session.
 * <p>
 * An owner captures environment hints or an embedder's existing result once.
 * Construction and reads never start discovery, load IDE settings or native
 * transports, launch subprocesses/workers, or await an in-flight probe.
 * Missing facts remain unknown. There is no fallback to the process default.
 * <p>
 * A supplied result is retained by reference, not copied or frozen. Its producer
 * may still fill pending values; this owner only reads them. Several owners can
 * deliberately share that result. Replacing or invalidating the process default
 * does not replace a captured reference, and this owner does not cancel work
 * started by another owner. Array/map getters retain their defensive-copy rules.
 * <p>
 * Subscriptions made through this owner share its lifetime. Closing the owner
 * closes only those subscriptions, never subscriptions made through another
 * owner or directly on {@link TerminalCapabilities}. Use a distinct owner for
 * each independently closed connection or application. Closing does not
 * invalidate capability reads or cancel discovery.
 * <p>
 * This does not grant terminal I/O ownership or establish live TTY/locality
 * facts. Bounded enrichment remains a separate contract.
 *
 * @since 3.18.8
 */
public final class TerminalDetection implements AutoCloseable {

    private final TerminalCapabilities capabilities;
    /** Guarded by this monitor; allocated on the first subscription. */
    private List<OwnedSubscription> subscriptions;
    /** Guarded by this monitor. */
    private boolean closed;

    private TerminalDetection(TerminalCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Capture process environment hints without reading or publishing shared
     * detection. Useful when the caller already knows probing is inappropriate.
     * Environment hints themselves are not proof of a usable terminal.
     *
     * @return the scoped detection owner
     */
    public static TerminalDetection environmentOnly() {
        return new TerminalDetection(TerminalCapabilities.detect());
    }

    /**
     * Capture hints from a supplied environment without discovery or reading
     * process environment/properties. Values are captured now; later changes
     * to the map do not change this result.
     *
     * @param environment the environment hints, never null
     * @return the scoped detection owner
     * @throws NullPointerException if environment is null
     */
    public static TerminalDetection environmentOnly(Map<String, String> environment) {
        return new TerminalDetection(TerminalCapabilities.fromEnvironment(environment));
    }

    /**
     * Consume supplied capabilities without starting or joining discovery.
     * A null source creates conservative unknown facts without scanning the
     * environment. Missing RGB, palette or mode answers never cause a retry.
     *
     * @param capabilities the supplied result, or null for unknown facts
     * @return the scoped detection owner
     */
    public static TerminalDetection managed(TerminalCapabilities capabilities) {
        return new TerminalDetection(capabilities == null
                ? TerminalCapabilities.fromEnvironment(Collections.<String, String> emptyMap())
                : capabilities);
    }

    /**
     * Read the captured result without awaiting or upgrading it.
     *
     * @return the captured capabilities, never null; the reference is stable
     */
    public TerminalCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Subscribe through this owner with synchronous later delivery.
     * Registration replays the current state synchronously. No protocol
     * notification, reader, raw mode, or discovery is enabled by this call.
     *
     * @param listener receives immutable snapshots
     * @return a handle owned by this detection scope
     * @since 3.18.8
     */
    public TerminalSubscription subscribe(Consumer<TerminalSnapshot> listener) {
        return subscribe(listener, null);
    }

    /**
     * Subscribe through this owner with caller-supplied later delivery.
     * Registration still replays synchronously on the calling thread.
     *
     * @param listener receives immutable snapshots
     * @param executor runs later events, or null for synchronous delivery
     * @return a handle owned by this detection scope; if this owner is already
     *         closed, the returned handle is closed and receives no replay
     * @since 3.18.8
     */
    public TerminalSubscription subscribe(Consumer<TerminalSnapshot> listener, Executor executor) {
        return subscribeWithReplayBarrierForTests(listener, executor, null);
    }

    /** Package-visible registration barrier for deterministic lifecycle tests. */
    TerminalSubscription subscribeWithReplayBarrierForTests(Consumer<TerminalSnapshot> listener,
            Executor executor, Runnable beforeReplay) {
        Objects.requireNonNull(listener, "listener");
        OwnedSubscription owned = new OwnedSubscription(this, listener);
        synchronized (this) {
            if (closed) {
                owned.rejectRegistration();
                return owned;
            }
            if (subscriptions == null) {
                subscriptions = new ArrayList<OwnedSubscription>();
            }
            subscriptions.add(owned);
        }
        try {
            TerminalSubscription delegate = capabilities.subscribeWithReplayBarrierForTests(
                    owned, executor, beforeReplay);
            owned.finishRegistration(delegate);
            return owned;
        } catch (RuntimeException | Error e) {
            owned.close();
            throw e;
        }
    }

    private synchronized void remove(OwnedSubscription subscription) {
        if (subscriptions != null) {
            subscriptions.remove(subscription);
        }
    }

    /**
     * Close every subscription made through this owner. Capability reads stay
     * valid and shared discovery is not cancelled. Calling more than once has
     * no effect.
     */
    @Override
    public void close() {
        List<OwnedSubscription> close;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            if (subscriptions == null) {
                return;
            }
            close = new ArrayList<OwnedSubscription>(subscriptions);
            subscriptions = null;
        }
        for (OwnedSubscription subscription : close) {
            subscription.close();
        }
    }

    /** Connects one owner registration to its underlying result subscription. */
    private static final class OwnedSubscription implements TerminalSubscription, Consumer<TerminalSnapshot> {
        private final TerminalDetection owner;
        private final Consumer<TerminalSnapshot> listener;
        private final Object monitor = new Object();
        private final Thread registeringThread;
        private TerminalSubscription delegate;
        private boolean registering = true;
        private volatile boolean closed;

        OwnedSubscription(TerminalDetection owner, Consumer<TerminalSnapshot> listener) {
            this.owner = owner;
            this.listener = listener;
            this.registeringThread = Thread.currentThread();
        }

        @Override
        public void accept(TerminalSnapshot snapshot) {
            if (!closed) {
                listener.accept(snapshot);
            }
        }

        private void rejectRegistration() {
            synchronized (monitor) {
                closed = true;
                registering = false;
                monitor.notifyAll();
            }
        }

        private void finishRegistration(TerminalSubscription subscription) {
            boolean close;
            synchronized (monitor) {
                delegate = subscription;
                registering = false;
                close = closed;
                monitor.notifyAll();
            }
            if (close) {
                subscription.close();
            }
        }

        @Override
        public void close() {
            TerminalSubscription current;
            boolean interrupted = false;
            synchronized (monitor) {
                closed = true;
                while (registering && registeringThread != Thread.currentThread()) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                current = delegate;
            }
            owner.remove(this);
            if (current != null) {
                current.close();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
