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
package org.aesh.terminal.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class WcWidthTest {

    @Test
    public void testWidth() {
        //assertEquals(1, WcWidth.width('æ'));
        assertEquals(1, org.aesh.terminal.utils.WcWidth.width('s'));
        assertEquals(1, org.aesh.terminal.utils.WcWidth.width('h'));
        assertEquals(0, org.aesh.terminal.utils.WcWidth.width('\0'));
        assertEquals(-1, org.aesh.terminal.utils.WcWidth.width('\n'));
        assertEquals(-1, org.aesh.terminal.utils.WcWidth.width('\r'));
        assertEquals(-1, org.aesh.terminal.utils.WcWidth.width('\t'));
        assertEquals(-1, org.aesh.terminal.utils.WcWidth.width('\u001B'));
    }

    // ==================== Emoji Width Tests ====================

    @Test
    public void testEmojiWidth_MiscSymbolsAndPictographs() {
        // U+1F300 Cyclone (start of range)
        assertEquals(2, WcWidth.width(0x1F300));
        // U+1F4A9 Pile of Poo
        assertEquals(2, WcWidth.width(0x1F4A9));
        // U+1F5FF Moyai (end of range)
        assertEquals(2, WcWidth.width(0x1F5FF));
    }

    @Test
    public void testEmojiWidth_Emoticons() {
        // U+1F600 Grinning Face
        assertEquals(2, WcWidth.width(0x1F600));
        // U+1F60D Heart Eyes
        assertEquals(2, WcWidth.width(0x1F60D));
        // U+1F64F Person with Folded Hands (end of range)
        assertEquals(2, WcWidth.width(0x1F64F));
    }

    @Test
    public void testEmojiWidth_TransportAndMap() {
        // U+1F680 Rocket
        assertEquals(2, WcWidth.width(0x1F680));
        // U+1F6FF (end of range)
        assertEquals(2, WcWidth.width(0x1F6FF));
    }

    @Test
    public void testEmojiWidth_SupplementalSymbols() {
        // U+1F900 (start of Supplemental Symbols)
        assertEquals(2, WcWidth.width(0x1F900));
        // U+1F9FF (end of Supplemental Symbols)
        assertEquals(2, WcWidth.width(0x1F9FF));
    }

    @Test
    public void testEmojiWidth_SymbolsExtendedA() {
        // U+1FA70 (start of Symbols Extended-A)
        assertEquals(2, WcWidth.width(0x1FA70));
        // U+1FAFF (end of range)
        assertEquals(2, WcWidth.width(0x1FAFF));
    }

    @Test
    public void testEmojiWidth_GeometricShapesExtendedEmoji() {
        // Only the colored emoji circles/squares (U+1F7E0-U+1F7EB) have Emoji_Presentation
        assertEquals(2, WcWidth.width(0x1F7E0)); // Large Orange Circle
        assertEquals(2, WcWidth.width(0x1F7EB)); // Large Brown Square
    }

    @Test
    public void testNonEmojiBlocks_Width1() {
        // Alchemical Symbols (U+1F700-1F77F) are NOT emoji, should be width 1
        assertEquals(1, WcWidth.width(0x1F700));
        assertEquals(1, WcWidth.width(0x1F77F));
        // Supplemental Arrows-C (U+1F800-1F8FF) are NOT emoji, should be width 1
        assertEquals(1, WcWidth.width(0x1F800));
        assertEquals(1, WcWidth.width(0x1F8FF));
        // Non-emoji part of Geometric Shapes Extended (before U+1F7E0) should be width 1
        assertEquals(1, WcWidth.width(0x1F780));
        // Chess Symbols (U+1FA00-1FA6F) are NOT emoji (no Emoji_Presentation), should be width 1
        assertEquals(1, WcWidth.width(0x1FA00));
        assertEquals(1, WcWidth.width(0x1FA6F));
    }

    @Test
    public void testEmojiWidth_SkinToneModifiers() {
        // Emoji_Modifier characters U+1F3FB-U+1F3FF are width 2 when standalone
        // (they render as visible colored squares; when combined with a preceding
        // emoji via Mode 2027 grapheme clustering, they form a skin-toned emoji)
        assertEquals(2, WcWidth.width(0x1F3FB));
        assertEquals(2, WcWidth.width(0x1F3FF));
    }

    @Test
    public void testZeroWidthJoiner() {
        // U+200D ZWJ should be zero-width (it's in the COMBINING table)
        assertEquals(0, WcWidth.width(0x200D));
    }

    @Test
    public void testVariationSelectorsSupplementInCombining() {
        // Variation Selectors Supplement U+E0100-U+E01EF should be zero-width
        assertEquals(0, WcWidth.width(0xE0100));
        assertEquals(0, WcWidth.width(0xE01EF));
    }

    @Test
    public void testCjkStillWidth2() {
        // CJK characters should still be width 2
        assertEquals(2, WcWidth.width(0x4E00)); // CJK Unified Ideograph
        assertEquals(2, WcWidth.width(0x3000)); // Ideographic Space (CJK)
        assertEquals(2, WcWidth.width(0xAC00)); // Hangul Syllable
    }

    @Test
    public void testAsciiStillWidth1() {
        // ASCII characters should still be width 1
        assertEquals(1, WcWidth.width('A'));
        assertEquals(1, WcWidth.width('z'));
        assertEquals(1, WcWidth.width('0'));
        assertEquals(1, WcWidth.width(' '));
    }

}
