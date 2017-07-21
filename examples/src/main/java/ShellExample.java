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
import org.aesh.readline.completion.Completion;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Signal;
import org.aesh.utils.Config;
import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This example is mostly taken from the TestShell class in Termd.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ShellExample implements Consumer<Connection>{

    private static final Pattern splitter = Pattern.compile("\\w+");

    private static final Logger LOGGER = LoggerUtil.getLogger(ShellExample.class.getName());

    private boolean stopped = false;

    public static void main(String[] args) throws IOException {
        LoggerUtil.doLog();
        new TerminalConnection(new ShellExample());
    }

    @Override
    public void accept(Connection connection) {
        System.out.println("starting readline..");
        connection.setSignalHandler( signal -> {
            connection.write("\nlets quit\n");
            connection.close();
        });

        connection.setCloseHandler(close->{
            stopped = true;
        });

        Readline readline = new Readline(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        read(connection, readline);
        connection.openBlocking();
    }

    /**
     * Use {@link Readline} to openBlocking a user input and then process it
     *
     * @param conn the tty connection
     * @param readline the readline object
     */
    public void read(final Connection conn, final Readline readline) {
        // Just call readline and get a callback when line is openBlocking
        Prompt prompt = new Prompt(new TerminalString("[aesh@rules]$ ",
                        new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT)));

        //suspend reader asap since we're creating commands in a new thread
        //this is not needed when running single threaded, eg as Example
       readline.readline(conn, prompt, line -> {
            // Ctrl-D
            if (line == null) {
                //((TerminalConnection) conn).stop();
                conn.write("logout\n").close();
                return;
            }

            //LOGGER.info("got: " + line);

            Matcher matcher = splitter.matcher(line);
            if (matcher.find()) {
                String cmd = matcher.group();

                /*
                if(cmd.equals("exit")) {
                    conn.write("exiting...\n").close();
                    return;
                }
                */

                // Gather args
                List<String> args = new ArrayList<>();
                while (matcher.find()) {
                    args.add(matcher.group());
                }

                try {
                    new Task(conn, readline, Command.valueOf(cmd), args).start();
                    return;
                } catch (IllegalArgumentException e) {
                    conn.write(line + ": command not found\n");
                }
            }
            read(conn, readline);
        }, getCompletions());
    }

    private List<Completion> getCompletions() {
        List<Completion> completions =  new ArrayList<>();
        completions.add(completeOperation -> {
            if("exit".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("exit");
            if("sleep".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("sleep");
            if("echo".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("echo");
            if("window".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("window");
            if("help".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("help");
            if("keyscan".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("keyscan");
            if("linescan".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("linescan");
            if("top".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("top");
            if("cursor".startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate("cursor");
        });
        return completions;
    }


    /**
     * A blocking interruptable task.
     */
    class Task extends Thread implements Consumer<Signal> {

        final Connection conn;
        final Readline readline;
        final Command command;
        final List<String> args;

        public Task(Connection conn, Readline readline, Command command, List<String> args) {
            this.conn = conn;
            this.readline = readline;
            this.command = command;
            this.args = args;
        }

        @Override
        public void accept(Signal signal) {
            switch (signal) {
                case INT:
                    // Ctrl-C interrupt : we use Thread interrupts to signal the command to stop
                    LOGGER.info("got interrupted in Task");
                    interrupt();
            }
        }

        @Override
        public void run() {
            // Subscribe to events, in particular Ctrl-C
            conn.setSignalHandler(this);
            try {
                command.execute(conn, args);
            }
            catch (InterruptedException | InterruptedIOException e) {
                // Ctlr-C interrupt
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                conn.setSignalHandler(null);
                // Readline again
                if(!stopped)
                    read(conn, readline);
            }
        }
    }

    /**
     * The shell app commands.
     */
    enum Command {

        sleep() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                if (args.isEmpty()) {
                    conn.write("usage: sleep seconds\n");
                    return;
                }
                int time = -1;
                try {
                    time = Integer.parseInt(args.get(0));
                } catch (NumberFormatException ignore) {
                }
                if (time > 0) {
                    // Sleep until timeout or Ctrl-C interrupted
                    Thread.sleep(time * 1000);
                }
            }
        },

       exit() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                conn.close();
            }
        },


       echo() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                for (int i = 0;i < args.size();i++) {
                    if (i > 0) {
                        conn.write(" ");
                    }
                    conn.write(args.get(i));
                }
                conn.write("\n");
            }
        },

        window() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                conn.write("Current window size " + conn.size() + ", try resize it\n");

                // Refresh the screen with the new size
                conn.setSizeHandler(size -> {
                    conn.write("Window resized " + size + "\n");
                });

                try {
                    // Wait until interrupted
                    new CountDownLatch(1).await();
                } finally {
                    conn.setSizeHandler(null);
                }
            }
        },

        help() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                StringBuilder msg = new StringBuilder("Demo term, try commands: ");
                Command[] commands = Command.values();
                for (int i = 0;i < commands.length;i++) {
                    if (i > 0) {
                        msg.append(",");
                    }
                    msg.append(" ").append(commands[i].name());
                }
                msg.append("...\n");
                conn.write(msg.toString());
            }
        },

        keyscan() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                //we need to enter raw mode to get each keystroke
                Attributes attributes = conn.enterRawMode();
                // Subscribe to key events and print them
                conn.setStdinHandler(keys -> {
                    for (int key : keys) {
                        conn.write(key + " pressed\n");
                    }
                });
                try {
                    // Wait until interrupted
                    new CountDownLatch(1).await();
                }
                finally {
                    conn.setStdinHandler(null);
                    conn.setAttributes(attributes);
                }
            }
        },

        linescan() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                String line = readLine("[myprompt]", conn);

                conn.write("we got: "+line+Config.getLineSeparator());
            }

            private String readLine(String prompt, Connection conn) throws InterruptedException {
                CountDownLatch latch = new CountDownLatch(1);
                Readline readline = new Readline();
                String[] out = new String[1];
                readline.readline(conn, "[myprompt]: ", event -> {
                    out[0] = event;
                    latch.countDown();
                });
                try {
                    // Wait until interrupted
                    latch.await();
                }
                finally {
                    conn.setStdinHandler(null);
                }

                return out[0];
            }

        },

        top() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                while (true) {

                    StringBuilder buf = new StringBuilder();
                    Formatter formatter = new Formatter(buf);

                    List<Thread> threads = new ArrayList<>(Thread.getAllStackTraces().keySet());
                    for (int i = 1;i <= conn.size().getHeight();i++) {

                        // Change cursor position and erase line with ANSI escape code magic
                        buf.append("\033[").append(i).append(";1H\033[K");

                        //
                        String format = "  %1$-5s %2$-10s %3$-50s %4$s";
                        if (i == 1) {
                            formatter.format(format,
                                    "ID",
                                    "STATE",
                                    "NAME",
                                    "GROUP");
                        } else {
                            int index = i - 2;
                            if (index < threads.size()) {
                                Thread thread = threads.get(index);
                                formatter.format(format,
                                        thread.getId(),
                                        thread.getState().name(),
                                        thread.getName(),
                                        thread.getThreadGroup().getName());
                            }
                        }
                    }

                    conn.write(buf.toString());
                    // Sleep until we refresh the list of interrupted
                    Thread.sleep(1000);
                }
            }
        },

        cursor() {
            @Override
            public void execute(Connection conn, List<String> args) throws Exception {
                conn.write("cursor position is: ");
                Point p = conn.getCursorPosition();
                conn.write(p.toString()+Config.getLineSeparator());
            }

        };

        abstract void execute(Connection conn, List<String> args) throws Exception;
    }
}
