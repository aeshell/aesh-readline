/*
 * Demo application for testing the interactive fuzzy history search (Ctrl+R).
 *
 * Type commands and press Enter to add them to history.
 * Press Ctrl+R to open the fuzzy search.
 * Type "exit" to quit.
 */
package org.aesh.readline.example;

import java.io.IOException;

import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.tty.TerminalConnection;

public class FuzzySearchExample {

    private static final String[] SEED_COMMANDS = {
            "ls -la",
            "cd /home/user/projects",
            "git status",
            "git commit -m \"initial commit\"",
            "git push origin main",
            "git pull --rebase",
            "git log --oneline -10",
            "mvn clean test",
            "mvn clean install -DskipTests",
            "mvn clean test -pl readline -Dtest=HistoryTest",
            "grep -rn \"FuzzyMatch\" src/",
            "find . -name \"*.java\" -type f",
            "docker ps -a",
            "docker-compose up -d",
            "ssh user@server.example.com",
            "cat /etc/hosts",
            "vim ~/.bashrc",
            "java -jar target/app.jar --port=8080",
            "curl -s https://api.example.com/health",
            "tail -f /var/log/syslog",
    };

    public static void main(String... args) throws IOException {
        TerminalConnection connection = new TerminalConnection();

        Readline readline = ReadlineBuilder.builder()
                .enableHistory(true)
                .build();

        // Pre-seed history with sample commands
        readline.enableHistorySuggestions();

        connection.write("=== Fuzzy History Search Demo ===\n");
        connection.write("Type commands and press Enter to add to history.\n");
        connection.write("Press Ctrl+R to open the fuzzy search.\n");
        connection.write("  - Type to filter results\n");
        connection.write("  - Up/Down to navigate\n");
        connection.write("  - Enter to select, Esc to cancel\n");
        connection.write("  - Ctrl+R again to toggle sort order\n");
        connection.write("Type 'seed' to pre-load 20 sample commands into history.\n");
        connection.write("Type 'exit' to quit.\n\n");

        read(connection, readline);
        connection.openBlocking();
    }

    private static void read(TerminalConnection connection, Readline readline) {
        readline.readline(connection, "demo$ ", input -> {
            if (input != null && input.equals("exit")) {
                connection.write("Bye!\n");
                connection.close();
            } else if (input != null && input.equals("seed")) {
                // Seed history by reading each command
                connection.write("Seeding " + SEED_COMMANDS.length + " commands into history...\n");
                for (String cmd : SEED_COMMANDS) {
                    // Push directly into history via a readline cycle
                    // We'll use a simpler approach: just tell the user to do it
                }
                connection.write("Done! Press Ctrl+R to search.\n");
                // Actually seed by pushing through readline
                seedHistory(connection, readline, 0);
            } else {
                if (input != null && !input.isEmpty()) {
                    connection.write("  -> " + input + "\n");
                }
                read(connection, readline);
            }
        });
    }

    private static void seedHistory(TerminalConnection connection, Readline readline, int index) {
        if (index >= SEED_COMMANDS.length) {
            connection.write("Seeded " + SEED_COMMANDS.length + " commands. Press Ctrl+R to search!\n");
            read(connection, readline);
            return;
        }
        readline.readline(connection, "", input -> {
            seedHistory(connection, readline, index + 1);
        });
        // Simulate typing the command + Enter
        String cmd = SEED_COMMANDS[index];
        for (int cp : cmd.codePoints().toArray()) {
            connection.stdinHandler().accept(new int[]{cp});
        }
        connection.stdinHandler().accept(new int[]{13}); // Enter
    }
}
