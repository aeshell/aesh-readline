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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.aesh.terminal.formatting.TerminalString;

/**
 * String/Parser util methods
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public final class Parser {

    /**
     * Private constructor to prevent instantiation.
     */
    private Parser() {
    }

    private static final String spaceEscapedMatcher = "\\ ";
    /** Space string constant. */
    public static final String SPACE = " ";
    /** Space character constant. */
    public static final char SPACE_CHAR = CodePointUtils.SPACE_CHAR;
    /** Backslash character constant. */
    public static final char BACK_SLASH = CodePointUtils.BACK_SLASH;
    /** Single quote character constant. */
    public static final char SINGLE_QUOTE = '\'';
    /** Double quote character constant. */
    public static final char DOUBLE_QUOTE = '\"';
    /** Dollar sign character constant. */
    public static final char DOLLAR = '$';
    private static final Pattern spaceEscapedPattern = Pattern.compile("\\\\ ");
    private static final Pattern ansiPattern = Pattern
            .compile("\\u001B(?:\\[[\\?]?[0-9;]*[a-zA-Z]?|\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\))");
    // command text which starts with '#' is a comment
    private static final Pattern commentPattern = Pattern.compile("^(\\s*)(#)(.*)");

    /**
     * Format completions so that they look similar to GNU Readline
     *
     * @param displayList to format
     * @param termHeight max height
     * @param termWidth max width
     * @return formatted string to be outputted
     */
    public static String formatDisplayList(String[] displayList, int termHeight, int termWidth) {
        return formatDisplayList(Arrays.asList(displayList), termHeight, termWidth);
    }

    /**
     * Format completions so that they look similar to GNU Readline
     *
     * @param displayList to format
     * @param termHeight max height
     * @param termWidth max width
     * @return formatted string to be outputted
     */
    public static String formatDisplayList(List<String> displayList, int termHeight, int termWidth) {
        if (displayList == null || displayList.isEmpty()) {
            return "";
        }
        // make sure that termWidth is > 0
        if (termWidth < 1) {
            termWidth = 80; // setting it to default
        }
        int maxLength = 0;
        for (String completion : displayList) {
            if (completion.length() > termWidth) {
                maxLength = termWidth;
                break;
            }
            if (completion.length() > maxLength) {
                maxLength = completion.length();
            }
        }

        if (maxLength + 2 <= termWidth) {
            maxLength = maxLength + 2; // adding two spaces for better readability
        }
        int[] layout = calculateColumnLayout(displayList.size(), maxLength, termWidth);
        int numColumns = layout[0];
        int numRows = layout[1];

        // create the completion listing
        StringBuilder completionOutput = new StringBuilder();
        for (int i = 0; i < numRows; i++) {
            for (int c = 0; c < numColumns; c++) {
                int fetch = i + (c * numRows);
                if (fetch < displayList.size()) {
                    completionOutput.append(padRight(maxLength, displayList.get(i + (c * numRows))));
                } else {
                    break;
                }
            }
            completionOutput.append(Config.getLineSeparator());
        }

        return completionOutput.toString();
    }

    /**
     * Format completions so that they look similar to GNU Readline
     *
     * @param displayList to format
     * @param termHeight max height
     * @param termWidth max width
     * @return formatted string to be outputted
     */
    public static String formatDisplayListTerminalString(List<TerminalString> displayList, int termHeight, int termWidth) {
        if (displayList == null || displayList.isEmpty())
            return "";
        // make sure that termWidth is > 0
        if (termWidth < 1)
            termWidth = 80; // setting it to default

        int maxLength = 0;
        for (TerminalString completion : displayList)
            if (completion.getCharacters().length() > maxLength)
                maxLength = completion.getCharacters().length();

        maxLength = maxLength + 2; // adding two spaces for better readability
        int[] layout = calculateColumnLayout(displayList.size(), maxLength, termWidth);
        int numColumns = layout[0];
        int numRows = layout[1];

        StringBuilder completionOutput = new StringBuilder();
        if (numRows > 1) {
            // create the completion listing
            for (int i = 0; i < numRows; i++) {
                for (int c = 0; c < numColumns; c++) {
                    int fetch = i + (c * numRows);
                    if (fetch < displayList.size()) {
                        if (c == numColumns - 1) { // No need to pad the right most column
                            completionOutput.append(displayList.get(i + (c * numRows)).toString());
                        } else {
                            completionOutput.append(padRight(maxLength
                                    + displayList.get(i + (c * numRows)).getANSILength(),
                                    displayList.get(i + (c * numRows)).toString()));
                        }
                    } else {
                        break;
                    }
                }
                completionOutput.append(Config.getLineSeparator());
            }
        } else {
            for (TerminalString ts : displayList) {
                completionOutput.append(ts.toString()).append("  ");
            }
            completionOutput.append(Config.getLineSeparator());
        }

        return completionOutput.toString();
    }

    /**
     * Format output to columns with flexible sizes and no redundant space between them
     *
     * @param displayList to format
     * @param termWidth max width
     * @return formatted string to be outputted
     */
    public static String formatDisplayCompactListTerminalString(List<TerminalString> displayList, int termWidth) {
        if (displayList == null || displayList.isEmpty())
            return "";
        // make sure that termWidth is > 0
        if (termWidth < 1)
            termWidth = 80; // setting it to default

        int numRows = 1;

        // increase numRows and check in loop if it's possible to format strings to columns
        while (!canDisplayColumns(displayList, numRows, termWidth) && numRows < displayList.size()) {
            numRows++;
        }

        int numColumns = displayList.size() / numRows;
        if (displayList.size() % numRows > 0) {
            numColumns++;
        }

        int[] columnsSizes = calculateColumnSizes(displayList, numColumns, numRows, termWidth);

        StringBuilder stringOutput = new StringBuilder();

        for (int i = 0; i < numRows; i++) {
            for (int c = 0; c < numColumns; c++) {
                int fetch = i + (c * numRows);
                int nextFetch = i + ((c + 1) * numRows);

                if (fetch < displayList.size()) {
                    // don't need to format last column of row = nextFetch doesn't exit
                    if (nextFetch < displayList.size()) {
                        stringOutput.append(padRight(columnsSizes[c] + displayList.get(i + (c * numRows)).getANSILength(),
                                displayList.get(i + (c * numRows)).toString()));
                    } else {
                        stringOutput.append(displayList.get(i + (c * numRows)).toString());
                    }
                } else {
                    break;
                }
            }
            stringOutput.append(Config.getLineSeparator());
        }

        return stringOutput.toString();
    }

    /**
     * Decides if it's possible to format provided Strings into calculated number of columns while the output will not exceed
     * terminal width
     *
     * @param displayList
     * @param numRows
     * @param terminalWidth
     * @return true if it's possible to format strings to columns and false otherwise.
     */
    private static boolean canDisplayColumns(List<TerminalString> displayList, int numRows, int terminalWidth) {

        int numColumns = displayList.size() / numRows;
        if (displayList.size() % numRows > 0) {
            numColumns++;
        }

        int[] columnSizes = calculateColumnSizes(displayList, numColumns, numRows, terminalWidth);

        int totalSize = 0;
        for (int columnSize : columnSizes) {
            totalSize += columnSize;
        }
        return totalSize <= terminalWidth;
    }

    private static int[] calculateColumnSizes(List<TerminalString> displayList, int numColumns, int numRows, int termWidth) {
        int[] columnSizes = new int[numColumns];

        for (int i = 0; i < displayList.size(); i++) {
            int columnIndex = (i / numRows) % numColumns;
            int stringSize = displayList.get(i).getCharacters().length() + 2;

            if (columnSizes[columnIndex] < stringSize && stringSize <= termWidth) {
                columnSizes[columnIndex] = stringSize;
            }
        }

        return columnSizes;
    }

    /**
     * Calculate column layout for a display list.
     *
     * @param itemCount the number of items to display
     * @param maxItemLength the maximum item length (including padding)
     * @param termWidth the terminal width
     * @return array of {numColumns, numRows}
     */
    private static int[] calculateColumnLayout(int itemCount, int maxItemLength, int termWidth) {
        int numColumns = termWidth / maxItemLength;
        if (numColumns > itemCount) {
            numColumns = itemCount;
        }
        if (numColumns < 1) {
            numColumns = 1;
        }
        int numRows = itemCount / numColumns;
        if (numRows * numColumns < itemCount) {
            numRows++;
        }
        return new int[] { numColumns, numRows };
    }

    /**
     * Pad a string to the right with spaces.
     *
     * @param n the total width
     * @param s the string to pad
     * @return the padded string
     */
    public static String padRight(int n, String s) {
        return String.format("%1$-" + n + "s", s);
    }

    /**
     * Pad a string to the left with spaces.
     *
     * @param n the total width
     * @param s the string to pad
     * @return the padded string
     */
    public static String padLeft(int n, String s) {
        return String.format("%1$" + n + "s", s);
    }

    /**
     * Split a string by size while keeping words intact.
     *
     * @param words the string to split
     * @param size the maximum line size
     * @return list of split strings
     */
    public static List<String> splitBySizeKeepWords(String words, int size) {
        List<String> out = new ArrayList<>();
        if (words.length() <= size) {
            out.add(words);
        } else {
            while (words.length() > size) {
                int i = words.lastIndexOf(' ', size);
                if (i > 0) {
                    out.add(words.substring(0, i));
                    words = words.substring(i + 1);
                } else {
                    out.add(words);
                    break;
                }
            }
            if (!words.isEmpty())
                out.add(words);
        }
        return out;
    }

    /**
     * Remove leading dashes from an option name.
     *
     * @param word the option name to trim
     * @return the option name without leading dashes
     */
    public static String trimOptionName(String word) {
        if (word.startsWith("--"))
            return word.substring(2);
        else if (word.startsWith("-"))
            return word.substring(1);
        else
            return word;
    }

    /**
     * Check if a word ends with a non-escaped space.
     *
     * @param word the word to check
     * @return true if the word ends with a space that is not escaped
     */
    public static boolean findIfWordEndWithSpace(String word) {
        return !word.isEmpty() && word.endsWith(" ") && !word.endsWith("\\ ");
    }

    /**
     * Return the biggest common startsWith string
     *
     * @param completionList list to compare
     * @return biggest common startsWith string
     */
    public static String findStartsWith(List<String> completionList) {
        StringBuilder builder = new StringBuilder();
        for (String completion : completionList)
            while (builder.length() < completion.length() &&
                    startsWith(completion.substring(0, builder.length() + 1), completionList))
                builder.append(completion.charAt(builder.length()));

        return builder.toString();
    }

    private static boolean startsWith(String criteria, List<String> completionList) {
        for (String completion : completionList)
            if (!completion.startsWith(criteria))
                return false;

        return true;
    }

    /**
     * Return the biggest common startsWith string
     *
     * @param completionList list to compare
     * @return biggest common startsWith string
     */
    public static String findStartsWithTerminalString(List<TerminalString> completionList) {
        StringBuilder builder = new StringBuilder();
        for (TerminalString completion : completionList)
            while (builder.length() < completion.getCharacters().length() &&
                    startsWithTerminalString(completion.getCharacters().substring(0, builder.length() + 1), completionList))
                builder.append(completion.getCharacters().charAt(builder.length()));

        return builder.toString();
    }

    private static boolean startsWithTerminalString(String criteria, List<TerminalString> completionList) {
        for (TerminalString completion : completionList)
            if (!completion.getCharacters().startsWith(criteria))
                return false;

        return true;
    }

    /**
     * Find the word closest to the cursor position.
     *
     * @param text the text to search in
     * @param cursor the cursor position
     * @return the word closest to the cursor
     */
    public static String findWordClosestToCursor(String text, int cursor) {
        boolean startOutsideText = false;
        if (cursor >= text.length()) {
            cursor = text.length() - 1;
            startOutsideText = true;
        }
        if (cursor < 0 || text.trim().isEmpty())
            return "";

        boolean foundBackslash = false;

        if (text.contains(SPACE)) {
            int start, end;
            if (text.charAt(cursor) == SPACE_CHAR) {
                if (startOutsideText)
                    return "";
                if (cursor > 0) {
                    if (text.charAt(cursor - 1) == SPACE_CHAR)
                        return "";
                    else
                        cursor--;
                }
            }

            boolean space = false;
            for (start = cursor; start > 0; start--) {
                if (space) {
                    if (text.charAt(start) == BACK_SLASH) {
                        space = false;
                        foundBackslash = true;
                    } else {
                        start += 2;
                        break;
                    }
                }
                if (Character.isSpaceChar(text.charAt(start)))
                    space = true;
            }

            boolean back = false;
            for (end = cursor; end < text.length(); end++) {
                if (text.charAt(end) == BACK_SLASH) {
                    back = true;
                    foundBackslash = true;
                } else if (back) {
                    if (Character.isSpaceChar(text.charAt(end))) {
                        back = false;
                    }
                } else if (Character.isSpaceChar(text.charAt(end))) {
                    break;
                }
            }
            if (foundBackslash)
                return switchEscapedSpacesToSpacesInWord(text.substring(start, end));
            else
                return text.substring(start, end);
        } else {
            return text.trim();
        }
    }

    /**
     * Return the word "connected" to cursor, the word ends at cursor position. Note that cursor position starts at 0
     *
     * @param text to parse
     * @param cursor position
     * @return word connected to cursor
     */
    public static String findCurrentWordFromCursor(String text, int cursor) {
        if (text.length() <= cursor + 1) {
            // return last word
            if (text.contains(SPACE)) {
                if (doWordContainEscapedSpace(text)) {
                    if (doWordContainOnlyEscapedSpace(text))
                        return switchEscapedSpacesToSpacesInWord(text);
                    else {
                        return switchEscapedSpacesToSpacesInWord(findEscapedSpaceWordCloseToEnd(text));
                    }
                } else {
                    if (text.lastIndexOf(SPACE) >= cursor) // cant use lastIndexOf
                        return text.substring(text.substring(0, cursor).lastIndexOf(SPACE)).trim();
                    else
                        return text.substring(text.lastIndexOf(SPACE)).trim();
                }
            } else
                return text.trim();
        } else {
            String rest;
            if (text.length() > cursor + 1)
                rest = text.substring(0, cursor + 1);
            else
                rest = text;

            if (doWordContainOnlyEscapedSpace(rest)) {
                if (cursor > 1 &&
                        text.charAt(cursor) == SPACE_CHAR && text.charAt(cursor - 1) == SPACE_CHAR)
                    return "";
                else
                    return switchEscapedSpacesToSpacesInWord(rest);
            } else {
                if (cursor > 1 &&
                        text.charAt(cursor) == SPACE_CHAR && text.charAt(cursor - 1) == SPACE_CHAR)
                    return "";
                // only if it contains a ' ' and its not at the end of the string
                if (rest.trim().contains(SPACE))
                    return rest.substring(rest.trim().lastIndexOf(" ")).trim();
                else
                    return rest.trim();
            }
        }
    }

    /**
     * Search backwards for a non-escaped space and only return work containing non-escaped space
     *
     * @param text text
     * @return text with only non-escaped space
     */
    public static String findEscapedSpaceWordCloseToEnd(String text) {
        int index;
        String originalText = text;
        while ((index = text.lastIndexOf(SPACE)) > -1) {
            if (index > 0 && text.charAt(index - 1) == BACK_SLASH) {
                text = text.substring(0, index - 1);
            } else
                return originalText.substring(index + 1);
        }
        return originalText;
    }

    /**
     * Check if a string contain open quotes. Escaped quotes does not count.
     *
     * @param text text
     * @param flags flags, <code>1</code> to ignore double quotes, <code>2</code> to ignore single
     *        quotes. Other values will check for both.
     * @return true if it contains open quotes, else false
     */
    public static boolean doesStringContainOpenQuote(String text, int flags) {
        boolean ignoreDoubleQuote = flags == 1;
        boolean ignoreSingleQuote = flags == 2;
        boolean doubleQuote = false;
        boolean singleQuote = false;
        boolean escapedByBackSlash = false;
        // do not parse comment
        if (commentPattern.matcher(text).find())
            return false;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == BACK_SLASH || escapedByBackSlash) {
                escapedByBackSlash = !escapedByBackSlash;
                continue;
            }
            if (text.charAt(i) == SINGLE_QUOTE && !ignoreSingleQuote) {
                if (!doubleQuote)
                    singleQuote = !singleQuote;
            } else if (text.charAt(i) == DOUBLE_QUOTE && !ignoreDoubleQuote) {
                if (!singleQuote)
                    doubleQuote = !doubleQuote;
            }
        }
        return doubleQuote || singleQuote;
    }

    /**
     * Check if a string contains open quotes, checking for both single and double quotes.
     *
     * @param text the text to check
     * @return true if the text contains unclosed quotes
     */
    public static boolean doesStringContainOpenQuote(String text) {
        return doesStringContainOpenQuote(text, -1);
    }

    /**
     * Check if a word contains only escaped spaces (no regular spaces).
     *
     * @param word the word to check
     * @return true if all spaces in the word are escaped
     */
    public static boolean doWordContainOnlyEscapedSpace(String word) {
        return (findAllOccurrences(word, spaceEscapedMatcher) == findAllOccurrences(word, SPACE));
    }

    /**
     * Check if a word contains any escaped spaces.
     *
     * @param word the word to check
     * @return true if the word contains at least one escaped space
     */
    public static boolean doWordContainEscapedSpace(String word) {
        return spaceEscapedPattern.matcher(word).find();
    }

    /**
     * find number of spaces in the given word. escaped spaces are not counted
     *
     * @param word to check
     * @return number of spaces
     */
    public static int findNumberOfSpacesInWord(String word) {
        int count = 0;
        for (int i = 0; i < word.length(); i++)
            if (word.charAt(i) == SPACE_CHAR && (i == 0 || word.charAt(i - 1) != BACK_SLASH))
                count++;

        return count;
    }

    /**
     * Find the number of occurrences of a pattern in a word.
     *
     * @param word the word to search in
     * @param pattern the pattern to search for
     * @return the number of occurrences
     */
    public static int findAllOccurrences(String word, String pattern) {
        int count = 0;
        while (word.contains(pattern)) {
            count++;
            word = word.substring(word.indexOf(pattern) + pattern.length());
        }
        return count;
    }

    /**
     * Convert escaped spaces to regular spaces in all strings in a list.
     *
     * @param list the list of strings to convert
     * @return a new list with escaped spaces converted
     */
    public static List<String> switchEscapedSpacesToSpacesInList(List<String> list) {
        List<String> newList = new ArrayList<>(list.size());
        for (String s : list)
            newList.add(switchEscapedSpacesToSpacesInWord(s));
        return newList;
    }

    /**
     * Convert escaped spaces to regular spaces in all terminal strings in a list.
     *
     * @param list the list of terminal strings to modify
     */
    public static void switchEscapedSpacesToSpacesInTerminalStringList(List<TerminalString> list) {
        for (TerminalString ts : list)
            ts.setCharacters(switchEscapedSpacesToSpacesInWord(ts.getCharacters()));
    }

    /**
     * Convert regular spaces to escaped spaces in a word.
     *
     * @param word the word to convert
     * @return the word with spaces escaped
     */
    public static String switchSpacesToEscapedSpacesInWord(String word) {
        return CodePointUtils.switchSpacesToEscapedSpacesInWord(word);
    }

    /**
     * Convert escaped spaces to regular spaces in a word.
     *
     * @param word the word to convert
     * @return the word with escaped spaces converted to regular spaces
     */
    public static String switchEscapedSpacesToSpacesInWord(String word) {
        return spaceEscapedPattern.matcher(word).replaceAll(SPACE);
    }

    /**
     * Similar to String.trim(), but do not remove spaces that are escaped
     *
     * @param buffer input
     * @return trimmed buffer
     */
    public static String trim(String buffer) {
        // remove spaces in front
        int count = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) == SPACE_CHAR)
                count++;
            else
                break;
        }
        if (count > 0)
            buffer = buffer.substring(count);

        // remove spaces in the end
        count = buffer.length();
        for (int i = buffer.length() - 1; i > 0; i--) {
            if (buffer.charAt(i) == SPACE_CHAR && buffer.charAt(i - 1) != BACK_SLASH)
                count--;
            else
                break;
        }
        if (count != buffer.length())
            buffer = buffer.substring(0, count);

        return buffer;
    }

    /**
     * Only trim space in front of the word
     *
     * @param buffer input
     * @return trimmed buffer
     */
    public static String trimInFront(String buffer) {
        // remove spaces in front
        int count = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) == SPACE_CHAR)
                count++;
            else
                break;
        }
        if (count > 0)
            return buffer.substring(count);
        else
            return buffer;
    }

    /**
     * If string contain space, return the text before the first space. Spaces in the beginning and end is removed with
     * Parser.trim(..)
     *
     * @param buffer input
     * @return first word
     */
    public static String findFirstWord(String buffer) {
        if (buffer.indexOf(SPACE_CHAR) < 0)
            return buffer;
        else {
            buffer = Parser.trim(buffer);
            int index = buffer.indexOf(SPACE_CHAR);
            if (index > 0)
                return buffer.substring(0, index);
            else
                return buffer;
        }
    }

    /**
     * Check if a string contains a non-escaped dollar sign.
     *
     * @param buffer the string to check
     * @return true if it contains a non-escaped dollar sign
     */
    public static boolean containsNonEscapedDollar(String buffer) {
        int startIndex = 0;
        while ((startIndex = buffer.indexOf(DOLLAR, startIndex)) > -1) {
            if (startIndex == 0)
                return true;
            else if (buffer.charAt(startIndex - 1) != BACK_SLASH)
                return true;
            else
                startIndex++;
        }
        return false;
    }

    /**
     * Check if a string contains a non-escaped space.
     *
     * @param buffer the string to check
     * @return true if it contains a non-escaped space
     */
    public static boolean containsNonEscapedSpace(String buffer) {
        int startIndex = 0;
        while ((startIndex = buffer.indexOf(SPACE_CHAR, startIndex)) > -1) {
            if (startIndex == 0)
                return true;
            else if (buffer.charAt(startIndex - 1) != BACK_SLASH)
                return true;
            else
                startIndex++;
        }
        return false;
    }

    /**
     * Remove all ANSI escape codes from a string.
     *
     * @param text the text containing ANSI codes
     * @return the text with ANSI codes removed
     */
    public static String stripAwayAnsiCodes(String text) {
        return ansiPattern.matcher(text).replaceAll("");
    }

    /**
     * Convert a string to an array of code points.
     * Uses direct iteration instead of Stream API for better performance.
     *
     * @param s the string to convert
     * @return array of code points
     */
    public static int[] toCodePoints(String s) {
        if (s == null || s.isEmpty()) {
            return new int[0];
        }
        return CodePointUtils.toCodePoints(s);
    }

    /**
     * Convert an array of code points to a string.
     *
     * @param input the code points to convert
     * @return the resulting string
     */
    public static String fromCodePoints(int[] input) {
        return CodePointUtils.fromCodePoints(input);
    }

    /**
     * Check if an array contains only space characters (code point 32).
     *
     * @param input the array to check
     * @return true if the array is empty or contains only spaces
     */
    public static boolean isTrimmedArrayEmpty(int[] input) {
        for (int i : input) {
            if (i != 32)
                return false;
        }
        return true;
    }

    /**
     * Find the index of a target array within a source array.
     *
     * @param source the source array to search in
     * @param target the target array to find
     * @return the index of the target in source, or -1 if not found
     */
    public static int arrayIndexOf(int[] source, int[] target) {
        return arrayIndexOf(source, 0, source.length, target, 0, target.length, 0);

    }

    /**
     * Find the index of a target array within a source array with offsets.
     *
     * @param source the source array to search in
     * @param sourceOffset the starting offset in the source array
     * @param sourceCount the number of elements to search in source
     * @param target the target array to find
     * @param targetOffset the starting offset in the target array
     * @param targetCount the number of elements to match from target
     * @param fromIndex the index to start searching from
     * @return the index of the target in source, or -1 if not found
     */
    public static int arrayIndexOf(int[] source, int sourceOffset, int sourceCount,
            int[] target, int targetOffset, int targetCount, int fromIndex) {
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        int first = target[targetOffset];
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (source[i] != first) {
                while (++i <= max && source[i] != first)
                    ;
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && source[j] == target[k]; j++, k++)
                    ;

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
    }

    /**
     * Check if a source array contains a target array.
     *
     * @param source the source array to search in
     * @param target the target array to find
     * @return true if source contains target
     */
    public static boolean arrayContains(int[] source, int[] target) {
        return arrayIndexOf(source, target) > -1;

    }
}
