package org.aesh.readline.terminal;

public class TerminalChecker {

    public static boolean isTerminalAvailable() {
        var console = System.console();
        return console != null && console.isTerminal();
    }
}
