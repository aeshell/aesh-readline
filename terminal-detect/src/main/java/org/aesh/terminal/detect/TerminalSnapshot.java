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
import java.util.HashMap;
import java.util.Map;

/**
 * One immutable, coherent view of a terminal's appearance and mode facts.
 * <p>
 * Every snapshot carries the {@link #revision()} that produced it, so a
 * subscriber can order a replayed state against later updates and reject
 * stale deliveries. Values are captured once per mutation and shared by all
 * subscribers to that revision; array and map getters still return
 * defensive copies, mirroring {@link TerminalCapabilities}.
 * <p>
 * A theme change arrives with the new theme and null RGB values in a single
 * revision. Consumers must not start a probe to fill the gaps; missing
 * values mean unknown, not a request for discovery.
 *
 * @since 3.18.8
 */
public final class TerminalSnapshot {

    private final long revision;
    private final TerminalTheme theme;
    private final int[] foregroundRGB;
    private final int[] backgroundRGB;
    private final Map<Integer, int[]> paletteColors;
    private final ModeSupport mode2026Support;
    private final ModeSupport mode2027Support;
    private final Boolean nativeGraphemeClustering;

    TerminalSnapshot(long revision, TerminalTheme theme,
            int[] foregroundRGB, int[] backgroundRGB,
            Map<Integer, int[]> paletteColors,
            ModeSupport mode2026Support, ModeSupport mode2027Support,
            Boolean nativeGraphemeClustering) {
        this.revision = revision;
        this.theme = theme;
        this.foregroundRGB = foregroundRGB == null ? null : foregroundRGB.clone();
        this.backgroundRGB = backgroundRGB == null ? null : backgroundRGB.clone();
        if (paletteColors == null) {
            this.paletteColors = null;
        } else {
            Map<Integer, int[]> copy = new HashMap<Integer, int[]>();
            for (Map.Entry<Integer, int[]> entry : paletteColors.entrySet()) {
                int[] value = entry.getValue();
                copy.put(entry.getKey(), value == null ? null : value.clone());
            }
            this.paletteColors = Collections.unmodifiableMap(copy);
        }
        this.mode2026Support = mode2026Support;
        this.mode2027Support = mode2027Support;
        this.nativeGraphemeClustering = nativeGraphemeClustering;
    }

    /**
     * Monotonically increasing revision of the owning capabilities instance.
     * Revisions order replayed state against later updates; they are opaque
     * otherwise and restart at zero for every new result instance.
     *
     * @return the revision that produced this snapshot
     */
    public long revision() {
        return revision;
    }

    /**
     * @return the resolved theme, never null in practice
     */
    public TerminalTheme theme() {
        return theme;
    }

    /**
     * @return RGB array [r, g, b], or null when unknown
     */
    public int[] foregroundRGB() {
        return foregroundRGB == null ? null : foregroundRGB.clone();
    }

    /**
     * @return RGB array [r, g, b], or null when unknown
     */
    public int[] backgroundRGB() {
        return backgroundRGB == null ? null : backgroundRGB.clone();
    }

    /**
     * @return an unmodifiable palette with defensive array copies, or null
     *         when unknown
     */
    public Map<Integer, int[]> paletteColors() {
        if (paletteColors == null) {
            return null;
        }
        Map<Integer, int[]> copy = new HashMap<Integer, int[]>();
        for (Map.Entry<Integer, int[]> entry : paletteColors.entrySet()) {
            int[] value = entry.getValue();
            copy.put(entry.getKey(), value == null ? null : value.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * @return synchronized-output mode support
     */
    public ModeSupport mode2026Support() {
        return mode2026Support;
    }

    /**
     * @return grapheme-cluster mode support
     */
    public ModeSupport mode2027Support() {
        return mode2027Support;
    }

    /**
     * @return grapheme clustering answer, or null when unprobed
     */
    public Boolean nativeGraphemeClustering() {
        return nativeGraphemeClustering;
    }
}
