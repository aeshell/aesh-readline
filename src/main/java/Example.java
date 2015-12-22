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

import org.jboss.aesh.readline.Prompt;
import org.jboss.aesh.readline.Readline;
import org.jboss.aesh.terminal.formatting.CharacterType;
import org.jboss.aesh.terminal.formatting.Color;
import org.jboss.aesh.terminal.formatting.TerminalCharacter;
import org.jboss.aesh.terminal.formatting.TerminalColor;
import org.jboss.aesh.tty.Connection;
import org.jboss.aesh.tty.Signal;
import org.jboss.aesh.tty.terminal.TerminalConnection;
import org.jboss.aesh.util.LoggerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Example {

    private static boolean masking = false;
    private static Prompt defaultPrompt;
    private static Thread sleeperThread;

    public static void main(String[] args) throws IOException {
        LoggerUtil.doLog();

        defaultPrompt = createDefaultPrompt();
        Readline readline = new Readline();
        TerminalConnection connection = new TerminalConnection();
        connection.setSignalHandler( signal -> {
            if(signal == Signal.INT) {
                connection.write("we're catching ctrl-c, just continuing.\n");
                if(sleeperThread != null)
                sleeperThread.interrupt();
            }
        });

        connection.setCloseHandler(close -> {
            connection.write("we're shutting down, do something...!");
        });

        //readInput(connection, readline, defaultPrompt);
        readInput(connection, readline, new Prompt("[aesh@rules]$ "));
        connection.startBlockingReader();

        //a simple interruptHook
        /*
        builder.interruptHook(new InterruptHook() {
            @Override
            public void handleInterrupt(Console console, Action action) {
                if(action == Action.INTERRUPT) {
                    console.getShell().out().println("^C");
                    console.clearBufferAndDisplayPrompt();
                }
                else if(action == Action.IGNOREEOF) {
                    console.getShell().out().println("Use \"exit\" to leave the shell.");
                    console.clearBufferAndDisplayPrompt();
                }
                else {
                    console.getShell().out().println();
                    console.stop();
                }
            }
        });
        */


    }

    public static void readInput(Connection connection, Readline readline, Prompt prompt) {
        readline.readline(connection, prompt, line -> {
            connection.write("=====> "+line+"\n");
            if(line == null) {
                connection.write("got eof, lets quit.\n");
                connection.close();
            }
            else if(masking) {
                connection.write("got password: "+line+", stopping masking\n");
                masking = false;
                readInput(connection, readline, defaultPrompt);
            }
            else if(line.equals("quit") || line.equals("exit")) {
                connection.write("we're quitting...\n");
                connection.close();
                return;
            }
            else if(line.equals("sleep")) {
                try {
                    connection.write("we're going to sleep for 5 seconds, you can interrupt me if you want.\n");
                    sleeperThread = Thread.currentThread();
                    Thread.sleep(5000);
                    connection.write("done sleeping, returning.\n");
                    readInput(connection, readline, defaultPrompt);
                }
                catch (InterruptedException e) {
                    connection.write("we got interrupted, lets continue...\n");
                    readInput(connection, readline, defaultPrompt);
                }
            }
            else if(line.equals("password")) {
                masking = true;
                readInput(connection, readline, new Prompt("password: ", (char) 0));
            }
            else if(line.startsWith("man")) {
                connection.write("trying to wait for input:\n");
                Readline inputLine = new Readline();
                inputLine.readline(connection, "write something: ", newLine -> {
                    connection.write("we got: "+newLine+"\n");
                    readInput(connection, readline, prompt);
                });
            }
            else {
                readInput(connection, readline, prompt);
            }
        });
    }

    private static Prompt createDefaultPrompt() {
            List<TerminalCharacter> chars = new ArrayList<>();
        chars.add(new TerminalCharacter('[', new TerminalColor(Color.BLUE, Color.DEFAULT)));
        chars.add(new TerminalCharacter('t', new TerminalColor(Color.RED, Color.DEFAULT),
                CharacterType.ITALIC));
        chars.add(new TerminalCharacter('e', new TerminalColor(Color.RED, Color.DEFAULT),
                CharacterType.INVERT));
        chars.add(new TerminalCharacter('s', new TerminalColor(Color.RED, Color.DEFAULT),
                CharacterType.CROSSED_OUT));
        chars.add(new TerminalCharacter('t', new TerminalColor(Color.RED, Color.DEFAULT),
                CharacterType.BOLD));
        chars.add(new TerminalCharacter(']', new TerminalColor(Color.BLUE, Color.DEFAULT),
                CharacterType.FAINT));
        chars.add(new TerminalCharacter('$', new TerminalColor(Color.GREEN, Color.DEFAULT),
                CharacterType.UNDERLINE));
        chars.add(new TerminalCharacter(' ', new TerminalColor(Color.DEFAULT, Color.DEFAULT)));

        return new Prompt(chars);

    }

        /*
        final ConsoleCallback consoleCallback = new AeshConsoleCallback() {
            @Override
            public int execute(ConsoleOperation output) throws InterruptedException {
                try {
                //To change body of implemented methods use File | Settings | File Templates.
                exampleConsole.getShell().out().println("======>\"" + output.getBuffer());
                if(masking) {
                    exampleConsole.getShell().out().print("got password: " + output.getBuffer() + ", stopping masking");
                    masking = false;
                    exampleConsole.setPrompt(prompt);
                }
                else if (output.getBuffer().equalsIgnoreCase("quit") || output.getBuffer().equalsIgnoreCase("exit") ||
                        output.getBuffer().equalsIgnoreCase("reset")) {
                    exampleConsole.stop();
                }
                else if(output.getBuffer().equalsIgnoreCase("password")) {
                    masking = true;
                    exampleConsole.setPrompt(new Prompt("password: ", (char) 0));
                }
                else if(output.getBuffer().startsWith("blah")) {
                    exampleConsole.getShell().err().println("blah. command not found.");
                    exampleConsole.getShell().out().print("BAH" + Config.getLineSeparator());
                }
                else if(output.getBuffer().equals("clear"))
                    exampleConsole.clear();
                else if(output.getBuffer().startsWith("man")) {
                    //exampleConsole.attachProcess(test);
                    //man = new ExampleConsoleCommand(exampleConsole, output);
                    exampleConsole.getShell().out().println("trying to wait for input");
                    exampleConsole.getShell().out().println("got: " + exampleConsole.getInputLine());
                    //exampleConsole.attachProcess(test);
                }
                else if(output.getBuffer().startsWith("login")) {
                    exampleConsole.setConsoleCallback(passwordCallback);
                    exampleConsole.setPrompt(new Prompt("Username: "));
                }
                 return 0;
                }
                catch (IOException ioe) {
                    exampleConsole.getShell().out().println("Exception: "+ioe.getMessage());
                    return -1;
                }
            }
        };

        exampleConsole.setConsoleCallback(consoleCallback);
        exampleConsole.start();
        exampleConsole.setPrompt(prompt);

        passwordCallback = new AeshConsoleCallback() {
            private boolean hasUsername = false;

            @Override
            public int execute(ConsoleOperation output) throws InterruptedException {
                if(hasUsername) {
                    password = output.getBuffer();
                    hasPassword = true;
                    exampleConsole.getShell().out().print("Username: " + username + ", password: " + password + Config.getLineSeparator());
                    exampleConsole.setPrompt(prompt);
                    exampleConsole.setConsoleCallback(consoleCallback);
                }
                else {
                    username = output.getBuffer();
                    exampleConsole.setPrompt( new Prompt("Password: ", (char) 0));
                    hasUsername = true;
                }
                return 0;
            }
        };

        //show how we can change the prompt async
        try {
            Thread.sleep(4000);
            exampleConsole.setPrompt(new Prompt(
                    new TerminalString("[FOO]» ", new TerminalColor( Color.RED, Color.DEFAULT), new TerminalTextStyle(CharacterType.BOLD))));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static class ExampleConsoleCommand {

        private final Console console;
        private final ConsoleOperation operation;

        public ExampleConsoleCommand(Console console, ConsoleOperation operation) {
            this.console = console;
            this.operation = operation;

            init();
        }

        private void init() {
            try {
                if(!operation.getControlOperator().isRedirectionOut()) {
                    console.getShell().out().print(ANSI.ALTERNATE_BUFFER);
                    console.getShell().out().println("print alternate screen...");
                    console.getShell().out().flush();
                }

                if(console.getShell().in().getStdIn().available() > 0) {
                    java.util.Scanner s = new java.util.Scanner(console.getShell().in().getStdIn()).useDelimiter("\\A");
                    String fileContent = s.hasNext() ? s.next() : "";
                    console.getShell().out().println("FILECONTENT: ");
                    console.getShell().out().print(fileContent);
                    console.getShell().out().flush();
                }
                else
                    console.getShell().out().println("console.in() == null");


                readFromFile();

                //detach after init if hasRedirectOut()
                if(operation.getControlOperator().isRedirectionOut()) {
                }

                console.getShell().out().println("trying to wait on input");
                int input = console.getShell().in().getStdIn().read();
                console.getShell().out().println("we got: "+input);
            }
            catch(IOException ioe) {

            }
        }

        private void readFromFile() throws IOException {
            if(console.getShell().in().getStdIn().available() > 0) {
                console.getShell().out().println("FROM STDOUT: ");
            }
            else
                console.getShell().out().println("here should we present some text... press 'q' to quit");
        }

        public void processOperation(CommandOperation operation) throws IOException {
            if(operation.getInput()[0] == 'q') {
                console.getShell().out().print(ANSI.MAIN_BUFFER);
            }
            else if(operation.getInput()[0] == 'a') {
                readFromFile();
            }
            else {

            }
        }
    }
        */
}
