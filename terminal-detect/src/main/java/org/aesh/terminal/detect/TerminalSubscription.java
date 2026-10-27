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

/**
 * A scoped capability subscription on one {@link TerminalCapabilities}
 * instance.
 * <p>
 * Closing is idempotent: after close no further events are delivered, even
 * ones already queued. Closing one subscription never affects other
 * subscribers, the owning result, or shared discovery.
 *
 * @since 3.18.8
 */
public interface TerminalSubscription {

    /**
     * Stop delivery to this subscriber and release its listener and queued
     * snapshot. After this method returns, no new callback begins. A callback
     * already selected for delivery finishes first, so closing from another
     * thread waits for it. Reentrant close from inside that callback does not
     * wait and suppresses subsequent events. Calling more than once has no
     * effect.
     */
    void close();
}
