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

import org.jboss.aesh.readline.Readline;
import org.jboss.aesh.readline.ReadlineBuilder;
import org.jboss.aesh.tty.terminal.TerminalConnection;
import org.jboss.aesh.util.LoggerUtil;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SimpleExample {

    public static void main(String... args) {
        LoggerUtil.doLog();
        TerminalConnection connection = new TerminalConnection();
        Readline readline = ReadlineBuilder.builder().enableHistory(false).build();
        read(connection, readline, "[aesh@rules]$ ");
        connection.startBlockingReader();
    }

    private static void read(TerminalConnection connection, Readline readline, String prompt) {
        readline.readline(connection, prompt, input -> {
            if(input != null && input.equals("exit")) {
                connection.write("we're exiting\n");
                connection.close();
            }
            else {
                connection.write("=====> "+input+"\n");
                read(connection, readline, prompt);
            }
        });
    }
}
