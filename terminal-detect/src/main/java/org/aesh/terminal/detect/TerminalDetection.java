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

import java.util.Collections;
import java.util.Map;

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
 * This does not grant terminal I/O ownership, establish live TTY/locality facts,
 * or change existing static factories and connection behavior. Bounded
 * enrichment and connection integration are separate contracts.
 *
 * @since 3.18.8
 */
public final class TerminalDetection {

    private final TerminalCapabilities capabilities;

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
}
