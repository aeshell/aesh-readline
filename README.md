# Æsh (Another Extendable SHell) Readline

[![Build Status](https://github.com/aeshell/aesh-readline/actions/workflows/main.yml/badge.svg)](https://github.com/aeshell/aesh-readline/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Æsh Readline is a library for handling console input with the goal to support most GNU Readline features.

For examples, have a look here: [Aesh Examples](https://github.com/aeshell/aesh-examples)

## Table of Contents

- [Features](#features)
- [Scope](#scope)
- [Connectivity](#connectivity)
- [How to Build](#how-to-build)
- [Getting Started](#getting-started)
- [Examples](#examples)
- [Key Mappings](#keys-that-are-mapped-by-default-in-æsh-readline)
- [Supported Runtime Properties](#supported-runtime-properties)
- [Contributing](#contributing)
- [License](#license)

## Features

- Line editing
- History (search, persistence)
- Completion
- Masking
- Undo and Redo
- Paste buffer
- Emacs and Vi editing mode
- Supports POSIX OS's and Windows
- Easy to configure (history file & buffer size, edit mode, streams, possible to override terminal implementations, etc)
- Support standard out and standard error
- Redirect
- Alias
- Pipeline

## Scope

The scope of Æsh Readline is to provide an easy to use Readline library. It is used by the Aesh project.

## Connectivity

Æsh Readline supports remote connections via SSH, Telnet, and WebSockets, enabling console applications to run over networks.

## How to Build

Æsh Readline uses Maven (https://maven.apache.org/) as its build tool.

### Prerequisites
- Java 8 or higher
- Maven 3.6+

### Building
To build the project:
```bash
mvn clean install
```

To run tests:
```bash
mvn test
```

## Getting Started

Here's a simple example to get you started:

```java
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.tty.terminal.TerminalConnection;

import java.io.IOException;


public class SimpleExample {

    public static void main(String... args) {
        TerminalConnection connection = new TerminalConnection();
        read(connection, ReadlineBuilder.builder().enableHistory(false).build(), "[aesh@rules]$ ");
        connection.openBlocking();
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
```

## Examples

For examples, have a look at [Aesh Examples](https://github.com/aeshell/aesh-examples/readline)

## Keys that are mapped by default in Æsh Readline

Note: C equals Control and M is Meta/Alt

### EMACS Mode

- Move back one char : C-b or left arrow
- Move forward one char : C-f or right arrow
- Delete the character left of cursor : backspace
- Delete the character on cursor : C-d
- Undo : C-_ or C-x C-u
- Move to the start of the line : C-a or home
- Move to the end of the line : C-e or end
- Move forward a word, where a word is composed of letters and digits : M-f
- Move backward a word : M-b
- Previous line : up arrow
- Next line : down arrow
- Clear the screen, reprinting the current line at the top : C-l
- Delete next word to the right of cursor : M-d
- Complete : tab
- Kill the text from the current cursor position to the end of the line : C-k
- Kill from the cursor to the end of the current word, or, if between words, to the end of the next word : M-d
- Kill from the cursor to the previous whitespace : C-w
- Yank the most recently killed text back into the buffer at the cursor : C-y
- Search backward in the history for a particular string : C-r
- Search forward in the history for a particular string : C-s
- Switch to VI editing mode: M-C-j

### VI Mode

In command mode: About every vi command is supported, here's a few:

- Move back one char : h
- Move forward one char : l
- Delete the character left of cursor : X
- Delete the character on cursor : x
- Undo : u
- Move to the start of the line : 0
- Move to the end of the line : $
- Move forward a word, where a word is composed of letters and digits : w
- Move backward a word : b
- Previous line : k
- Next line : n
- Clear the screen, reprinting the current line at the top : C-l
- Delete next word to the right of cursor : dw
- Kill the text from the current cursor position to the end of the line : D and d$
- Kill from the cursor to the end of the current word, or, if between words, to the end of the next word : db
- Kill from the cursor to the previous whitespace : dB
- Yank the most recently killed text back into the buffer at the cursor : p (after cursor), P (before cursor)
- Add text into yank buffer : y + movement action
- Enable change mode : c
- Repeat previous action : .
- +++ (read a vi manual)

In edit mode:

- Search backward in the history for a particular string : C-r
- Search forward in the history for a particular string : C-s
- Delete the character left of cursor : backspace

## Supported runtime properties

- aesh.terminal : specify Terminal object
- aesh.editmode : specify either VI or EMACS edit mode
- aesh.readinputrc : specify if Æsh should read settings from inputrc
- aesh.inputrc : specify the inputrc file (must exist)
- aesh.historyfile : specify the history file (must exist)
- aesh.historypersistent : specify if Æsh should persist history file on exit
- aesh.historydisabled : specify if history should be disabled
- aesh.historysize : specify the maximum size of the history file
- aesh.logging : specify if logging should be enabled
- aesh.logfile : specify the log file
- aesh.disablecompletion : specify if completion should be disabled

## Contributing

We welcome contributions! Please see our [Contributing Guide](https://github.com/aeshell/aesh-readline/blob/main/CONTRIBUTING.md) for details on how to get started.

- Report issues: [GitHub Issues](https://github.com/aeshell/aesh-readline/issues)
- Submit pull requests: [GitHub Pull Requests](https://github.com/aeshell/aesh-readline/pulls)

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.
