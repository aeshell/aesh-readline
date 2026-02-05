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

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.aesh.terminal.DeviceAttributes.Feature;
import org.aesh.terminal.DeviceAttributes.TerminalType;
import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

/**
 * Tests for DeviceAttributes class and DA1/DA2 parsing.
 *
 * @author Ståle Pedersen
 */
public class DeviceAttributesTest {

    // ==================== DA1 Query Tests ====================

    @Test
    public void testDA1QueryConstant() {
        assertEquals("\u001B[c", ANSI.DA1_QUERY);
    }

    @Test
    public void testParseDA1Response_VT220() {
        // VT220 response: ESC [ ? 62 ; 1 ; 2 ; 6 ; 7 ; 8 ; 9 c
        String response = "\u001B[?62;1;2;6;7;8;9c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull("Should parse valid DA1 response", da);
        assertEquals(62, da.getDeviceClass());
        assertTrue(da.hasDA1());
        assertFalse(da.hasDA2());

        // Check features
        assertTrue(da.supports132Columns()); // 1
        assertTrue(da.hasFeature(Feature.PRINTER)); // 2
        assertTrue(da.hasFeature(Feature.SELECTIVE_ERASE)); // 6
        assertTrue(da.hasFeature(Feature.DRCS)); // 7
        assertTrue(da.hasFeature(Feature.USER_DEFINED_KEYS)); // 8
        assertTrue(da.hasFeature(Feature.NATIONAL_CHARSETS)); // 9
    }

    @Test
    public void testParseDA1Response_XtermWithSixel() {
        // xterm with sixel: ESC [ ? 64 ; 4 ; 22 c
        String response = "\u001B[?64;4;22c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull(da);
        assertEquals(64, da.getDeviceClass());
        assertTrue(da.supportsSixel()); // 4
        assertTrue(da.supportsAnsiColor()); // 22
    }

    @Test
    public void testParseDA1Response_WithMouse() {
        // Terminal with mouse support: ESC [ ? 62 ; 29 c
        String response = "\u001B[?62;29c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull(da);
        assertTrue(da.supportsMouse()); // 29 = ANSI text locator
    }

    @Test
    public void testParseDA1Response_WithDecLocator() {
        // Terminal with DEC locator: ESC [ ? 62 ; 16 c
        String response = "\u001B[?62;16c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull(da);
        assertTrue(da.supportsMouse()); // 16 = DEC locator
        assertTrue(da.hasFeature(Feature.LOCATOR));
    }

    @Test
    public void testParseDA1Response_MinimalVT100() {
        // Minimal VT100 response: ESC [ ? 1 ; 0 c
        String response = "\u001B[?1;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull(da);
        assertEquals(1, da.getDeviceClass());
    }

    @Test
    public void testParseDA1Response_ClassOnly() {
        // Class only, no features: ESC [ ? 62 c
        String response = "\u001B[?62c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull(da);
        assertEquals(62, da.getDeviceClass());
        assertTrue(da.getFeatures().isEmpty());
    }

    @Test
    public void testParseDA1Response_WithPrefixNoise() {
        // Response with noise before it
        String response = "noise\u001B[?64;4c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA1Response(input);

        assertNotNull("Should parse response with prefix noise", da);
        assertEquals(64, da.getDeviceClass());
        assertTrue(da.supportsSixel());
    }

    @Test
    public void testParseDA1Response_NullInput() {
        assertNull(ANSI.parseDA1Response(null));
    }

    @Test
    public void testParseDA1Response_EmptyInput() {
        assertNull(ANSI.parseDA1Response(new int[0]));
    }

    @Test
    public void testParseDA1Response_InvalidFormat() {
        // Missing ? after [
        String response = "\u001B[62;4c";
        int[] input = response.codePoints().toArray();

        assertNull(ANSI.parseDA1Response(input));
    }

    // ==================== DA2 Query Tests ====================

