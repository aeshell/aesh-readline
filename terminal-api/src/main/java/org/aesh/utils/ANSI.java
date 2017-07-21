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
package org.aesh.utils;

import org.aesh.terminal.tty.Point;

import java.util.Arrays;

/**
 * Utility class to provide ANSI codes for different operations
 *
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class ANSI {

    private static final int TAB = 4;

    public static final String START = "\u001B[";
    public static final String BLACK_TEXT = "\u001B[0;30m";
    public static final String RED_TEXT = "\u001B[0;31m";
    public static final String GREEN_TEXT = "\u001B[0;32m";
    public static final String YELLOW_TEXT = "\u001B[0;33m";
    public static final String BLUE_TEXT = "\u001B[0;34m";
    public static final String MAGENTA_TEXT = "\u001B[0;35m";
    public static final String CYAN_TEXT = "\u001B[0;36m";
    public static final String WHITE_TEXT = "\u001B[0;37m";
    public static final String DEFAULT_TEXT = "\u001B[0;39m";

    public static final String BLACK_BG = "\u001B[0;40m";
    public static final String RED_BG = "\u001B[0;41m";
    public static final String GREEN_BG = "\u001B[0;42m";
    public static final String YELLOW_BG = "\u001B[0;43m";
    public static final String BLUE_BG = "\u001B[0;44m";
    public static final String MAGENTA_BG = "\u001B[0;45m";
    public static final String CYAN_BG = "\u001B[0;46m";
    public static final String WHITE_BG = "\u001B[0;47m";
    public static final String DEFAULT_BG = "\u001B[0;49m";
    public static final String ALTERNATE_BUFFER =
            InfoCmpHelper.getCurrentTranslatedCapability("smcup","\u001B[?1049h");
    public static final String MAIN_BUFFER =
            InfoCmpHelper.getCurrentTranslatedCapability("rmcup","\u001B[?1049l");
    public static final String INVERT_BACKGROUND =
            InfoCmpHelper.getCurrentTranslatedCapability("smso","\u001B[7m");
    public static final String NORMAL_BACKGROUND =
            InfoCmpHelper.getCurrentTranslatedCapability("rmso","\u001B[27m");
    public static final String RESET = "\u001B[0m";
    public static final String BOLD =
            InfoCmpHelper.getCurrentTranslatedCapability("bold","\u001B[0;1m");
    public static final String BOLD_OFF = "\u001B[0;22m";
    public static final String UNDERLINE =
            InfoCmpHelper.getCurrentTranslatedCapability("smul","\u001B[0;4m");
    public static final String UNDERLINE_OFF =
            InfoCmpHelper.getCurrentTranslatedCapability("rmul","\u001B[0;24m");
    public static final String BLINK =
            InfoCmpHelper.getCurrentTranslatedCapability("blink","\u001B[5m");
    public static final String BLINK_OFF = "\u001B[25m";
    public static final int[] CURSOR_START = new int[]{ 27, '[', 'G'};
    public static final int[] ERASE_WHOLE_LINE = new int[]{ 27, '[', '2', 'K'};
    public static final String CURSOR_ROW = "\u001B[6n";
    public static final int[] CLEAR_SCREEN =
            InfoCmpHelper.getCurrentTranslatedCapability("clear","\u001B[2J").codePoints().toArray();
    public static final String CURSOR_SAVE =
            InfoCmpHelper.getCurrentTranslatedCapability("sc","\u001B[s");
    public static final String CURSOR_RESTORE =
            InfoCmpHelper.getCurrentTranslatedCapability("rc","\u001B[u");
    public static final String CURSOR_HIDE = "\u001B[?25l";
    public static final String CURSOR_SHOW = "\u001B[?25h";
    public static final int[] ERASE_LINE_FROM_CURSOR = new int[]{ 27, '[', 'K'};
    public static final int[] MOVE_LINE_UP = new int[]{ 27, '[', '1', 'A'};
    public static final int[] MOVE_LINE_DOWN = new int[]{ 27, '[', '1', 'B'};

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
        int[] ansi = new int[out.length+2];
        ansi[0] = 27;
        ansi[1] = '[';
        int counter = 0;
        for (char anOut : out) {
            if (anOut == '\t') {
                Arrays.fill(ansi, counter + 2, counter + 2 + TAB, ' ');
                counter += TAB - 1;
            }
            else
                ansi[counter + 2] = anOut;

            counter++;
        }
        return ansi;
    }

    public static Point getActualCursor(int[] input) {
        boolean started = false;
        boolean gotSep = false;
        int col = 0;
        int row = 0;

        //read until we get a 'R'
        for(int i=0; i < input.length-1; i++) {
            if(started) {
                if(input[i] == 82)
                    break;
                else if(input[i] == 59) // we got a ';' which is the separator
                    gotSep = true;
                else {
                    if(gotSep) {
                        char c = (char) input[i];
                        col *= 10;
                        col += ((int)c & 0xF);
                    }
                    else {
                        char c = (char) input[i];
                        row *= 10;
                        row += ((int)c & 0xF);
                    }
                }
            }
            //search for the beginning which starts with esc,[
            else if(input[i] == 27 && i < input.length-1 && input[i+1] == 91) {
                started = true;
                i++;
            }
        }

        return new Point(col, row);
    }
}
