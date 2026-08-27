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
package org.aesh.terminal;

/**
 * Three-state result of a DEC private mode probe via DECRQM.
 * <p>
 * The three states enable smart fallbacks:
 * <ul>
 * <li>{@link #SUPPORTED} — terminal responded positively to DECRQM</li>
 * <li>{@link #NOT_SUPPORTED} — terminal responded to DA1 (speaks escapes)
 * but did not respond to DECRQM for this mode</li>
 * <li>{@link #NO_RESPONSE} — terminal did not respond to DA1 at all
 * (dumb terminal, no probes safe)</li>
 * </ul>
 */
public enum ModeSupport {

    /**
     * Terminal responded to DECRQM with Ps=1 (set), Ps=2 (reset but recognized),
     * or Ps=3 (permanently set). The mode is known to be supported.
     */
    SUPPORTED,

    /**
     * Terminal responded to DA1 (it speaks escape sequences) but did not
     * respond to DECRQM for this mode. The mode is not supported, but
     * cursor-position fallbacks are safe to use.
     */
    NOT_SUPPORTED,

    /**
     * Terminal did not respond to DA1 at all. This is a dumb terminal —
     * no further probes should be attempted (they would all time out).
     */
    NO_RESPONSE
}
