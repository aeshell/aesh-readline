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
package org.aesh.terminal.detect;

import java.io.IOException;

/**
 * A probe transport that can open a session against a deadline.
 * <p>
 * The legacy {@link TerminalProbeTransport} contract cannot express a
 * bound: {@code isAvailable()}, {@code open()}, reads, and {@code close()}
 * may each block without limit. Bounded enrichment only uses transports
 * implementing this interface; a legacy transport is declined, never
 * wrapped in a racing future that would leave work alive past the bound.
 * <p>
 * An expired deadline must decline before touching the terminal: no raw
 * mode entry, no session, no writes. Once opened, the session bounds its
 * waits to the remaining budget; each underlying inter-byte timeout caps
 * the overshoot at one transport quantum (500 ms on the built-ins).
 * Closing still restores terminal state even when the budget expired;
 * restoration is best-effort and may exceed the deadline.
 *
 * @since 3.18.8
 */
public interface BoundedProbeTransport extends TerminalProbeTransport {

    /**
     * Enter raw mode and open a probe session while budget remains.
     * An expired deadline declines with {@link IOException} before any
     * terminal state changes.
     *
     * @param deadline the round deadline; never null
     * @return an open probe session
     * @throws IOException if raw mode cannot be entered, the terminal
     *         cannot be opened, or the deadline already expired
     */
    TerminalProbeSession open(ProbeDeadline deadline) throws IOException;
}
