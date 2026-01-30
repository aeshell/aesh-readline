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
 * Enumeration of ANSI terminal colors with intensity and type options.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum Color {

    /** Black color (ANSI code 0). */
    BLACK(0),
    /** Red color (ANSI code 1). */
    RED(1),
    /** Green color (ANSI code 2). */
    GREEN(2),
    /** Yellow color (ANSI code 3). */
    YELLOW(3),
    /** Blue color (ANSI code 4). */
    BLUE(4),
    /** Magenta color (ANSI code 5). */
    MAGENTA(5),
    /** Cyan color (ANSI code 6). */
    CYAN(6),
    /** White color (ANSI code 7). */
    WHITE(7),
    /** Default terminal color (ANSI code 9). */
    DEFAULT(9);

    private final int value;

    Color(int value) {
        this.value = value;
    }

    /**
     * Returns the ANSI color code value.
     *
     * @return the ANSI color code value
     */
    public int getValue() {
        return value;
    }

    /**
     * Color intensity levels for ANSI terminal colors.
     */
    public enum Intensity {
        /** Normal intensity color. */
        NORMAL,
        /** Bright or high intensity color. */
        BRIGHT;

        /**
         * Returns the ANSI escape code prefix value based on intensity and color type.
         *
         * @param type the color type (foreground or background)
         * @return the ANSI escape code prefix value
         */
        public int getValue(Type type) {
            if (this == NORMAL) {
                if (type == Type.FOREGROUND)
                    return 3;
                else
                    return 4;
            } else {
                if (type == Type.FOREGROUND)
                    return 9;
                else
                    return 10;
            }
        }
    }

    /**
     * Specifies whether a color applies to foreground or background.
     */
    public enum Type {
        /** Foreground (text) color. */
        FOREGROUND, // 3
        /** Background color. */
        BACKGROUND // 4
    }
}