    @Test
    public void testDA2QueryConstant() {
        assertEquals("\u001B[>c", ANSI.DA2_QUERY);
    }

    @Test
    public void testParseDA2Response_Xterm() {
        // xterm response: ESC [ > 0 ; 136 ; 0 c (type 0, version 136)
        String response = "\u001B[>0;136;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA2Response(input);

        assertNotNull("Should parse valid DA2 response", da);
        assertFalse(da.hasDA1());
        assertTrue(da.hasDA2());
        assertEquals(136, da.getFirmwareVersion());
        assertEquals(0, da.getRomCartridge());
    }

    @Test
    public void testParseDA2Response_VT220() {
        // VT220 response: ESC [ > 1 ; 10 ; 0 c
        String response = "\u001B[>1;10;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA2Response(input);

        assertNotNull(da);
        assertEquals(TerminalType.VT220, da.getTerminalType());
        assertEquals(10, da.getFirmwareVersion());
    }

    @Test
    public void testParseDA2Response_VT420() {
        // VT420 response: ESC [ > 41 ; 1 ; 0 c
        String response = "\u001B[>41;1;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA2Response(input);

        assertNotNull(da);
        assertEquals(TerminalType.VT420, da.getTerminalType());
    }

    @Test
    public void testParseDA2Response_UnknownType() {
        // Unknown terminal type
        String response = "\u001B[>99;50;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDA2Response(input);

        assertNotNull(da);
        assertEquals(TerminalType.UNKNOWN, da.getTerminalType());
        assertEquals(50, da.getFirmwareVersion());
    }

    @Test
    public void testParseDA2Response_NullInput() {
        assertNull(ANSI.parseDA2Response(null));
    }

    @Test
    public void testParseDA2Response_InvalidFormat() {
        // Missing > after [
        String response = "\u001B[0;136;0c";
        int[] input = response.codePoints().toArray();

        assertNull(ANSI.parseDA2Response(input));
    }

    // ==================== Combined DA Response Tests ====================

    @Test
    public void testParseDAResponse_BothDA1AndDA2() {
        // Combined response with both DA1 and DA2
        String response = "\u001B[?64;4;22c\u001B[>0;136;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDAResponse(input);

        assertNotNull(da);
        assertTrue(da.hasDA1());
        assertTrue(da.hasDA2());
        assertEquals(64, da.getDeviceClass());
        assertTrue(da.supportsSixel());
        assertTrue(da.supportsAnsiColor());
        assertEquals(136, da.getFirmwareVersion());
    }

    @Test
    public void testParseDAResponse_OnlyDA1() {
        String response = "\u001B[?64;4c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDAResponse(input);

        assertNotNull(da);
        assertTrue(da.hasDA1());
        assertFalse(da.hasDA2());
    }

    @Test
    public void testParseDAResponse_OnlyDA2() {
        String response = "\u001B[>1;10;0c";
        int[] input = response.codePoints().toArray();

        DeviceAttributes da = ANSI.parseDAResponse(input);

        assertNotNull(da);
        assertFalse(da.hasDA1());
        assertTrue(da.hasDA2());
    }

    // ==================== DeviceAttributes Class Tests ====================

    @Test
    public void testDeviceAttributesMerge() {
        Set<Integer> features = new HashSet<>();
        features.add(4); // Sixel
        features.add(22); // ANSI color

        DeviceAttributes da1 = new DeviceAttributes(64, features);
        DeviceAttributes da2 = new DeviceAttributes(-1, null,
                TerminalType.VT420, 100, 0);

        DeviceAttributes merged = da1.merge(da2);

        assertEquals(64, merged.getDeviceClass());
        assertTrue(merged.supportsSixel());
        assertTrue(merged.supportsAnsiColor());
        assertEquals(TerminalType.VT420, merged.getTerminalType());
        assertEquals(100, merged.getFirmwareVersion());
    }

