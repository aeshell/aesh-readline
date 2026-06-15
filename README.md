# Æsh (Another Extendable SHell) Readline

[![Build Status](https://github.com/aeshell/aesh-readline/actions/workflows/main.yml/badge.svg)](https://github.com/aeshell/aesh-readline/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Homepage:** [aeshell.github.io](https://aeshell.github.io)

Æsh Readline is a library for handling console input with the goal to support most GNU Readline features.

For examples, have a look here: [Aesh Examples](https://github.com/aeshell/aesh-examples)

## Table of Contents

- [Features](#features)
- [Scope](#scope)
- [Connectivity](#connectivity)
- [How to Build](#how-to-build)
- [Getting Started](#getting-started)
- [Terminal Colors](#terminal-colors)
- [Terminal Images](#terminal-images)
- [Documentation](#documentation)
- [Examples](#examples)
- [Key Mappings](#keys-that-are-mapped-by-default-in-æsh-readline)
- [Supported Runtime Properties](#supported-runtime-properties)
- [Contributing](#contributing)
- [License](#license)

## Features

### Core Readline
- Line editing with undo/redo
- History (search, persistence, [fuzzy search](https://aeshell.github.io/docs/readline/fuzzy-history-search/) via Ctrl+R, [ignore patterns](https://aeshell.github.io/docs/readline/history/))
- Completion
- Masking (password input)
- Paste buffer
- Emacs and Vi editing mode
- [Right prompt](https://aeshell.github.io/docs/readline/right-prompt/) (right-aligned, auto-hiding)
- [Ghost text suggestions](https://aeshell.github.io/docs/readline/readline-api/) (inline history suggestions)
- Supports POSIX and Windows
- Easy to configure (history file & buffer size, edit mode, streams, etc)
- Redirect, Alias, Pipeline

### Async Output
- **[Print Above](https://aeshell.github.io/docs/readline/print-above/)** - Print text above the prompt without disrupting input (thread-safe)
- **[Status Lines](https://aeshell.github.io/docs/readline/status-lines/)** - Persistent status display between output and prompt
- **[Split Screen](https://aeshell.github.io/docs/readline/split-screen/)** - Independent scrolling regions (experimental)

### Terminal Features
- **[Focus Tracking](https://aeshell.github.io/docs/readline/focus-tracking/)** - Detect terminal focus gained/lost
- **[Mouse Tracking](https://aeshell.github.io/docs/readline/mouse-tracking/)** - Mouse events with SGR encoding
- **[Synchronized Output](https://aeshell.github.io/docs/readline/synchronized-output/)** - Tear-free rendering (Mode 2026)
- **[Non-blocking Input](https://aeshell.github.io/docs/readline/posix-native-access/)** - Poll-based read with escape timeout disambiguation (Java 22+)
- **[Terminal Provider SPI](https://aeshell.github.io/docs/readline/terminal-provider/)** - Pluggable terminal implementations

### Terminal Colors and Styling
- **Color Detection** - Automatically detect terminal theme (light/dark) and color depth
- **ANSIBuilder** - Fluent API for building styled terminal output
- **Semantic Colors** - Theme-aware colors for log levels (error, warning, info, debug, trace)
- **Extended Colors** - Support for 256-color palette and 24-bit true color (RGB/hex)
- **Color Adaptation** - Automatic color downgrading for terminals with limited color support

### Terminal Graphics
- **Image Support** - Display images in supported terminals (Kitty, iTerm2, Sixel)

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

Note: the Windows JNI library `aesh-console.dll` is built with a non-executable stack to avoid JVM stack guard warnings during native-image builds. On Java 22+, the FFM-based implementation is used instead of the JNI DLL.

Troubleshooting: if you still see a stack guard warning mentioning `aesh-console*.dll`, rebuild the DLL or run `execstack -c` on the extracted library in your build image.

## Getting Started

### Maven Dependency

Use the BOM (Bill of Materials) to manage all module versions consistently:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.aesh</groupId>
            <artifactId>aesh-readline-bom</artifactId>
            <version>3.14</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the modules you need without specifying versions:

```xml
<dependencies>
    <dependency>
        <groupId>org.aesh</groupId>
        <artifactId>readline</artifactId>
    </dependency>
    <dependency>
        <groupId>org.aesh</groupId>
        <artifactId>terminal-tty</artifactId>
    </dependency>
</dependencies>
```

Or without the BOM, add dependencies directly:

```xml
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>readline</artifactId>
    <version>3.14</version>
</dependency>
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>terminal-tty</artifactId>
    <version>3.14</version>
</dependency>
```

`readline` pulls in `terminal-api` and `readline-api` transitively. `terminal-tty` provides local terminal support.

### Simple Example

Here's a simple example to get you started:

```java
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineBuilder;
import org.aesh.terminal.tty.TerminalConnection;

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

## Terminal Colors

Æsh Readline provides comprehensive terminal color support with automatic theme detection and a fluent API for styled output.

### Color Detection

Automatically detect the terminal's color capabilities and theme:

```java
import org.aesh.terminal.utils.TerminalColorCapability;

// Quick detection from environment variables
TerminalColorCapability cap = TerminalColorCapability.detectFromEnvironment();

// Check theme and color depth
if (cap.getTheme().isDark()) {
    // Use light colors for dark backgrounds
}

if (cap.getColorDepth().supportsTrueColor()) {
    // Terminal supports 24-bit RGB colors
}
```

### ANSIBuilder

Use `ANSIBuilder` for fluent, theme-aware terminal styling:

```java
import org.aesh.terminal.utils.ANSIBuilder;

// Basic usage
String output = ANSIBuilder.builder()
    .bold("Title: ").append("Some text")
    .toString();

// Theme-aware semantic colors
TerminalColorCapability cap = TerminalColorCapability.detectFromEnvironment();
ANSIBuilder builder = ANSIBuilder.builder(cap);

String logLine = builder
    .timestamp("10:30:45").append(" ")
    .error("[ERROR]").append(" ")
    .append("Connection failed")
    .toLine();
```

### Semantic Colors

Theme-aware colors for log levels that automatically adjust to light/dark terminals:

```java
ANSIBuilder builder = ANSIBuilder.builder(cap);

// Log level colors (most to least prominent)
builder.error("Error message");      // Red
builder.warning("Warning message");  // Yellow
builder.success("Success message");  // Green (same as INFO level)
builder.info("Info message");        // Blue
builder.debug("Debug message");      // White/Gray (subdued)
builder.trace("Trace message");      // Gray (least prominent)

// Other semantic colors
builder.timestamp("10:30:45");       // Cyan
builder.message("Highlighted");      // Magenta
```

### Extended Colors

Support for 256-color palette and 24-bit true color:

```java
// 256-color palette
builder.color256(208).append("Orange text");
builder.bg256(17).append("Blue background");

// True color RGB
builder.rgb(255, 87, 51).append("Custom color");
builder.bgRgb(30, 30, 30).append("Dark background");

// Hex colors
builder.hex("#FF5733").append("Coral text");
builder.bgHex("#1E1E1E").append("Dark background");
```

### Custom Color Overrides

Override semantic colors with custom values:

```java
ANSIBuilder builder = ANSIBuilder.builder()
    .errorCode(196)              // 256-color red
    .successHex("#00FF00")       // Hex green
    .timestampRgb(100, 149, 237); // RGB cornflower blue

builder.error("Custom error color");
```

## Terminal Images

Display images in terminals that support graphics protocols (Kitty, iTerm2, Sixel).

### Basic Usage

```java
import org.aesh.terminal.utils.TerminalImageBuilder;
import java.nio.file.Path;

// Display an image
String imageData = TerminalImageBuilder.builder()
    .file(Path.of("image.png"))
    .width(40)
    .height(20)
    .build();

connection.write(imageData);
```

### Protocol Detection

```java
// Check which protocol the terminal supports
TerminalGraphicsCapability graphics = TerminalGraphicsDetector.detect(connection);

if (graphics.supportsKitty()) {
    // Use Kitty graphics protocol
} else if (graphics.supportsIterm()) {
    // Use iTerm2 inline images
} else if (graphics.supportsSixel()) {
    // Use Sixel graphics
}
```

## Documentation

For detailed documentation, visit the [Æsh Readline Documentation](https://aeshell.github.io/docs/readline/).

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
