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

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.tty.terminal.TerminalConnection;

import java.io.IOException;

/**
 * A very simple example where we use the default values and create a simple
 * terminal application that reads the input and returns it.
 * Typing "exit" or ctrl-c/ctrl-d will exit the program.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SimpleExample {

    public static void main(String... args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        //we're setting up readline to read when connection receives any input
        //note that this needs to be done after every time Readline.readline returns
        read(connection, ReadlineBuilder.builder().enableHistory(false).build(), "[aesh@rules]$ ");
        //lets open the connection to the terminal using this thread
        connection.openBlocking();
    }

    private static void read(TerminalConnection connection, Readline readline, String prompt) {
        readline.readline(connection, prompt, input -> {
            //we specify a simple lambda consumer to read the input thats returned
            if(input != null && input.equals("exit")) {
                connection.write("we're exiting\n");
                connection.close();
            }
            else {
                connection.write("=====> "+input+"\n");
                //lets read until we get exit
                read(connection, readline, prompt);
            }
        });
    }
}