    @Test
    public void testDeviceAttributesMerge_Null() {
        Set<Integer> features = new HashSet<>();
        features.add(4);

        DeviceAttributes da = new DeviceAttributes(64, features);
        DeviceAttributes merged = da.merge(null);

        assertSame(da, merged);
    }

    @Test
    public void testFeatureFromCode() {
        assertEquals(Feature.SIXEL, Feature.fromCode(4));
        assertEquals(Feature.ANSI_COLOR, Feature.fromCode(22));
        assertEquals(Feature.ANSI_TEXT_LOCATOR, Feature.fromCode(29));
        assertNull(Feature.fromCode(999));
    }

    @Test
    public void testTerminalTypeFromCode() {
        assertEquals(TerminalType.VT100, TerminalType.fromCode(0));
        assertEquals(TerminalType.VT220, TerminalType.fromCode(1));
        assertEquals(TerminalType.VT420, TerminalType.fromCode(41));
        assertEquals(TerminalType.UNKNOWN, TerminalType.fromCode(999));
    }

    @Test
    public void testDeviceAttributesToString() {
        Set<Integer> features = new HashSet<>();
        features.add(4);
        features.add(22);

        DeviceAttributes da = new DeviceAttributes(64, features,
                TerminalType.VT420, 100, 0);

        String str = da.toString();
        assertTrue(str.contains("class=64"));
        assertTrue(str.contains("SIXEL"));
        assertTrue(str.contains("VT420"));
        assertTrue(str.contains("version=100"));
    }

    @Test
    public void testRawParameters() {
        Set<Integer> features = new HashSet<>();
        features.add(4);
        features.add(999); // Unknown feature code

        DeviceAttributes da = new DeviceAttributes(64, features);

        // Raw parameters should include unknown code
        assertTrue(da.getRawParameters().contains(999));
        // But features set should not
        assertFalse(da.getFeatures().contains(Feature.fromCode(999)));
        // Known feature should be in both
        assertTrue(da.getRawParameters().contains(4));
        assertTrue(da.hasFeature(Feature.SIXEL));
    }

    // ==================== OSC Support Inference Tests ====================

    @Test
    public void testLikelySupportsOscQueries_WithAnsiColor() {
        Set<Integer> features = new HashSet<>();
        features.add(22); // ANSI_COLOR

        DeviceAttributes da = new DeviceAttributes(64, features);

        assertTrue("Terminal with ANSI color should likely support OSC",
                da.likelySupportsOscQueries());
    }

    @Test
    public void testLikelySupportsOscQueries_WithSixel() {
        Set<Integer> features = new HashSet<>();
        features.add(4); // SIXEL

        DeviceAttributes da = new DeviceAttributes(1, features);

        assertTrue("Terminal with Sixel should likely support OSC",
                da.likelySupportsOscQueries());
    }

    @Test
    public void testLikelySupportsOscQueries_VT220Plus() {
        // VT220 (class 62) and above typically support OSC
        DeviceAttributes da = new DeviceAttributes(62, new HashSet<>());

        assertTrue("VT220+ terminals should likely support OSC",
                da.likelySupportsOscQueries());
    }

    @Test
    public void testLikelySupportsOscQueries_VT100() {
        // VT100 (class 1) without modern features - less likely to support OSC
        DeviceAttributes da = new DeviceAttributes(1, new HashSet<>());

        assertFalse("Basic VT100 without features should not indicate OSC support",
                da.likelySupportsOscQueries());
    }

    @Test
    public void testLikelySupportsOscQueries_ModernTerminal() {
        // Modern terminal with multiple features
        Set<Integer> features = new HashSet<>();
        features.add(4); // Sixel
        features.add(22); // ANSI color
        features.add(29); // Mouse

        DeviceAttributes da = new DeviceAttributes(64, features);

        assertTrue(da.likelySupportsOscQueries());
        assertTrue(da.supportsSixel());
        assertTrue(da.supportsAnsiColor());
        assertTrue(da.supportsMouse());
    }
}
