package org.aesh.readline.example;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * Demo showing the right prompt feature.
 * The right side of the prompt line shows the current time.
 * Type commands and watch the right prompt disappear as input grows.
 * Type "exit" or press Ctrl+D to quit.
 */
public class RightPromptDemo {

    private static volatile boolean stopped = false;

    public static void main(String[] args) throws IOException {
        TerminalConnection connection = new TerminalConnection();
        Attributes savedAttr = connection.enterRawMode();

        // No initial signal handler — Readline handles Ctrl+C internally.
        // Ctrl+D (EOF) closes via the requestHandler receiving null.

        connection.setCloseHandler(v -> {
            stopped = true;
            try {
                connection.setAttributes(savedAttr);
            } catch (Exception | java.io.IOError ignored) {
            }
        });

        Readline readline = ReadlineBuilder.builder().enableHistory(true).build();
        read(connection, readline);
        connection.openBlocking();
    }

    private static void read(TerminalConnection connection, Readline readline) {
        if (stopped) return;
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Prompt prompt = Prompt.builder()
                .message("demo$ ")
                .rightPrompt(time)
                .build();

        readline.readline(connection, prompt, line -> {
            if (line == null || "exit".equals(line)) {
                connection.close();
                return;
            }
            if (!line.isEmpty()) {
                connection.write("  -> " + line + "\n");
            }
            read(connection, readline);
        });
    }
}
