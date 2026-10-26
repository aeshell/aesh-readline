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

import java.util.concurrent.TimeUnit;

/**
 * A monotonic deadline for one bounded enrichment round.
 * <p>
 * Created before transport selection and passed through lock acquisition,
 * session setup, reads, platform fallback, restoration, and cleanup. Every
 * phase reads the same remaining budget; no phase restarts a timeout.
 * An expired budget declines new work instead of starting another attempt.
 * Cleanup still runs when the budget expires, and may exceed it; that
 * overrun is reported, never hidden as success.
 * <p>
 * Java 8, zero dependencies. The clock is {@link System#nanoTime}, so wall
 * clock changes cannot extend or shorten a round.
 *
 * @since 3.18.8
 */
public final class ProbeDeadline {

    private final long deadlineNanos;

    private ProbeDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    /**
     * Create a deadline the given duration from now.
     *
     * @param timeout the budget from now, in the given unit
     * @param unit the budget unit
     * @return a deadline {@code timeout} in the future
     */
    public static ProbeDeadline after(long timeout, TimeUnit unit) {
        return new ProbeDeadline(System.nanoTime() + unit.toNanos(timeout));
    }

    /**
     * Create an already-expired deadline for tests.
     *
     * @return a deadline that is expired on creation
     */
    public static ProbeDeadline expired() {
        return new ProbeDeadline(System.nanoTime() - 1);
    }

    /**
     * Remaining budget, or zero when expired. Never negative.
     *
     * @return remaining nanoseconds, saturating at zero
     */
    public long remainingNanos() {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0 ? 0 : remaining;
    }

    /**
     * Remaining budget in milliseconds for subprocess waits.
     * Saturates at {@link Long#MAX_VALUE}; expired reads as zero.
     *
     * @return remaining whole milliseconds, zero when expired
     */
    public long remainingMillis() {
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos());
    }

    /**
     * Check whether any budget remains.
     *
     * @return true when the deadline has passed
     */
    public boolean isExpired() {
        return remainingNanos() == 0;
    }
}
