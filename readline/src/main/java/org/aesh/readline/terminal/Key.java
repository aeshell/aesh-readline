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
package org.aesh.readline.terminal;

import org.aesh.readline.action.KeyAction;
import org.aesh.utils.InfoCmpHelper;
import org.aesh.utils.Config;
import org.aesh.util.Parser;

import java.nio.IntBuffer;

/**
 * ANSCII enum key chart
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public enum Key implements KeyAction {
    CTRL_AT(new int[]{0}),
    CTRL_A(new int[]{1}),
    CTRL_B(new int[]{2}),
    CTRL_C(new int[]{3}),
    CTRL_D(new int[]{4}),
    CTRL_E(new int[]{5}),
    CTRL_F(new int[]{6}),
    CTRL_G(new int[]{7}),
    CTRL_H(new int[]{8}),
    CTRL_I(new int[]{9}),
    CTRL_J(new int[]{10}),
    CTRL_K(new int[]{11}),
    CTRL_L(new int[]{12}),
    CTRL_M(new int[]{13}),
    CTRL_N(new int[]{14}),
    CTRL_O(new int[]{15}),
    CTRL_P(new int[]{16}),
    CTRL_Q(new int[]{17}),
    CTRL_R(new int[]{18}),
    CTRL_S(new int[]{19}),
    CTRL_T(new int[]{20}),
    CTRL_U(new int[]{21}),
    CTRL_V(new int[]{22}),
    CTRL_W(new int[]{23}),
    CTRL_X(new int[]{24}),
    CTRL_Y(new int[]{25}),
    CTRL_Z(new int[]{26}),
    ESC(new int[]{27}), //ctrl-[ and esc
    FILE_SEPARATOR(new int[]{28}), //ctrl-\
    GROUP_SEPARATOR(new int[]{29}), //ctrl-]
    RECORD_SEPARATOR(new int[]{30}), //ctrl-ctrl
    UNIT_SEPARATOR(new int[]{31}), //ctrl-_

    SPACE(new int[]{32}),
    EXCLAMATION(new int[]{33}), // !
    QUOTE(new int[]{34}), // "
    HASH(new int[]{35}), // #
    DOLLAR(new int[]{36}), // $
    PERCENT(new int[]{37}), // %
    AMPERSAND(new int[]{38}), // &
    APOSTROPHE(new int[]{39}), // '
    LEFT_PARANTHESIS(new int[]{40}), // (
    RIGHT_PARANTHESIS(new int[]{41}), // )
    STAR(new int[]{42}), // *
    PLUS(new int[]{43}), // +
    COMMA(new int[]{44}), // ,
    MINUS(new int[]{45}), // -
    PERIOD(new int[]{46}), // .
    SLASH(new int[]{47}), // /
    ZERO(new int[]{48}), // 0
    ONE(new int[]{49}), // 1
    TWO(new int[]{50}), // 2
    THREE(new int[]{51}), // 3
    FOUR(new int[]{52}), // 4
    FIVE(new int[]{53}), // 5
    SIX(new int[]{54}), // 6
    SEVEN(new int[]{55}), // 7
    EIGHT(new int[]{56}), // 8
    NINE(new int[]{57}), // 9
    COLON(new int[]{58}), // :
    SEMI_COLON(new int[]{59}), // ;
    LESS_THAN(new int[]{60}), // <
    EQUALS(new int[]{61}), // =
    GREATER_THAN(new int[]{62}), // >
    QUESTION_MARK(new int[]{63}), // ?
    AT(new int[]{64}), // @
    A(new int[]{65}),
    B(new int[]{66}),
    C(new int[]{67}),
    D(new int[]{68}),
    E(new int[]{69}),
    F(new int[]{70}),
    G(new int[]{71}),
    H(new int[]{72}),
    I(new int[]{73}),
    J(new int[]{74}),
    K(new int[]{75}),
    L(new int[]{76}),
    M(new int[]{77}),
    N(new int[]{78}),
    O(new int[]{79}),
    P(new int[]{80}),
    Q(new int[]{81}),
    R(new int[]{82}),
    S(new int[]{83}),
    T(new int[]{84}),
    U(new int[]{85}),
    V(new int[]{86}),
    W(new int[]{87}),
    X(new int[]{88}),
    Y(new int[]{89}),
    Z(new int[]{90}),
    LEFT_SQUARE_BRACKET(new int[]{91}), // [
    BACKSLASH(new int[]{92}), // \
    RIGHT_SQUARE_BRACKET(new int[]{93}), // ]
    HAT(new int[]{94}), // ^
    UNDERSCORE(new int[]{95}), // _
    GRAVE(new int[]{96}), // `
    a(new int[]{97}),
    b(new int[]{98}),
    c(new int[]{99}),
    d(new int[]{100}),
    e(new int[]{101}),
    f(new int[]{102}),
    g(new int[]{103}),
    h(new int[]{104}),
    i(new int[]{105}),
    j(new int[]{106}),
    k(new int[]{107}),
    l(new int[]{108}),
    m(new int[]{109}),
    n(new int[]{110}),
    o(new int[]{111}),
    p(new int[]{112}),
    q(new int[]{113}),
    r(new int[]{114}),
    s(new int[]{115}),
    t(new int[]{116}),
    u(new int[]{117}),
    v(new int[]{118}),
    w(new int[]{119}),
    x(new int[]{120}),
    y(new int[]{121}),
    z(new int[]{122}),
    LEFT_CURLY_BRACKET(new int[]{123}), // {
    VERTICAL_BAR(new int[]{124}), // |
    RIGHT_CURLY_BRACKET(new int[]{125}), // }
    TILDE(new int[]{126}), // ~

    BACKSPACE(Config.isOSPOSIXCompatible() ?
            new int[]{127} : new int[]{8}),

    WINDOWS_ESC(new int[]{224}), // just used to identify win special chars
    WINDOWS_ESC_2(new int[]{341}), // just used to identify win special chars
    //movement
    UP( new int[]{ESC.getFirstValue(),91,65}),
    DOWN( new int[]{ESC.getFirstValue(),91,66}),
    RIGHT( new int[]{ESC.getFirstValue(),91,67}),
    LEFT( new int[]{ESC.getFirstValue(),91,68}),

    UP_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcuu1", new int[]{27,79,65})),
    DOWN_2( InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcud1", new int[]{27,79,66})),
    RIGHT_2( InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("cuf1", new int[]{27,79,67})),
    LEFT_2( InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kcub1", new int[]{27,79,68})),

    //meta
    META_a(new int[]{ESC.getFirstValue(),a.getFirstValue()}),
    META_b(new int[]{ESC.getFirstValue(),b.getFirstValue()}),
    META_c(new int[]{ESC.getFirstValue(),c.getFirstValue()}),
    META_d(new int[]{ESC.getFirstValue(),d.getFirstValue()}),
    META_e(new int[]{ESC.getFirstValue(),e.getFirstValue()}),
    META_f(new int[]{ESC.getFirstValue(),f.getFirstValue()}),
    META_l(new int[]{ESC.getFirstValue(),l.getFirstValue()}),
    META_u(new int[]{ESC.getFirstValue(),u.getFirstValue()}),

    META_BACKSPACE(new int[]{ESC.getFirstValue(),BACKSPACE.getFirstValue()}),

    //div
    DELETE( InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kdch1", new int[]{27,91,51,126})),

    INSERT(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kich1", new int[]{27,91,50,126})),
    PGUP(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kpp", new int[]{27,91,53,126})),
    PGDOWN(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("knp", new int[]{27,91,54,126})),
    HOME(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("khome", new int[]{27,79,72})),
    HOME_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("home", new int[]{27,91,72})),
    END(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("kend", new int[]{27,79,70})),
    END_2(InfoCmpHelper.getCurrentTranslatedCapabilityAsInts("end", new int[]{27,91,70})),

    META_CTRL_J( new int[]{ESC.getFirstValue(),10}),
    META_CTRL_D(new int[]{ESC.getFirstValue(),4}),
    CTRL_X_CTRL_U(new int[]{CTRL_X.getFirstValue(),CTRL_U.getFirstValue()}),

    CTRL_LEFT(new int[] {ESC.getFirstValue(),91,49,59,53,68}),
    CTRL_RIGHT(new int[] {ESC.getFirstValue(),91,49,59,53,67}),
    CTRL_UP(new int[] {ESC.getFirstValue(),91,49,59,53,65}),
    CTRL_DOWN(new int[] {ESC.getFirstValue(),91,49,59,53,66}),

    ENTER(Config.isOSPOSIXCompatible() ?
            new int[]{10} : new int[]{13}),
    //needed to support stupid \r\n on windows...
    ENTER_2(Config.isOSPOSIXCompatible() ?
            new int[]{10} : new int[]{13,10});

    private final IntBuffer keyValues;

    Key(int[] keyValues) {
        this.keyValues = IntBuffer.allocate(keyValues.length);
        this.keyValues.put(keyValues);

    }

    /**
     * is of type a-z or A-Z
     */
    public boolean isCharacter() {
        return (keyValues.limit() == 1 &&
                ((keyValues.get(0) > 63 && keyValues.get(0) < 91) || (keyValues.get(0) > 96 && keyValues.get(0) < 123)));
    }

    /**
     * @return true if input is 0-9
     */
    public boolean isNumber() {
        return (keyValues.limit() == 1 && ((keyValues.get(0) > 47) && (keyValues.get(0) < 58)));
    }

    /**
     * @return true if input is a valid char
     */
    public boolean isPrintable() {
        return isPrintable(getKeyValues());
    }

    public static boolean isPrintable(int value) {
        if(Config.isOSPOSIXCompatible())
            return (((value > 31 && value < 127) || value > 127));
        else
            return (((value > 31 && value < 127) ||
                    (value > 127 &&
                            value != WINDOWS_ESC.getFirstValue() &&
                            value != WINDOWS_ESC_2.getFirstValue())));

    }

    public static boolean isPrintable(int[] keyValues) {
        return (keyValues.length == 1 && isPrintable(keyValues[0]));
    }

    public static boolean isPrintable(IntBuffer keyValues) {
        if(Config.isOSPOSIXCompatible())
            return (keyValues.limit() == 1 && ((keyValues.get(0) > 31 && keyValues.get(0) < 127) || keyValues.get(0) > 127));
        else
            return (keyValues.limit() == 1 && ((keyValues.get(0) > 31 && keyValues.get(0) < 127) ||
                    (keyValues.get(0) > 127 &&
                            keyValues.get(0) != WINDOWS_ESC.getFirstValue() &&
                            keyValues.get(0) != WINDOWS_ESC_2.getFirstValue())));
    }

    public char getAsChar() {
        return (char) keyValues.get(0);
    }

    public int[] getKeyValues() {
        return keyValues.array();
    }

    public String getKeyValuesAsString() {
        return Parser.fromCodePoints(keyValues.array());
    }

    public int getFirstValue() {
        return keyValues.get(0);
    }

    public static boolean startsWithEscape(int[] input) {
        return ((Config.isOSPOSIXCompatible() && input[0] == Key.ESC.getFirstValue()) ||
                (!Config.isOSPOSIXCompatible() && input[0] == Key.WINDOWS_ESC.getFirstValue()));
    }

    public static Key getKey(int[] otherValues) {
        for(Key key : Key.values()) {
            if(key.equalTo(otherValues))
                return key;
        }
        return null;
    }

    public static Key findStartKey(int[] input) {
        for(Key key : values()) {
            if(key != Key.ESC && key != Key.WINDOWS_ESC &&
                    key.inputStartsWithKey(input)) {
                if(Config.isOSPOSIXCompatible() && key == Key.CTRL_J) {
                   return ENTER;
                }
                else if(!Config.isOSPOSIXCompatible() && key == Key.CTRL_M) {
                    if(input.length > 1 && input[1] == Key.CTRL_J.getFirstValue())
                        return ENTER_2;
                    else
                        return ENTER;
                }
                else
                    return key;
            }
        }
        //need to do this in two steps since esc/windows_esc would be returned always
        if(Key.ESC.inputStartsWithKey(input))
            return Key.ESC;
        else if(Key.WINDOWS_ESC.inputStartsWithKey(input))
            return Key.WINDOWS_ESC;

        return null;
    }

    public static Key findStartKey(int[] input, int position) {
        for(Key key : values()) {
            if(key != Key.ESC && key != Key.WINDOWS_ESC &&
                    key.inputStartsWithKey(input, position)) {
                if(Config.isOSPOSIXCompatible() && key == Key.CTRL_J) {
                   return ENTER;
                }
                else if(!Config.isOSPOSIXCompatible() && key == Key.CTRL_M) {
                    if(input.length > position + 1 && input[position+1] == Key.CTRL_J.getFirstValue())
                        return ENTER_2;
                    else
                        return ENTER;
                }
                else
                    return key;
            }
        }
        //need to do this in two steps since esc/windows_esc would be returned always
        if(Key.ESC.inputStartsWithKey(input, position))
            return Key.ESC;
        else if(Key.WINDOWS_ESC.inputStartsWithKey(input, position))
            return Key.WINDOWS_ESC;

        return null;
    }

    public boolean inputStartsWithKey(int[] input) {
        if(keyValues.limit() > input.length)
            return false;
        for(int i=0; i < keyValues.limit(); i++) {
            if(keyValues.get(i) != input[i])
                return false;
        }
        return true;
    }

    public boolean inputStartsWithKey(int[] input, int position) {
        if(keyValues.limit()+position > input.length)
            return false;
        for(int i=0; i < keyValues.limit(); i++) {
            if(keyValues.get(i) != input[i+position])
                return false;
        }
        return true;
    }

    public boolean containKey(int[] input) {
        for(int i=0; i < input.length; i++) {
            if(input[i] == keyValues.get(0)) {
                if(keyValues.limit() == 1)
                    return true;
                else if((i + keyValues.limit()) < input.length) {
                    int j = i;
                    for(int k : keyValues.array()) {
                        if(input[j] != k) {
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

    public boolean equalTo(int[] otherValues) {
        if(keyValues.limit() == otherValues.length) {
            for(int i=0; i < keyValues.limit(); i++) {
                if(keyValues.get(i) != otherValues[i])
                    return false;
            }
            return true;
        }
        return false;
    }

    public boolean equalTo(KeyAction key) {
        if(keyValues.limit() == key.length()) {
            for(int i = 0; i < keyValues.limit();i++)
                if(keyValues.get(i) != key.getCodePointAt(i))
                    return false;
            return true;
        }
        return false;
    }

    @Override
    public int getCodePointAt(int index) {
        return keyValues.get(index);
    }

    @Override
    public int length() {
        return keyValues.limit();
    }

    @Override
    public IntBuffer buffer() {
        return keyValues;
    }
}
