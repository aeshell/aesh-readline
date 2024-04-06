package org.aesh.readline.terminal;

public class TerminalChecker {

    public static boolean isTerminalAvailable() {
        return System.console() != null;
    }
}
