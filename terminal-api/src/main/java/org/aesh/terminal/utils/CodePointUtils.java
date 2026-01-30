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

import java.util.regex.Pattern;

/**
 * Utility methods for working with code points.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public final class CodePointUtils {

    /**
     * The space character constant.
     */
    public static final char SPACE_CHAR = ' ';

    /**
     * The backslash character constant.
     */
    public static final char BACK_SLASH = '\\';
    private static final Pattern spacePattern = Pattern.compile("(?<!\\\\)\\s");

    private CodePointUtils() {
        // utility class
    }

    /**
     * Convert an array of code points to a String.
     *
     * @param input the code points
     * @return the resulting string
     */
    public static String fromCodePoints(int[] input) {
        return new String(input, 0, input.length);
    }

    /**
     * Convert a String to an array of code points.
     *
     * @param s the string
     * @return the code points
     */
    public static int[] toCodePoints(String s) {
        return s.codePoints().toArray();
    }

    /**
     * Replace spaces with escaped spaces in a word.
     *
     * @param word the word to process
     * @return the word with spaces escaped
     */
    public static String switchSpacesToEscapedSpacesInWord(String word) {
        return spacePattern.matcher(word).replaceAll("\\\\ ");
    }
}
