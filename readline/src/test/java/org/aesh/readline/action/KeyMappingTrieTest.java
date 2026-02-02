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
package org.aesh.readline.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.Key;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for KeyMappingTrie.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class KeyMappingTrieTest {

    private KeyMappingTrie trie;

    @Before
    public void setUp() {
        trie = new KeyMappingTrie();
        trie.build(Key.values());
    }

    @Test
    public void testSingleCharacterMatch() {
        // Test lowercase 'a' (code 97)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 97 });
        assertNotNull(result.action);
        assertEquals(Key.a, result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testSingleCharacterMatchViaFastPath() {
        // Test lowercase 'a' (code 97) using the fast path
        KeyMappingTrie.MatchResult result = trie.matchSingleByte(97);
        assertNotNull(result.action);
        assertEquals(Key.a, result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testControlKeyMatch() {
        // Test CTRL_A (code 1)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 1 });
        assertNotNull(result.action);
        assertEquals(Key.CTRL_A, result.action);
    }

    @Test
    public void testEscapeSequenceMatch() {
        // Arrow up: ESC [ A (27, 91, 65)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27, 91, 65 });
        assertNotNull(result.action);
        assertEquals(Key.UP, result.action);
    }

    @Test
    public void testPrefixDetection() {
        // Just ESC (27) - should be a valid key and also a prefix of longer sequences
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27 });
        assertNotNull(result.action);
        assertEquals(Key.ESC, result.action);
        assertTrue(result.hasPrefix); // ESC is prefix of UP, DOWN, META_a, etc.
    }

    @Test
    public void testPartialEscapeSequence() {
        // ESC [ (27, 91) - not a complete sequence but prefix of UP, DOWN, etc.
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27, 91 });
        // ESC alone is matched, but 91 after it continues the sequence
        // The result depends on whether [27, 91] matches anything in Key enum
        // LEFT_SQUARE_BRACKET is 91 alone, not ESC+91
        assertNotNull(result.action);
        assertEquals(Key.ESC, result.action); // ESC is the longest match at position 0
        assertTrue(result.hasPrefix); // But 27, 91 is prefix of arrow keys
    }

    @Test
    public void testLongestMatch() {
        // CTRL_LEFT: ESC [ 1 ; 5 D (27, 91, 49, 59, 53, 68)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27, 91, 49, 59, 53, 68 });
        assertNotNull(result.action);
        assertEquals(Key.CTRL_LEFT, result.action);
    }

    @Test
    public void testMetaKeyMatch() {
        // META_a: ESC a (27, 97)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27, 97 });
        assertNotNull(result.action);
        assertEquals(Key.META_a, result.action);
    }

    @Test
    public void testNoMatchReturnsNull() {
        // An invalid sequence that doesn't match anything
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 255, 254, 253 });
        // 255 doesn't match any key
        assertNull(result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testEmptyBufferReturnsNull() {
        KeyMappingTrie.MatchResult result = trie.match(new int[] {});
        assertNull(result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testNullBufferReturnsNull() {
        KeyMappingTrie.MatchResult result = trie.match(null);
        assertNull(result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testDeleteKey() {
        // DELETE: ESC [ 3 ~ (27, 91, 51, 126) - default
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27, 91, 51, 126 });
        assertNotNull(result.action);
        assertEquals(Key.DELETE, result.action);
    }

    @Test
    public void testPrintableCharacters() {
        // Test space (32)
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 32 });
        assertNotNull(result.action);
        assertEquals(Key.SPACE, result.action);

        // Test 'Z' (90)
        result = trie.match(new int[] { 90 });
        assertNotNull(result.action);
        assertEquals(Key.Z, result.action);
    }

    @Test
    public void testRebuildTrie() {
        // Build with only a subset of keys
        trie.build(new Key[] { Key.a, Key.b, Key.c });

        KeyMappingTrie.MatchResult result = trie.match(new int[] { 97 });
        assertNotNull(result.action);
        assertEquals(Key.a, result.action);

        // 'd' should not be found now
        result = trie.match(new int[] { 100 });
        assertNull(result.action);
        assertFalse(result.hasPrefix);

        // Rebuild with all keys
        trie.build(Key.values());

        // 'd' should be found now
        result = trie.match(new int[] { 100 });
        assertNotNull(result.action);
        assertEquals(Key.d, result.action);
    }

    @Test
    public void testSingleByteOutOfRange() {
        // Test a code point > 255 via matchSingleByte (should fall back to match)
        KeyMappingTrie.MatchResult result = trie.matchSingleByte(300);
        // 300 is not a standard key, so no match
        assertNull(result.action);
        assertFalse(result.hasPrefix);
    }

    @Test
    public void testMultipleMatchingPrefixes() {
        // ESC alone matches Key.ESC
        // ESC followed by 'a' matches Key.META_a
        // This tests that we get the longest match

        // First, just ESC
        KeyMappingTrie.MatchResult result = trie.match(new int[] { 27 });
        assertEquals(Key.ESC, result.action);
        assertTrue(result.hasPrefix);

        // Now ESC + 'a'
        result = trie.match(new int[] { 27, 97 });
        assertEquals(Key.META_a, result.action);
    }
}
