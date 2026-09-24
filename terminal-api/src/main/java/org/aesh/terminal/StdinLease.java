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

/**
 * Scoped lease on a connection's stdin handler, obtained via
 * {@link Connection#captureStdin(java.util.function.Consumer)}.
 * <p>
 * Closing the lease restores the handler that was current when the lease
 * was acquired. Use try-with-resources so temporary handler swaps
 * (terminal queries, process I/O) are exception-safe by construction
 * instead of hand-rolled save/set/restore.
 *
 * @since 3.18.2
 */
public interface StdinLease extends AutoCloseable {

    /**
     * Restore the stdin handler that was current when the lease was
     * acquired. Idempotent: closing twice restores once.
     */
    @Override
    void close();
}
