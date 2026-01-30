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
package org.aesh.terminal.formatting;

/**
 * Define what kind of character type to display
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum CharacterType {
    /** Bold or increased intensity text. */
    BOLD(1),
    /** Faint or decreased intensity text. */
    FAINT(2),
    /** Italic text style. */
    ITALIC(3),
    /** Underlined text. */
    UNDERLINE(4),
    /** Blinking text. */
    BLINK(5),
    /** Inverted foreground and background colors. */
    INVERT(7),
    /** Hidden or concealed text. */
    CONCEAL(8),
    /** Crossed-out or strikethrough text. */
    CROSSED_OUT(9);

    private final int value;

    CharacterType(int c) {
        this.value = c;
    }

    /**
     * Returns the ANSI escape code value for this character type.
     *
     * @return the ANSI escape code value
     */
    public int getValue() {
        return value;
    }

}
