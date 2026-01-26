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

import java.util.Arrays;

import org.aesh.terminal.tty.Point;

/**
 * Utility class to provide ANSI codes for different operations
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ANSI {

    private static final int TAB = 4;

    /** ANSI escape sequence start. */
    public static final String START = "\u001B[";
    /** ANSI escape code for black foreground text. */
    public static final String BLACK_TEXT = "\u001B[0;30m";
    /** ANSI escape code for red foreground text. */
    public static final String RED_TEXT = "\u001B[0;31m";
    /** ANSI escape code for green foreground text. */
    public static final String GREEN_TEXT = "\u001B[0;32m";
    /** ANSI escape code for yellow foreground text. */
    public static final String YELLOW_TEXT = "\u001B[0;33m";
    /** ANSI escape code for blue foreground text. */
    public static final String BLUE_TEXT = "\u001B[0;34m";
    /** ANSI escape code for magenta foreground text. */
    public static final String MAGENTA_TEXT = "\u001B[0;35m";
    /** ANSI escape code for cyan foreground text. */
    public static final String CYAN_TEXT = "\u001B[0;36m";
    /** ANSI escape code for white foreground text. */
    public static final String WHITE_TEXT = "\u001B[0;37m";
    /** ANSI escape code for default foreground text color. */
    public static final String DEFAULT_TEXT = "\u001B[0;39m";

    /** ANSI escape code for black background. */
    public static final String BLACK_BG = "\u001B[0;40m";
    /** ANSI escape code for red background. */
    public static final String RED_BG = "\u001B[0;41m";
    /** ANSI escape code for green background. */
    public static final String GREEN_BG = "\u001B[0;42m";
    /** ANSI escape code for yellow background. */
    public static final String YELLOW_BG = "\u001B[0;43m";
    /** ANSI escape code for blue background. */
    public static final String BLUE_BG = "\u001B[0;44m";
    /** ANSI escape code for magenta background. */
    public static final String MAGENTA_BG = "\u001B[0;45m";
    /** ANSI escape code for cyan background. */
    public static final String CYAN_BG = "\u001B[0;46m";
    /** ANSI escape code for white background. */
    public static final String WHITE_BG = "\u001B[0;47m";
    /** ANSI escape code for default background color. */
    public static final String DEFAULT_BG = "\u001B[0;49m";
    /** ANSI escape code to switch to alternate screen buffer. */
    public static final String ALTERNATE_BUFFER = InfoCmpHelper.getCurrentTranslatedCapability("smcup", "\u001B[?1049h");
    /** ANSI escape code to switch back to main screen buffer. */
    public static final String MAIN_BUFFER = InfoCmpHelper.getCurrentTranslatedCapability("rmcup", "\u001B[?1049l");
    /** ANSI escape code to invert foreground and background colors. */
    public static final String INVERT_BACKGROUND = InfoCmpHelper.getCurrentTranslatedCapability("smso", "\u001B[7m");
    /** ANSI escape code to restore normal foreground and background colors. */
    public static final String NORMAL_BACKGROUND = InfoCmpHelper.getCurrentTranslatedCapability("rmso", "\u001B[27m");
    /** ANSI escape code to reset all text attributes to default. */
    public static final String RESET = "\u001B[0m";
    /** ANSI escape code to enable bold text. */
    public static final String BOLD = InfoCmpHelper.getCurrentTranslatedCapability("bold", "\u001B[0;1m");
    /** ANSI escape code to disable bold text. */
    public static final String BOLD_OFF = "\u001B[0;22m";
    /** ANSI escape code to enable underlined text. */
    public static final String UNDERLINE = InfoCmpHelper.getCurrentTranslatedCapability("smul", "\u001B[0;4m");
    /** ANSI escape code to disable underlined text. */
    public static final String UNDERLINE_OFF = InfoCmpHelper.getCurrentTranslatedCapability("rmul", "\u001B[0;24m");
    /** ANSI escape code to enable blinking text. */
    public static final String BLINK = InfoCmpHelper.getCurrentTranslatedCapability("blink", "\u001B[5m");
    /** ANSI escape code to disable blinking text. */
    public static final String BLINK_OFF = "\u001B[25m";
    /** ANSI escape sequence to move cursor to start of line. */
    public static final int[] CURSOR_START = new int[] { 27, '[', 'G' };
    /** ANSI escape sequence to erase the entire current line. */
    public static final int[] ERASE_WHOLE_LINE = new int[] { 27, '[', '2', 'K' };
    /** ANSI escape code to query cursor row position. */
    public static final String CURSOR_ROW = "\u001B[6n";
    /** ANSI escape sequence to clear the entire screen. */
    public static final int[] CLEAR_SCREEN = InfoCmpHelper.getCurrentTranslatedCapability("clear", "\u001B[2J").codePoints()
            .toArray();
    /** ANSI escape code to save current cursor position. */
    public static final String CURSOR_SAVE = InfoCmpHelper.getCurrentTranslatedCapability("sc", "\u001B[s");
    /** ANSI escape code to restore previously saved cursor position. */
    public static final String CURSOR_RESTORE = InfoCmpHelper.getCurrentTranslatedCapability("rc", "\u001B[u");
    /** ANSI escape code to hide the cursor. */
    public static final String CURSOR_HIDE = "\u001B[?25l";
    /** ANSI escape code to show the cursor. */
    public static final String CURSOR_SHOW = "\u001B[?25h";
    /** ANSI escape sequence to erase from cursor to end of line. */
    public static final int[] ERASE_LINE_FROM_CURSOR = new int[] { 27, '[', 'K' };
    /** ANSI escape sequence to move cursor up one line. */
    public static final int[] MOVE_LINE_UP = new int[] { 27, '[', '1', 'A' };
    /** ANSI escape sequence to move cursor down one line. */
    public static final int[] MOVE_LINE_DOWN = new int[] { 27, '[', '1', 'B' };

    /** ANSI escape code to enable light (reverse video) background mode. */
    public static final String LIGHT_BG = "\u001B[?5h";
    /** ANSI escape code to enable dark (normal) background mode. */
    public static final String DARK_BG = "\u001B[?5l";

    private ANSI() {
    }

    /**
     * Return a ansified string based on param
     *
     * @param out string
     * @return ansified string
     */
    public static int[] printAnsi(String out) {
        return printAnsi(out.toCharArray());
    }

    /**
     * Return a ansified string based on param
     *
     * @param out what will be ansified
     * @return ansified string
     */
    public static int[] printAnsi(char... out) {
        int[] ansi = new int[out.length + 2];
        ansi[0] = 27;
        ansi[1] = '[';
        int counter = 0;
        for (char anOut : out) {
            if (anOut == '\t') {
                Arrays.fill(ansi, counter + 2, counter + 2 + TAB, ' ');
                counter += TAB - 1;
            } else
                ansi[counter + 2] = anOut;

            counter++;
        }
        return ansi;
    }

    /**
     * Parse cursor position response and return the actual cursor position.
     *
     * @param input the ANSI cursor position response sequence
     * @return a Point containing the column and row of the cursor
     */
    public static Point getActualCursor(int[] input) {
        boolean started = false;
        boolean gotSep = false;
        int col = 0;
        int row = 0;

        //read until we get a 'R'
        for (int i = 0; i < input.length - 1; i++) {
            if (started) {
                if (input[i] == 82)
                    break;
                else if (input[i] == 59) // we got a ';' which is the separator
                    gotSep = true;
                else {
                    if (gotSep) {
                        char c = (char) input[i];
                        col *= 10;
                        col += ((int) c & 0xF);
                    } else {
                        char c = (char) input[i];
                        row *= 10;
                        row += ((int) c & 0xF);
                    }
                }
            }
            //search for the beginning which starts with esc,[
            else if (input[i] == 27 && i < input.length - 1 && input[i + 1] == 91) {
                started = true;
                i++;
            }
        }

        return new Point(col, row);
    }

    /**
     * Create ANSI escape sequence to move cursor up by specified rows.
     *
     * @param rows number of rows to move up
     * @return ANSI escape sequence as int array
     */
    public static int[] moveRowsUp(int rows) {
        return moveInDirection(rows, 'A');
    }

    /**
     * Create ANSI escape sequence to move cursor down by specified rows.
     *
     * @param rows number of rows to move down
     * @return ANSI escape sequence as int array
     */
    public static int[] moveRowsDown(int rows) {
        return moveInDirection(rows, 'B');
    }

    /**
     * Create ANSI escape sequence to move cursor right by specified columns.
     *
     * @param rows number of columns to move right
     * @return ANSI escape sequence as int array
     */
    public static int[] moveColumnsRight(int rows) {
        return moveInDirection(rows, 'C');
    }

    /**
     * Create ANSI escape sequence to move cursor left by specified columns.
     *
     * @param rows number of columns to move left
     * @return ANSI escape sequence as int array
     */
    public static int[] moveColumnsLeft(int rows) {
        return moveInDirection(rows, 'D');
    }

    private static int[] moveInDirection(int value, char direction) {
        if (value < 10) {
            int[] out = new int[4];
            out[0] = 27; // esc
            out[1] = '['; // [
            out[2] = 48 + value;
            out[3] = direction;
            return out;
        } else {
            int[] asciiColumn = intToAsciiInts(value);
            int[] out = new int[3 + asciiColumn.length];
            out[0] = 27; // esc
            out[1] = '['; // [
            System.arraycopy(asciiColumn, 0, out, 2, asciiColumn.length);
            out[out.length - 1] = direction;
            return out;
        }
    }

    /**
     * we assume that value is > 0
     *
     * @param value int value (non ascii value)
     * @return ascii represented int value
     */
    private static int[] intToAsciiInts(int value) {
        int length = getAsciiSize(value);
        int[] asciiValue = new int[length];

        if (length == 1) {
            asciiValue[0] = 48 + value;
        } else {
            while (length > 0) {
                length--;
                int num = value % 10;
                asciiValue[length] = 48 + num;
                value = value / 10;
            }
        }
        return asciiValue;
    }

    private static int getAsciiSize(int value) {
        if (value < 10)
            return 1;
        //very simple way of getting the length
        if (value > 9 && value < 99)
            return 2;
        else if (value > 99 && value < 999)
            return 3;
        else if (value > 999 && value < 9999)
            return 4;
        else
            return 5;
    }

}
