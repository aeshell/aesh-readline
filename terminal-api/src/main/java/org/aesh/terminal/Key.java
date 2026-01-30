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

import java.nio.IntBuffer;

import org.aesh.terminal.utils.CodePointUtils;
import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.InfoCmpHelper;

/**
 * ANSCII enum key chart
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum Key implements KeyAction {
    /** Control + @ key. */
    CTRL_AT(new int[] { 0 }),
    /** Control + A key. */
    CTRL_A(new int[] { 1 }),
    /** Control + B key. */
    CTRL_B(new int[] { 2 }),
    /** Control + C key. */
    CTRL_C(new int[] { 3 }),
    /** Control + D key. */
    CTRL_D(new int[] { 4 }),
    /** Control + E key. */
    CTRL_E(new int[] { 5 }),
    /** Control + F key. */
    CTRL_F(new int[] { 6 }),
    /** Control + G key. */
    CTRL_G(new int[] { 7 }),
    /** Control + H key. */
    CTRL_H(new int[] { 8 }),
    /** Control + I key. */
    CTRL_I(new int[] { 9 }),
    /** Control + J key. */
    CTRL_J(new int[] { 10 }),
    /** Control + K key. */
    CTRL_K(new int[] { 11 }),
    /** Control + L key. */
    CTRL_L(new int[] { 12 }),
    /** Control + M key. */
    CTRL_M(new int[] { 13 }),
    /** Control + N key. */
    CTRL_N(new int[] { 14 }),
    /** Control + O key. */
    CTRL_O(new int[] { 15 }),
    /** Control + P key. */
    CTRL_P(new int[] { 16 }),
    /** Control + Q key. */
    CTRL_Q(new int[] { 17 }),
    /** Control + R key. */
    CTRL_R(new int[] { 18 }),
    /** Control + S key. */
    CTRL_S(new int[] { 19 }),
    /** Control + T key. */
    CTRL_T(new int[] { 20 }),
    /** Control + U key. */
    CTRL_U(new int[] { 21 }),
    /** Control + V key. */
    CTRL_V(new int[] { 22 }),
    /** Control + W key. */
    CTRL_W(new int[] { 23 }),
    /** Control + X key. */
    CTRL_X(new int[] { 24 }),
    /** Control + Y key. */
    CTRL_Y(new int[] { 25 }),
    /** Control + Z key. */
    CTRL_Z(new int[] { 26 }),
    /** Escape key (also Control + [). */
    ESC(new int[] { 27 }),
    /** File separator key (Control + \). */
    FILE_SEPARATOR(new int[] { 28 }),
    /** Group separator key (Control + ]). */
    GROUP_SEPARATOR(new int[] { 29 }),
    /** Record separator key (Control + ^). */
    RECORD_SEPARATOR(new int[] { 30 }),
    /** Unit separator key (Control + _). */
    UNIT_SEPARATOR(new int[] { 31 }),

    /** Space key. */
    SPACE(new int[] { 32 }),
    /** Exclamation mark (!) key. */
    EXCLAMATION(new int[] { 33 }),
    /** Quote (") key. */
    QUOTE(new int[] { 34 }),
    /** Hash (#) key. */
    HASH(new int[] { 35 }),
    /** Dollar ($) key. */
    DOLLAR(new int[] { 36 }),
    /** Percent (%) key. */
    PERCENT(new int[] { 37 }),
    /** Ampersand (&amp;) key. */
    AMPERSAND(new int[] { 38 }),
    /** Apostrophe (') key. */
    APOSTROPHE(new int[] { 39 }),
    /** Left parenthesis (() key. */
    LEFT_PARANTHESIS(new int[] { 40 }),
    /** Right parenthesis ()) key. */
    RIGHT_PARANTHESIS(new int[] { 41 }),
    /** Star (*) key. */
    STAR(new int[] { 42 }),
    /** Plus (+) key. */
    PLUS(new int[] { 43 }),
    /** Comma (,) key. */
    COMMA(new int[] { 44 }),
    /** Minus (-) key. */
    MINUS(new int[] { 45 }),
    /** Period (.) key. */
    PERIOD(new int[] { 46 }),
    /** Slash (/) key. */
    SLASH(new int[] { 47 }),
    /** Number 0 key. */
    ZERO(new int[] { 48 }),
    /** Number 1 key. */
    ONE(new int[] { 49 }),
    /** Number 2 key. */
    TWO(new int[] { 50 }),
    /** Number 3 key. */
    THREE(new int[] { 51 }),
    /** Number 4 key. */
    FOUR(new int[] { 52 }),
    /** Number 5 key. */
    FIVE(new int[] { 53 }),
    /** Number 6 key. */
    SIX(new int[] { 54 }),
    /** Number 7 key. */
    SEVEN(new int[] { 55 }),
    /** Number 8 key. */
    EIGHT(new int[] { 56 }),
    /** Number 9 key. */
    NINE(new int[] { 57 }),
    /** Colon (:) key. */
    COLON(new int[] { 58 }),
    /** Semicolon (;) key. */
    SEMI_COLON(new int[] { 59 }),
    /** Less than (&lt;) key. */
    LESS_THAN(new int[] { 60 }),
    /** Equals (=) key. */
    EQUALS(new int[] { 61 }),
    /** Greater than (&gt;) key. */
    GREATER_THAN(new int[] { 62 }),
    /** Question mark (?) key. */
    QUESTION_MARK(new int[] { 63 }),
    /** At (@) key. */
    AT(new int[] { 64 }),
    /** Uppercase A key. */
    A(new int[] { 65 }),
    /** Uppercase B key. */
    B(new int[] { 66 }),
    /** Uppercase C key. */
    C(new int[] { 67 }),
    /** Uppercase D key. */
    D(new int[] { 68 }),
    /** Uppercase E key. */
    E(new int[] { 69 }),
    /** Uppercase F key. */
    F(new int[] { 70 }),
    /** Uppercase G key. */
    G(new int[] { 71 }),
    /** Uppercase H key. */
    H(new int[] { 72 }),
    /** Uppercase I key. */
    I(new int[] { 73 }),
    /** Uppercase J key. */
    J(new int[] { 74 }),
    /** Uppercase K key. */
    K(new int[] { 75 }),
    /** Uppercase L key. */
    L(new int[] { 76 }),
    /** Uppercase M key. */
    M(new int[] { 77 }),
    /** Uppercase N key. */
    N(new int[] { 78 }),
    /** Uppercase O key. */
    O(new int[] { 79 }),
    /** Uppercase P key. */
    P(new int[] { 80 }),
    /** Uppercase Q key. */
    Q(new int[] { 81 }),
    /** Uppercase R key. */
    R(new int[] { 82 }),
    /** Uppercase S key. */
    S(new int[] { 83 }),
    /** Uppercase T key. */
    T(new int[] { 84 }),
    /** Uppercase U key. */
    U(new int[] { 85 }),
    /** Uppercase V key. */
    V(new int[] { 86 }),
    /** Uppercase W key. */
    W(new int[] { 87 }),
    /** Uppercase X key. */
    X(new int[] { 88 }),
    /** Uppercase Y key. */
    Y(new int[] { 89 }),
    /** Uppercase Z key. */
    Z(new int[] { 90 }),
    /** Left square bracket ([) key. */
    LEFT_SQUARE_BRACKET(new int[] { 91 }),
    /** Backslash (\) key. */
    BACKSLASH(new int[] { 92 }),
    /** Right square bracket (]) key. */
    RIGHT_SQUARE_BRACKET(new int[] { 93 }),
    /** Hat (^) key. */
    HAT(new int[] { 94 }),
    /** Underscore (_) key. */
    UNDERSCORE(new int[] { 95 }),
    /** Grave accent (`) key. */
    GRAVE(new int[] { 96 }),
    /** Lowercase a key. */
    a(new int[] { 97 }),
    /** Lowercase b key. */
    b(new int[] { 98 }),
    /** Lowercase c key. */
    c(new int[] { 99 }),
    /** Lowercase d key. */
    d(new int[] { 100 }),
    /** Lowercase e key. */
    e(new int[] { 101 }),
    /** Lowercase f key. */
    f(new int[] { 102 }),
    /** Lowercase g key. */
    g(new int[] { 103 }),
    /** Lowercase h key. */
    h(new int[] { 104 }),
    /** Lowercase i key. */
    i(new int[] { 105 }),
    /** Lowercase j key. */
    j(new int[] { 106 }),
    /** Lowercase k key. */
    k(new int[] { 107 }),
    /** Lowercase l key. */
    l(new int[] { 108 }),
    /** Lowercase m key. */
    m(new int[] { 109 }),
    /** Lowercase n key. */
    n(new int[] { 110 }),
    /** Lowercase o key. */
    o(new int[] { 111 }),
    /** Lowercase p key. */
    p(new int[] { 112 }),
    /** Lowercase q key. */
    q(new int[] { 113 }),
    /** Lowercase r key. */
    r(new int[] { 114 }),
    /** Lowercase s key. */
    s(new int[] { 115 }),
    /** Lowercase t key. */
    t(new int[] { 116 }),
    /** Lowercase u key. */
    u(new int[] { 117 }),
    /** Lowercase v key. */
    v(new int[] { 118 }),
    /** Lowercase w key. */
    w(new int[] { 119 }),
    /** Lowercase x key. */
    x(new int[] { 120 }),
    /** Lowercase y key. */
    y(new int[] { 121 }),
    /** Lowercase z key. */
    z(new int[] { 122 }),
    /** Left curly bracket ({) key. */
    LEFT_CURLY_BRACKET(new int[] { 123 }),
    /** Vertical bar (|) key. */
    VERTICAL_BAR(new int[] { 124 }),
    /** Right curly bracket (}) key. */
    RIGHT_CURLY_BRACKET(new int[] { 125 }),
    /** Tilde (~) key. */
    TILDE(new int[] { 126 }),

    /** Backspace key. */
    BACKSPACE(Config.isOSPOSIXCompatible() ? new int[] { 127 } : new int[] { 8 }),

    /** Windows escape sequence identifier. */
    WINDOWS_ESC(new int[] { 224 }),
    /** Windows escape sequence identifier (alternate). */
    WINDOWS_ESC_2(new int[] { 341 }),
    /** Up arrow key. */
    UP(new int[] { ESC.getFirstValue(), 91, 65 }),
    /** Down arrow key. */
    DOWN(new int[] { ESC.getFirstValue(), 91, 66 }),
    /** Right arrow key. */
    RIGHT(new int[] { ESC.getFirstValue(), 91, 67 }),
    /** Left arrow key. */
    LEFT(new int[] { ESC.getFirstValue(), 91, 68 }),

    /** Up arrow key (alternate encoding). */
    UP_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcuu1", new int[] { 27, 79, 65 })),
    /** Down arrow key (alternate encoding). */
    DOWN_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcud1", new int[] { 27, 79, 66 })),
    /** Right arrow key (alternate encoding). */
    RIGHT_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("cuf1", new int[] { 27, 79, 67 })),
    /** Left arrow key (alternate encoding). */
    LEFT_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcub1", new int[] { 27, 79, 68 })),

    /** Meta (Alt) + a combination. */
    META_a(new int[] { ESC.getFirstValue(), a.getFirstValue() }),
    /** Meta (Alt) + b combination. */
    META_b(new int[] { ESC.getFirstValue(), b.getFirstValue() }),
    /** Meta (Alt) + c combination. */
    META_c(new int[] { ESC.getFirstValue(), c.getFirstValue() }),
    /** Meta (Alt) + d combination. */
    META_d(new int[] { ESC.getFirstValue(), d.getFirstValue() }),
    /** Meta (Alt) + e combination. */
    META_e(new int[] { ESC.getFirstValue(), e.getFirstValue() }),
    /** Meta (Alt) + f combination. */
    META_f(new int[] { ESC.getFirstValue(), f.getFirstValue() }),
    /** Meta (Alt) + l combination. */
    META_l(new int[] { ESC.getFirstValue(), l.getFirstValue() }),
    /** Meta (Alt) + u combination. */
    META_u(new int[] { ESC.getFirstValue(), u.getFirstValue() }),

    /** Meta (Alt) + Backspace combination. */
    META_BACKSPACE(new int[] { ESC.getFirstValue(), BACKSPACE.getFirstValue() }),

    /** Delete key. */
    DELETE(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kdch1", new int[] { 27, 91, 51, 126 })),

    /** Insert key. */
    INSERT(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kich1", new int[] { 27, 91, 50, 126 })),
    /** Page Up key. */
    PGUP(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kpp", new int[] { 27, 91, 53, 126 })),
    /** Page Up key (alternate encoding for Solaris). */
    PGUP_2(new int[] { 27, 91, 53, 126 }),
    /** Page Down key. */
    PGDOWN(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("knp", new int[] { 27, 91, 54, 126 })),
    /** Page Down key (alternate encoding for Solaris). */
    PGDOWN_2(new int[] { 27, 91, 54, 126 }),
    /** Home key. */
    HOME(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("khome", new int[] { 27, 79, 72 })),
    /** Home key (alternate encoding). */
    HOME_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("khome2", new int[] { 27, 91, 72 })),
    /** Home key (alternate encoding). */
    HOME_3(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("khome3", new int[] { 27, 91, 49, 126 })),
    /** End key. */
    END(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kend", new int[] { 27, 79, 70 })),
    /** End key (alternate encoding). */
    END_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kend2", new int[] { 27, 91, 70 })),
    /** End key (alternate encoding). */
    END_3(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kend3", new int[] { 27, 91, 52, 126 })),

    /** Meta (Alt) + Control + J combination. */
    META_CTRL_J(new int[] { ESC.getFirstValue(), 10 }),
    /** Meta (Alt) + Control + D combination. */
    META_CTRL_D(new int[] { ESC.getFirstValue(), 4 }),
    /** Control + X followed by Control + U combination. */
    CTRL_X_CTRL_U(new int[] { CTRL_X.getFirstValue(), CTRL_U.getFirstValue() }),

    /** Control + Left arrow key. */
    CTRL_LEFT(new int[] { ESC.getFirstValue(), 91, 49, 59, 53, 68 }),
    /** Control + Right arrow key. */
    CTRL_RIGHT(new int[] { ESC.getFirstValue(), 91, 49, 59, 53, 67 }),
    /** Control + Up arrow key. */
    CTRL_UP(new int[] { ESC.getFirstValue(), 91, 49, 59, 53, 65 }),
    /** Control + Down arrow key. */
    CTRL_DOWN(new int[] { ESC.getFirstValue(), 91, 49, 59, 53, 66 }),

    /** Enter key. */
    ENTER(Config.isOSPOSIXCompatible() ? new int[] { 10 } : new int[] { 13 }),
    /** Enter key (alternate encoding for Windows CR+LF). */
    ENTER_2(Config.isOSPOSIXCompatible() ? new int[] { 10 } : new int[] { 13, 10 });

    private final IntBuffer keyValues;

    Key(int[] keyValues) {
        this.keyValues = IntBuffer.allocate(keyValues.length);
        this.keyValues.put(keyValues);

    }

    /**
     * Checks if this key represents an alphabetic character (a-z or A-Z).
     *
     * @return true if this key is an alphabetic character
     */
    public boolean isCharacter() {
        return (keyValues.limit() == 1 &&
                ((keyValues.get(0) > 63 && keyValues.get(0) < 91) || (keyValues.get(0) > 96 && keyValues.get(0) < 123)));
    }

    /**
     * Checks if this key represents a numeric digit (0-9).
     *
     * @return true if this key is a numeric digit
     */
    public boolean isNumber() {
        return (keyValues.limit() == 1 && ((keyValues.get(0) > 47) && (keyValues.get(0) < 58)));
    }

    /**
     * Checks if this key represents a printable character.
     *
     * @return true if this key is a printable character
     */
    public boolean isPrintable() {
        return isPrintable(getKeyValues());
    }

    /**
     * Checks if the given code point value represents a printable character.
     *
     * @param value the code point value to check
     * @return true if the value represents a printable character
     */
    public static boolean isPrintable(int value) {
        if (Config.isOSPOSIXCompatible())
            return (((value > 31 && value < 127) || value > 127));
        else
            return (((value > 31 && value < 127) ||
                    (value > 127 &&
                            value != WINDOWS_ESC.getFirstValue() &&
                            value != WINDOWS_ESC_2.getFirstValue())));

    }

    /**
     * Checks if the given key values represent a single printable character.
     *
     * @param keyValues the array of key values to check
     * @return true if the values represent a single printable character
     */
    public static boolean isPrintable(int[] keyValues) {
        return (keyValues.length == 1 && isPrintable(keyValues[0]));
    }

    /**
     * Checks if the given IntBuffer key values represent a single printable character.
     *
     * @param keyValues the IntBuffer of key values to check
     * @return true if the values represent a single printable character
     */
    public static boolean isPrintable(IntBuffer keyValues) {
        if (Config.isOSPOSIXCompatible())
            return (keyValues.limit() == 1 && ((keyValues.get(0) > 31 && keyValues.get(0) < 127) || keyValues.get(0) > 127));
        else
            return (keyValues.limit() == 1 && ((keyValues.get(0) > 31 && keyValues.get(0) < 127) ||
                    (keyValues.get(0) > 127 &&
                            keyValues.get(0) != WINDOWS_ESC.getFirstValue() &&
                            keyValues.get(0) != WINDOWS_ESC_2.getFirstValue())));
    }

    /**
     * Returns the key value as a character.
     *
     * @return the first key value as a char
     */
    public char getAsChar() {
        return (char) keyValues.get(0);
    }

    /**
     * Returns the key values as an int array.
     *
     * @return the array of key code points
     */
    public int[] getKeyValues() {
        return keyValues.array();
    }

    /**
     * Returns the key values converted to a String.
     *
     * @return the key values as a String
     */
    public String getKeyValuesAsString() {
        return CodePointUtils.fromCodePoints(keyValues.array());
    }

    /**
     * Returns the first key value.
     *
     * @return the first code point value of this key
     */
    public int getFirstValue() {
        return keyValues.get(0);
    }

    /**
     * Checks if the input starts with an escape sequence.
     *
     * @param input the input array to check
     * @return true if the input starts with an escape character
     */
    public static boolean startsWithEscape(int[] input) {
        return ((Config.isOSPOSIXCompatible() && input[0] == Key.ESC.getFirstValue()) ||
                (!Config.isOSPOSIXCompatible() && input[0] == Key.WINDOWS_ESC.getFirstValue()));
    }

    /**
     * Finds the Key enum constant that matches the given values.
     *
     * @param otherValues the key values to match
     * @return the matching Key, or null if no match is found
     */
    public static Key getKey(int[] otherValues) {
        for (Key key : Key.values()) {
            if (key.equalTo(otherValues))
                return key;
        }
        return null;
    }

    /**
     * Finds the Key that matches the start of the input array.
     *
     * @param input the input array to search
     * @return the matching Key, or null if no match is found
     */
    public static Key findStartKey(int[] input) {
        for (Key key : values()) {
            if (key != Key.ESC && key != Key.WINDOWS_ESC &&
                    key.inputStartsWithKey(input)) {
                if (Config.isOSPOSIXCompatible() && key == Key.CTRL_J) {
                    return ENTER;
                } else if (!Config.isOSPOSIXCompatible() && key == Key.CTRL_M) {
                    if (input.length > 1 && input[1] == Key.CTRL_J.getFirstValue())
                        return ENTER_2;
                    else
                        return ENTER;
                } else
                    return key;
            }
        }
        //need to do this in two steps since esc/windows_esc would be returned always
        if (Key.ESC.inputStartsWithKey(input))
            return Key.ESC;
        else if (Key.WINDOWS_ESC.inputStartsWithKey(input))
            return Key.WINDOWS_ESC;

        return null;
    }

    /**
     * Finds the Key that matches the input array starting at the given position.
     *
     * @param input the input array to search
     * @param position the starting position in the input array
     * @return the matching Key, or null if no match is found
     */
    public static Key findStartKey(int[] input, int position) {
        for (Key key : values()) {
            if (key != Key.ESC && key != Key.WINDOWS_ESC &&
                    key.inputStartsWithKey(input, position)) {
                if (Config.isOSPOSIXCompatible() && key == Key.CTRL_J) {
                    return ENTER;
                } else if (!Config.isOSPOSIXCompatible() && key == Key.CTRL_M) {
                    if (input.length > position + 1 && input[position + 1] == Key.CTRL_J.getFirstValue())
                        return ENTER_2;
                    else
                        return ENTER;
                } else
                    return key;
            }
        }
        //need to do this in two steps since esc/windows_esc would be returned always
        if (Key.ESC.inputStartsWithKey(input, position))
            return Key.ESC;
        else if (Key.WINDOWS_ESC.inputStartsWithKey(input, position))
            return Key.WINDOWS_ESC;

        return null;
    }

    /**
     * Checks if the input array starts with this key's values.
     *
     * @param input the input array to check
     * @return true if the input starts with this key's values
     */
    public boolean inputStartsWithKey(int[] input) {
        if (keyValues.limit() > input.length)
            return false;
        for (int i = 0; i < keyValues.limit(); i++) {
            if (keyValues.get(i) != input[i])
                return false;
        }
        return true;
    }

    /**
     * Checks if the input array starts with this key's values at the given position.
     *
     * @param input the input array to check
     * @param position the starting position in the input array
     * @return true if the input starts with this key's values at the given position
     */
    public boolean inputStartsWithKey(int[] input, int position) {
        if (keyValues.limit() + position > input.length)
            return false;
        for (int i = 0; i < keyValues.limit(); i++) {
            if (keyValues.get(i) != input[i + position])
                return false;
        }
        return true;
    }

    /**
     * Checks if this key's values are contained within the input array.
     *
     * @param input the input array to search
     * @return true if this key's values are found within the input
     */
    public boolean containKey(int[] input) {
        for (int i = 0; i < input.length; i++) {
            if (input[i] == keyValues.get(0)) {
                if (keyValues.limit() == 1)
                    return true;
                else if ((i + keyValues.limit()) < input.length) {
                    int j = i;
                    for (int k : keyValues.array()) {
                        if (input[j] != k) {
                            return false;
                        }
                        j++;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if this key's values are equal to the given array.
     *
     * @param otherValues the values to compare
     * @return true if the values are equal
     */
    public boolean equalTo(int[] otherValues) {
        if (keyValues.limit() == otherValues.length) {
            for (int i = 0; i < keyValues.limit(); i++) {
                if (keyValues.get(i) != otherValues[i])
                    return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if this key's values are equal to the given KeyAction.
     *
     * @param key the KeyAction to compare
     * @return true if the values are equal
     */
    public boolean equalTo(KeyAction key) {
        if (keyValues.limit() == key.length()) {
            for (int i = 0; i < keyValues.limit(); i++)
                if (keyValues.get(i) != key.getCodePointAt(i))
                    return false;
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCodePointAt(int index) {
        return keyValues.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int length() {
        return keyValues.limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntBuffer buffer() {
        return keyValues;
    }
}
