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
package org.aesh.readline.util;

import java.io.IOException;

import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Utility class for retrieving terminal size and type information.
 *
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class TerminalUtil {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private TerminalUtil() {
    }

    /**
     * Returns the width of the terminal in columns.
     *
     * @return the terminal width, or -1 if the terminal is not available
     */
    public static int terminalWidth() {
        return terminalSize().getWidth();
    }

    /**
     * Returns the height of the terminal in rows.
     *
     * @return the terminal height, or -1 if the terminal is not available
     */
    public static int terminalHeight() {
        return terminalSize().getHeight();
    }

    /**
     * Returns the size of the terminal.
     *
     * @return the terminal size, or a Size with -1 dimensions if the terminal is not available
     */
    public static Size terminalSize() {
        TerminalConnection connection = terminal();
        if (connection != null)
            return connection.size();
        else
            return new Size(-1, -1);
    }

    /**
     * Returns the type of the terminal (e.g., "xterm", "vt100").
     *
     * @return the terminal type string, or an empty string if the terminal is not available
     */
    public static String terminalType() {
        TerminalConnection connection = terminal();
        if (connection != null)
            return connection.device().type();
        else
            return "";
    }

    /**
     * Creates a new terminal connection.
     *
     * @return a new TerminalConnection, or null if an error occurs
     */
    private static TerminalConnection terminal() {
        try {
            return new TerminalConnection();
        } catch (IOException e) {
            return null;
        }
    }
}
