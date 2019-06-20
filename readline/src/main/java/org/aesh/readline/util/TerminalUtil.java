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

import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.tty.Size;

import java.io.IOException;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class TerminalUtil {

    public static int terminalWidth() {
        return terminalSize().getWidth();
    }

    public static int terminalHeight() {
       return terminalSize().getHeight();
    }

    public static Size terminalSize() {
        TerminalConnection connection = terminal();
        if(connection != null)
            return connection.size();
        else
            return new Size(-1,-1);
    }

    public static String terminalType() {
        TerminalConnection connection = terminal();
        if(connection != null)
            return connection.device().type();
        else
            return "";
    }

    private static TerminalConnection terminal() {
        try {
            return new TerminalConnection();
        }
        catch (IOException e) {
            return null;
        }
    }
}
