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
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.alias.AliasCompletion;
import org.aesh.readline.alias.AliasManager;
import org.aesh.readline.alias.AliasPreProcessor;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.formatting.CharacterType;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalCharacter;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Signal;
import org.aesh.utils.ANSI;
import org.aesh.utils.Config;
import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A more extensive example compared to {@link SimpleExample}
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Example implements Consumer<Connection> {

    private static final Logger LOGGER = LoggerUtil.getLogger(Example.class.getName());
    private static final boolean ALIAS = true;
    private static boolean masking = false;
    private static Prompt defaultPrompt;
    private static Thread sleeperThread;
    private static AliasManager aliasManager;
    private static List<Function<String, Optional<String>>> preProcessors;
    private static AliasCompletion aliasCompletion;

    public static void main(String[] args) throws IOException {
       LoggerUtil.doLog();
        new TerminalConnection( new Example());
    }

    @Override
    public void accept(Connection connection) {
        try {
            defaultPrompt = createDefaultPrompt();
            preProcessors = new ArrayList<>();
            if(ALIAS) {
                aliasManager = new AliasManager(null, false);
                AliasPreProcessor aliasPreProcessor = new AliasPreProcessor(aliasManager);
                preProcessors.add(aliasPreProcessor);
                aliasCompletion = new AliasCompletion(aliasManager);
            }
            Readline readline = new Readline();
            connection.setSignalHandler( signal -> {
                if(signal == Signal.INT) {
                    connection.write("we're catching ctrl-c, just continuing.\n");
                    if(sleeperThread != null)
                        sleeperThread.interrupt();
                }
            });

            connection.setCloseHandler(close -> {
                connection.write("connection closed, we're shutting down, do something...!"+ Config.getLineSeparator());
            });

            readInput(connection, readline, defaultPrompt);
            connection.openBlocking();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readInput(Connection connection, Readline readline, Prompt prompt) {
        readline.readline(connection, prompt, line -> {
            if(line == null) {
                connection.write("got eof, lets quit.\n");
                connection.close();
            }
            else if(masking) {
                connection.write("got password: "+line+", stopping masking\n");
                masking = false;
                readInput(connection, readline, defaultPrompt);
            }
            else if(ALIAS && line.startsWith("alias")) {
                String out = aliasManager.parseAlias(line.trim());
                if(out != null)
                    connection.write(out);
                readInput(connection, readline, defaultPrompt);
            }
            else if(ALIAS && line.startsWith("unalias")) {
               String out = aliasManager.removeAlias(line.trim());
                if(out != null)
                    connection.write(out);
                readInput(connection, readline, defaultPrompt);

            }
            else if(line.equals("quit") || line.equals("exit")) {
                connection.write("quitting...\n");
                connection.close();
                return;
            }
            else if(line.equals("clear")) {
                connection.write("lets clear the screen after two seconds");
                try {
                    Thread.sleep(2000);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
                readInput(connection, readline, defaultPrompt);
            }
            else if(line.equals("sleep")) {
                try {
                    connection.write("we're going to sleep for 5 seconds, you can interrupt me if you want.\n");
                    sleeperThread = Thread.currentThread();
                    Thread.sleep(5000);
                    connection.write("done sleeping, returning.\n");
                    sleeperThread = null;
                    readInput(connection, readline, defaultPrompt);
                }
                catch (InterruptedException e) {
                    connection.write("we got interrupted, lets continue...\n");
                    sleeperThread = null;
                    readInput(connection, readline, defaultPrompt);
                }
            }
            else if(line.equals("login")) {
                masking = true;
                readInput(connection, readline, new Prompt("password: ", (char) 0));
            }
            else if(line.startsWith("man")) {
                connection.write("trying to wait for input:\n");
                readline.readline(connection, "write something: ", newLine -> {
                    connection.write("we got: "+newLine+"\n");
                    readInput(connection, readline, prompt);
                });
            }
            else {
                connection.write("=====>_"+line+"_\n");
                readInput(connection, readline, prompt);
            }
        }, getCompletions(), preProcessors);
    }

    private static List<Completion> getCompletions() {
        List<Completion> completions = new ArrayList<>();
        completions.add( completeOperation -> {
            if("exit".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("exit");
            if("quit".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("quit");
            if("sleep".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("sleep");
            if("login".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("login");
            if("man".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("man");
            if("clear".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("clear");

        });
        completions.add(aliasCompletion);
       return completions;
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


}
