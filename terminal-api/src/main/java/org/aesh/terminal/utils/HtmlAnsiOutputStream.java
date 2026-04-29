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
package org.aesh.terminal.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * An OutputStream that parses ANSI escape sequences from written bytes
 * and converts them to HTML with inline CSS styles.
 * <p>
 * Supports SGR (Select Graphic Rendition) parameters including:
 * <ul>
 * <li>Basic 8 foreground and background colors (codes 30-37, 40-47)</li>
 * <li>Bright foreground and background colors (codes 90-97, 100-107)</li>
 * <li>256-color extended palette (codes 38;5;n and 48;5;n)</li>
 * <li>True color RGB (codes 38;2;r;g;b and 48;2;r;g;b)</li>
 * <li>Bold, underline, italic, concealed, and other text attributes</li>
 * </ul>
 * <p>
 * HTML special characters ({@code &, <, >, "}) are escaped in the output.
 * <p>
 * Usage example:
 *
 * <pre>
 * ByteArrayOutputStream html = new ByteArrayOutputStream();
 * try (HtmlAnsiOutputStream out = new HtmlAnsiOutputStream(html)) {
 *     out.write("\u001B[1;31mError:\u001B[0m Something failed".getBytes());
 * }
 * String result = html.toString(); // "&lt;b&gt;&lt;span style="color: red;"&gt;Error:&lt;/span&gt;&lt;/b&gt; Something failed"
 * </pre>
 *
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HtmlAnsiOutputStream extends OutputStream {

    private static final String[] ANSI_COLOR_MAP = {
            "black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"
    };

    private static final String[] COLORS_256 = {
            "#000000", "#800000", "#008000", "#808000", "#000080", "#800080", "#008080", "#c0c0c0",
            "#808080", "#ff0000", "#00ff00", "#ffff00", "#0000ff", "#ff00ff", "#00ffff", "#ffffff",
            "#000000", "#00005f", "#000087", "#0000af", "#0000d7", "#0000ff", "#005f00", "#005f5f",
            "#005f87", "#005faf", "#005fd7", "#005fff", "#008700", "#00875f", "#008787", "#0087af",
            "#0087d7", "#0087ff", "#00af00", "#00af5f", "#00af87", "#00afaf", "#00afd7", "#00afff",
            "#00d700", "#00d75f", "#00d787", "#00d7af", "#00d7d7", "#00d7ff", "#00ff00", "#00ff5f",
            "#00ff87", "#00ffaf", "#00ffd7", "#00ffff", "#5f0000", "#5f005f", "#5f0087", "#5f00af",
            "#5f00d7", "#5f00ff", "#5f5f00", "#5f5f5f", "#5f5f87", "#5f5faf", "#5f5fd7", "#5f5fff",
            "#5f8700", "#5f875f", "#5f8787", "#5f87af", "#5f87d7", "#5f87ff", "#5faf00", "#5faf5f",
            "#5faf87", "#5fafaf", "#5fafd7", "#5fafff", "#5fd700", "#5fd75f", "#5fd787", "#5fd7af",
            "#5fd7d7", "#5fd7ff", "#5fff00", "#5fff5f", "#5fff87", "#5fffaf", "#5fffd7", "#5fffff",
            "#870000", "#87005f", "#870087", "#8700af", "#8700d7", "#8700ff", "#875f00", "#875f5f",
            "#875f87", "#875faf", "#875fd7", "#875fff", "#878700", "#87875f", "#878787", "#8787af",
            "#8787d7", "#8787ff", "#87af00", "#87af5f", "#87af87", "#87afaf", "#87afd7", "#87afff",
            "#87d700", "#87d75f", "#87d787", "#87d7af", "#87d7d7", "#87d7ff", "#87ff00", "#87ff5f",
            "#87ff87", "#87ffaf", "#87ffd7", "#87ffff", "#af0000", "#af005f", "#af0087", "#af00af",
            "#af00d7", "#af00ff", "#af5f00", "#af5f5f", "#af5f87", "#af5faf", "#af5fd7", "#af5fff",
            "#af8700", "#af875f", "#af8787", "#af87af", "#af87d7", "#af87ff", "#afaf00", "#afaf5f",
            "#afaf87", "#afafaf", "#afafd7", "#afafff", "#afd700", "#afd75f", "#afd787", "#afd7af",
            "#afd7d7", "#afd7ff", "#afff00", "#afff5f", "#afff87", "#afffaf", "#afffd7", "#afffff",
            "#d70000", "#d7005f", "#d70087", "#d700af", "#d700d7", "#d700ff", "#d75f00", "#d75f5f",
            "#d75f87", "#d75faf", "#d75fd7", "#d75fff", "#d78700", "#d7875f", "#d78787", "#d787af",
            "#d787d7", "#d787ff", "#d7af00", "#d7af5f", "#d7af87", "#d7afaf", "#d7afd7", "#d7afff",
            "#d7d700", "#d7d75f", "#d7d787", "#d7d7af", "#d7d7d7", "#d7d7ff", "#d7ff00", "#d7ff5f",
            "#d7ff87", "#d7ffaf", "#d7ffd7", "#d7ffff", "#ff0000", "#ff005f", "#ff0087", "#ff00af",
            "#ff00d7", "#ff00ff", "#ff5f00", "#ff5f5f", "#ff5f87", "#ff5faf", "#ff5fd7", "#ff5fff",
            "#ff8700", "#ff875f", "#ff8787", "#ff87af", "#ff87d7", "#ff87ff", "#ffaf00", "#ffaf5f",
            "#ffaf87", "#ffafaf", "#ffafd7", "#ffafff", "#ffd700", "#ffd75f", "#ffd787", "#ffd7af",
            "#ffd7d7", "#ffd7ff", "#ffff00", "#ffff5f", "#ffff87", "#ffffaf", "#ffffd7", "#ffffff",
            "#080808", "#121212", "#1c1c1c", "#262626", "#303030", "#3a3a3a", "#444444", "#4e4e4e",
            "#585858", "#606060", "#666666", "#767676", "#808080", "#8a8a8a", "#949494", "#9e9e9e",
            "#a8a8a8", "#b2b2b2", "#bcbcbc", "#c6c6c6", "#d0d0d0", "#dadada", "#e4e4e4", "#eeeeee",
    };

    private static final byte[] BYTES_QUOT = "&quot;".getBytes();
    private static final byte[] BYTES_AMP = "&amp;".getBytes();
    private static final byte[] BYTES_LT = "&lt;".getBytes();
    private static final byte[] BYTES_GT = "&gt;".getBytes();

    private final OutputStream out;
    private final ByteArrayOutputStream escBuf = new ByteArrayOutputStream();
    private final List<String> closingTags = new ArrayList<>();
    private boolean inEscape;
    private boolean inCsi;
    private boolean concealOn;

    /**
     * Constructor.
     *
     * @param out the output stream
     */
    public HtmlAnsiOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    /** Method. */
    public void write(int data) throws IOException {
        if (inCsi) {
            escBuf.write(data);
            if (data >= 0x40 && data <= 0x7E) {
                processEscape(escBuf.toByteArray());
                escBuf.reset();
                inCsi = false;
            }
        } else if (inEscape) {
            escBuf.write(data);
            if (data == '[') {
                inCsi = true;
            } else {
                // Non-CSI escape sequence, discard
                escBuf.reset();
            }
            inEscape = false;
        } else if (data == 0x1B) {
            inEscape = true;
            escBuf.reset();
            escBuf.write(data);
        } else {
            writeHtmlEscaped(data);
        }
    }

    @Override
    /** Method. */
    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(b[i] & 0xFF);
        }
    }

    @Override
    /** Method. */
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    /** Method. */
    public void close() throws IOException {
        closeAttributes();
        out.flush();
        out.close();
    }

    private void writeHtmlEscaped(int data) throws IOException {
        switch (data) {
            case '"':
                out.write(BYTES_QUOT);
                break;
            case '&':
                out.write(BYTES_AMP);
                break;
            case '<':
                out.write(BYTES_LT);
                break;
            case '>':
                out.write(BYTES_GT);
                break;
            default:
                out.write(data);
        }
    }

    private void processEscape(byte[] seq) throws IOException {
        if (seq.length < 3 || seq[1] != '[') {
            return;
        }
        char command = (char) seq[seq.length - 1];
        if (command != 'm') {
            return;
        }
        String params = new String(seq, 2, seq.length - 3);
        if (params.isEmpty()) {
            processAttributeReset();
            return;
        }
        String[] parts = params.split(";");
        for (int i = 0; i < parts.length; i++) {
            int code;
            try {
                code = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                continue;
            }
            switch (code) {
                case 0:
                    processAttributeReset();
                    break;
                case 1:
                    writeAttribute("b");
                    break;
                case 4:
                    writeAttribute("u");
                    break;
                case 7:
                    // Negative/inverse - not directly representable in HTML
                    break;
                case 8:
                    writeRaw("\u001B[8m");
                    concealOn = true;
                    break;
                case 22:
                case 24:
                    closeAttributes();
                    break;
                case 27:
                    // Negative off
                    break;
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 35:
                case 36:
                case 37:
                    writeAttribute("span style=\"color: " + ANSI_COLOR_MAP[code - 30] + ";\"");
                    break;
                case 38:
                    i = processExtendedColor(parts, i, "color");
                    break;
                case 39:
                    closeAttributes();
                    break;
                case 40:
                case 41:
                case 42:
                case 43:
                case 44:
                case 45:
                case 46:
                case 47:
                    writeAttribute("span style=\"background-color: " + ANSI_COLOR_MAP[code - 40] + ";\"");
                    break;
                case 48:
                    i = processExtendedColor(parts, i, "background-color");
                    break;
                case 49:
                    closeAttributes();
                    break;
                case 90:
                case 91:
                case 92:
                case 93:
                case 94:
                case 95:
                case 96:
                case 97:
                    writeAttribute("span style=\"color: " + ANSI_COLOR_MAP[code - 90] + ";\"");
                    break;
                case 100:
                case 101:
                case 102:
                case 103:
                case 104:
                case 105:
                case 106:
                case 107:
                    writeAttribute("span style=\"background-color: " + ANSI_COLOR_MAP[code - 100] + ";\"");
                    break;
                default:
                    break;
            }
        }
    }

    private int processExtendedColor(String[] parts, int i, String cssProperty) throws IOException {
        if (i + 1 >= parts.length) {
            return i;
        }
        int mode;
        try {
            mode = Integer.parseInt(parts[i + 1]);
        } catch (NumberFormatException e) {
            return i;
        }
        if (mode == 5 && i + 2 < parts.length) {
            try {
                int colorIndex = Integer.parseInt(parts[i + 2]);
                if (colorIndex >= 0 && colorIndex < COLORS_256.length) {
                    writeAttribute("span style=\"" + cssProperty + ": " + COLORS_256[colorIndex] + ";\"");
                }
            } catch (NumberFormatException e) {
                // ignore
            }
            return i + 2;
        } else if (mode == 2 && i + 4 < parts.length) {
            try {
                int r = Integer.parseInt(parts[i + 2]);
                int g = Integer.parseInt(parts[i + 3]);
                int b = Integer.parseInt(parts[i + 4]);
                String hex = String.format("#%02x%02x%02x", r, g, b);
                writeAttribute("span style=\"" + cssProperty + ": " + hex + ";\"");
            } catch (NumberFormatException e) {
                // ignore
            }
            return i + 4;
        }
        return i + 1;
    }

    private void processAttributeReset() throws IOException {
        if (concealOn) {
            writeRaw("\u001B[0m");
            concealOn = false;
        }
        closeAttributes();
    }

    private void writeAttribute(String tag) throws IOException {
        writeRaw("<" + tag + ">");
        closingTags.add(0, tag.split(" ", 2)[0]);
    }

    private void closeAttributes() throws IOException {
        for (String tag : closingTags) {
            writeRaw("</" + tag + ">");
        }
        closingTags.clear();
    }

    private void writeRaw(String s) throws IOException {
        out.write(s.getBytes());
    }
}
