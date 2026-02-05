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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents device attributes queried from a terminal via DA1/DA2 sequences.
 * <p>
 * Device attributes provide information about terminal capabilities that
 * cannot be determined from terminfo alone. This includes:
 * <ul>
 * <li>Terminal type and conformance level (VT100, VT220, xterm, etc.)</li>
 * <li>Supported features (sixel graphics, mouse, rectangular editing)</li>
 * <li>Firmware/version information</li>
 * </ul>
 * <p>
 * Use {@link org.aesh.terminal.Connection#queryDeviceAttributes(long)} to
 * obtain an instance of this class.
 *
 * @author Ståle Pedersen
 */
public class DeviceAttributes {

    /**
     * Feature flags that can be reported in DA1 response.
     */
    public enum Feature {
        /** 132-column mode (Ps=1) */
        COLUMNS_132(1),
        /** Printer port (Ps=2) */
        PRINTER(2),
        /** ReGIS graphics (Ps=3) */
        REGIS_GRAPHICS(3),
        /** Sixel graphics (Ps=4) */
        SIXEL(4),
        /** Selective erase (Ps=6) */
        SELECTIVE_ERASE(6),
        /** Soft character set / DRCS (Ps=7) */
        DRCS(7),
        /** User-defined keys (Ps=8) */
        USER_DEFINED_KEYS(8),
        /** National replacement character sets (Ps=9) */
        NATIONAL_CHARSETS(9),
        /** Technical character set (Ps=15) */
        TECHNICAL_CHARSETS(15),
        /** Locator port / mouse (Ps=16) - DEC locator */
        LOCATOR(16),
        /** Terminal state interrogation (Ps=17) */
        STATE_INTERROGATION(17),
        /** User windows (Ps=18) */
        USER_WINDOWS(18),
        /** Horizontal scrolling (Ps=21) */
        HORIZONTAL_SCROLLING(21),
        /** ANSI color (Ps=22) */
        ANSI_COLOR(22),
        /** Rectangular editing (Ps=28) */
        RECTANGULAR_EDITING(28),
        /** ANSI text locator / mouse (Ps=29) */
        ANSI_TEXT_LOCATOR(29);

        private final int code;

        Feature(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        /**
         * Find a Feature by its DA1 parameter code.
         *
         * @param code the DA1 parameter code
         * @return the Feature, or null if not recognized
         */
        public static Feature fromCode(int code) {
            for (Feature f : values()) {
                if (f.code == code) {
                    return f;
                }
            }
            return null;
        }
    }

    /**
     * Terminal type identifiers from DA2 response.
     */
    public enum TerminalType {
        VT100(0, "VT100"),
        VT220(1, "VT220"),
        VT240(2, "VT240"),
        VT330(18, "VT330"),
        VT340(19, "VT340"),
        VT320(24, "VT320"),
        VT382(32, "VT382"),
        VT420(41, "VT420"),
        VT510(61, "VT510"),
        VT520(64, "VT520"),
        VT525(65, "VT525"),
        XTERM(0, "xterm"), // xterm uses 0 but different version format
        UNKNOWN(-1, "Unknown");

        private final int code;
        private final String name;

        TerminalType(int code, String name) {
            this.code = code;
            this.name = name;
        }

        public int getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        /**
         * Find a TerminalType by its DA2 type code.
         * Note: Code 0 could be VT100 or xterm depending on version format.
         *
         * @param code the DA2 type code
         * @return the TerminalType, or UNKNOWN if not recognized
         */
        public static TerminalType fromCode(int code) {
            for (TerminalType t : values()) {
                if (t.code == code && t != XTERM && t != UNKNOWN) {
                    return t;
                }
            }
            return UNKNOWN;
        }
    }

    // DA1 (Primary Device Attributes) data
    private final int deviceClass;
    private final Set<Feature> features;
    private final Set<Integer> rawParameters;

    // DA2 (Secondary Device Attributes) data
    private final TerminalType terminalType;
    private final int firmwareVersion;
    private final int romCartridge;

    /**
     * Create DeviceAttributes from DA1 response data.
     *
     * @param deviceClass the device class (conformance level)
     * @param parameters the feature parameter codes from DA1 response
     */
    public DeviceAttributes(int deviceClass, Set<Integer> parameters) {
        this.deviceClass = deviceClass;
        this.rawParameters = parameters != null ? new HashSet<>(parameters) : new HashSet<>();
        this.features = parseFeatures(this.rawParameters);
        this.terminalType = TerminalType.UNKNOWN;
        this.firmwareVersion = -1;
        this.romCartridge = -1;
    }

    /**
     * Create DeviceAttributes from both DA1 and DA2 response data.
     *
     * @param deviceClass the device class from DA1
     * @param parameters the feature parameters from DA1
     * @param terminalType the terminal type from DA2
     * @param firmwareVersion the firmware version from DA2
     * @param romCartridge the ROM cartridge registration from DA2
     */
    public DeviceAttributes(int deviceClass, Set<Integer> parameters,
            TerminalType terminalType, int firmwareVersion, int romCartridge) {
        this.deviceClass = deviceClass;
        this.rawParameters = parameters != null ? new HashSet<>(parameters) : new HashSet<>();
        this.features = parseFeatures(this.rawParameters);
        this.terminalType = terminalType != null ? terminalType : TerminalType.UNKNOWN;
        this.firmwareVersion = firmwareVersion;
        this.romCartridge = romCartridge;
    }

    private Set<Feature> parseFeatures(Set<Integer> parameters) {
        Set<Feature> result = new HashSet<>();
        for (Integer code : parameters) {
            Feature f = Feature.fromCode(code);
            if (f != null) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Get the device class / conformance level.
     * <p>
     * Common values:
     * <ul>
     * <li>1 or 2: VT100</li>
     * <li>62: VT220</li>
     * <li>63: VT320</li>
     * <li>64: VT420</li>
     * </ul>
     *
     * @return the device class, or -1 if not available
     */
    public int getDeviceClass() {
        return deviceClass;
    }

    /**
     * Get the set of features reported by the terminal.
     *
     * @return unmodifiable set of features
     */
    public Set<Feature> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    /**
     * Get the raw parameter codes from DA1 response.
     * <p>
     * This includes both recognized and unrecognized parameters.
     *
     * @return unmodifiable set of parameter codes
     */
    public Set<Integer> getRawParameters() {
        return Collections.unmodifiableSet(rawParameters);
    }

    /**
     * Get the terminal type from DA2 response.
     *
     * @return the terminal type
     */
    public TerminalType getTerminalType() {
        return terminalType;
    }

    /**
     * Get the firmware version from DA2 response.
     *
     * @return the firmware version, or -1 if not available
     */
    public int getFirmwareVersion() {
        return firmwareVersion;
    }

    /**
     * Get the ROM cartridge registration number from DA2.
     *
     * @return the ROM cartridge number, or -1 if not available
     */
    public int getRomCartridge() {
        return romCartridge;
    }

    /**
     * Check if the terminal supports a specific feature.
     *
     * @param feature the feature to check
     * @return true if the feature is supported
     */
    public boolean hasFeature(Feature feature) {
        return features.contains(feature);
    }

    /**
     * Check if the terminal supports Sixel graphics.
     *
     * @return true if Sixel is supported
     */
    public boolean supportsSixel() {
        return hasFeature(Feature.SIXEL);
    }

    /**
     * Check if the terminal supports ANSI colors.
     *
     * @return true if ANSI color is supported
     */
    public boolean supportsAnsiColor() {
        return hasFeature(Feature.ANSI_COLOR);
    }

    /**
     * Check if the terminal supports mouse/locator.
     * <p>
     * This checks for DEC locator (Ps=16) or ANSI text locator (Ps=29).
     *
     * @return true if mouse/locator is supported
     */
    public boolean supportsMouse() {
        return hasFeature(Feature.LOCATOR) || hasFeature(Feature.ANSI_TEXT_LOCATOR);
    }

    /**
     * Check if the terminal supports rectangular editing operations.
     *
     * @return true if rectangular editing is supported
     */
    public boolean supportsRectangularEditing() {
        return hasFeature(Feature.RECTANGULAR_EDITING);
    }

    /**
     * Check if the terminal supports 132-column mode.
     *
     * @return true if 132-column mode is supported
     */
    public boolean supports132Columns() {
        return hasFeature(Feature.COLUMNS_132);
    }

    /**
     * Check if DA1 data is available.
     *
     * @return true if DA1 was successfully queried
     */
    public boolean hasDA1() {
        return deviceClass >= 0;
    }

    /**
     * Check if DA2 data is available.
     *
     * @return true if DA2 was successfully queried
     */
    public boolean hasDA2() {
        return terminalType != TerminalType.UNKNOWN || firmwareVersion >= 0;
    }

    /**
     * Check if the terminal likely supports OSC (Operating System Command) queries.
     * <p>
     * This is inferred from DA1 capabilities. Terminals that report modern features
     * like ANSI color or Sixel graphics typically also support OSC queries for
     * color detection (OSC 10/11), clipboard (OSC 52), etc.
     * <p>
     * Note: This is an inference, not a guarantee. Some terminals may report
     * these features but not support all OSC commands.
     *
     * @return true if OSC queries are likely supported based on DA1 features
     */
    public boolean likelySupportsOscQueries() {
        // Terminals reporting these modern features typically support OSC
        // Device class >= 62 indicates VT220+ which generally supports OSC
        return supportsAnsiColor() || supportsSixel() || deviceClass >= 62;
    }

    /**
     * Merge this DeviceAttributes with another, combining data from both.
     * <p>
     * This is useful when DA1 and DA2 are queried separately.
     *
     * @param other the other DeviceAttributes to merge with
     * @return a new DeviceAttributes with combined data
     */
    public DeviceAttributes merge(DeviceAttributes other) {
        if (other == null) {
            return this;
        }

        int mergedClass = this.deviceClass >= 0 ? this.deviceClass : other.deviceClass;
        Set<Integer> mergedParams = new HashSet<>(this.rawParameters);
        mergedParams.addAll(other.rawParameters);

        TerminalType mergedType = this.terminalType != TerminalType.UNKNOWN
                ? this.terminalType
                : other.terminalType;
        int mergedVersion = this.firmwareVersion >= 0
                ? this.firmwareVersion
                : other.firmwareVersion;
        int mergedRom = this.romCartridge >= 0
                ? this.romCartridge
                : other.romCartridge;

        return new DeviceAttributes(mergedClass, mergedParams, mergedType, mergedVersion, mergedRom);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeviceAttributes{");
        if (hasDA1()) {
            sb.append("class=").append(deviceClass);
            if (!features.isEmpty()) {
                sb.append(", features=").append(features);
            }
        }
        if (hasDA2()) {
            if (hasDA1()) {
                sb.append(", ");
            }
            sb.append("type=").append(terminalType.getName());
            if (firmwareVersion >= 0) {
                sb.append(", version=").append(firmwareVersion);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
