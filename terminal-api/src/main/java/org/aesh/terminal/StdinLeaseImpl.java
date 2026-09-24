/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Package-private {@link StdinLease} implementation.
 * <p>
 * Restore is unconditional (finally-semantics): whatever is current at
 * close time is replaced by the handler saved at capture time. Leases are
 * therefore last-in-first-out; closing overlapping leases out of order
 * restores a stale handler. That misuse is logged best-effort — the
 * volatile read/compare/restore sequence is detection, not prevention.
 */
final class StdinLeaseImpl implements StdinLease {

    private static final Logger LOGGER = Logger.getLogger(StdinLeaseImpl.class.getName());

    private final Connection connection;
    private final Consumer<int[]> saved;
    private final Consumer<int[]> installed;
    private final AtomicBoolean closed = new AtomicBoolean();

    StdinLeaseImpl(Connection connection, Consumer<int[]> saved, Consumer<int[]> installed) {
        this.connection = connection;
        this.saved = saved;
        this.installed = installed;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (connection.stdinHandler() != installed) {
                LOGGER.log(Level.WARNING,
                        "StdinLease closed out of order: current handler is not the leased one, "
                                + "restoring the saved handler anyway");
            }
            connection.setStdinHandler(saved);
        }
    }
}
