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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads compiled terminfo database entries directly, without spawning an
 * {@code infocmp} subprocess.
 * <p>
 * Reconstructs infocmp-compatible text (aliases line plus comma-separated
 * capabilities) so the existing {@link InfoCmp#parseInfoCmp} pipeline and
 * {@link Curses} consumers work unchanged. String escaping, name sorting,
 * and line wrapping follow ncurses {@code _nc_tic_expand} /
 * {@code wrap_concat} rules exactly (verified differentially against
 * infocmp output).
 * <p>
 * Capability index-to-name tables follow the ncurses {@code Caps}
 * inventory order (boolean/number/string sections; new capabilities are
 * appended at the end, so older databases parse as a matching prefix).
 * Unknown trailing indices are skipped gracefully. Any structural problem
 * returns {@code null} so callers fall through to the next source.
 */
final class TerminfoReader {

    private static final int MAGIC_LEGACY = 0x011A;
    private static final int MAGIC_EXTENDED = 0x021E;

    private static final int ABSENT_16 = 0xFFFF;
    private static final long ABSENT_32 = 0xFFFFFFFFL;

    private static final String[] DEFAULT_DIRS = {
            "/etc/terminfo", "/usr/share/terminfo", "/usr/lib/terminfo"
    };

    // Capability index-to-name tables in ncurses Caps inventory order
    // (boolean, number, string sections; new capabilities are appended at
    // the end, so older databases parse as a matching prefix and unknown
    // trailing indices are skipped by the callers below).
    private static final String[] BOOL_NAMES = {
            "bw", "am", "xsb", "xhp", "xenl", "eo", "gn", "hc",
            "km", "hs", "in", "da", "db", "mir", "msgr", "os",
            "eslok", "xt", "hz", "ul", "xon", "nxon", "mc5i", "chts",
            "nrrmc", "npc", "ndscr", "ccc", "bce", "hls", "xhpa", "crxm",
            "daisy", "xvpa", "sam", "cpix", "lpix", "OTbs", "OTns", "OTnc",
            "OTMT", "OTNL", "OTpt", "OTxr",
    };

    private static final String[] NUM_NAMES = {
            "cols", "it", "lines", "lm", "xmc", "pb", "vt", "wsl",
            "nlab", "lh", "lw", "ma", "wnum", "colors", "pairs", "ncv",
            "bufsz", "spinv", "spinh", "maddr", "mjump", "mcs", "mls", "npins",
            "orc", "orl", "orhi", "orvi", "cps", "widcs", "btns", "bitwin",
            "bitype", "OTug", "OTdC", "OTdN", "OTdB", "OTdT", "OTkn",
    };

    private static final String[] STR_NAMES = {
            "cbt", "bel", "cr", "csr", "tbc", "clear", "el", "ed",
            "hpa", "cmdch", "cup", "cud1", "home", "civis", "cub1", "mrcup",
            "cnorm", "cuf1", "ll", "cuu1", "cvvis", "dch1", "dl1", "dsl",
            "hd", "smacs", "blink", "bold", "smcup", "smdc", "dim", "smir",
            "invis", "prot", "rev", "smso", "smul", "ech", "rmacs", "sgr0",
            "rmcup", "rmdc", "rmir", "rmso", "rmul", "flash", "ff", "fsl",
            "is1", "is2", "is3", "if", "ich1", "il1", "ip", "kbs",
            "ktbc", "kclr", "kctab", "kdch1", "kdl1", "kcud1", "krmir", "kel",
            "ked", "kf0", "kf1", "kf10", "kf2", "kf3", "kf4", "kf5",
            "kf6", "kf7", "kf8", "kf9", "khome", "kich1", "kil1", "kcub1",
            "kll", "knp", "kpp", "kcuf1", "kind", "kri", "khts", "kcuu1",
            "rmkx", "smkx", "lf0", "lf1", "lf10", "lf2", "lf3", "lf4",
            "lf5", "lf6", "lf7", "lf8", "lf9", "rmm", "smm", "nel",
            "pad", "dch", "dl", "cud", "ich", "indn", "il", "cub",
            "cuf", "rin", "cuu", "pfkey", "pfloc", "pfx", "mc0", "mc4",
            "mc5", "rep", "rs1", "rs2", "rs3", "rf", "rc", "vpa",
            "sc", "ind", "ri", "sgr", "hts", "wind", "ht", "tsl",
            "uc", "hu", "iprog", "ka1", "ka3", "kb2", "kc1", "kc3",
            "mc5p", "rmp", "acsc", "pln", "kcbt", "smxon", "rmxon", "smam",
            "rmam", "xonc", "xoffc", "enacs", "smln", "rmln", "kbeg", "kcan",
            "kclo", "kcmd", "kcpy", "kcrt", "kend", "kent", "kext", "kfnd",
            "khlp", "kmrk", "kmsg", "kmov", "knxt", "kopn", "kopt", "kprv",
            "kprt", "krdo", "kref", "krfr", "krpl", "krst", "kres", "ksav",
            "kspd", "kund", "kBEG", "kCAN", "kCMD", "kCPY", "kCRT", "kDC",
            "kDL", "kslt", "kEND", "kEOL", "kEXT", "kFND", "kHLP", "kHOM",
            "kIC", "kLFT", "kMSG", "kMOV", "kNXT", "kOPT", "kPRV", "kPRT",
            "kRDO", "kRPL", "kRIT", "kRES", "kSAV", "kSPD", "kUND", "rfi",
            "kf11", "kf12", "kf13", "kf14", "kf15", "kf16", "kf17", "kf18",
            "kf19", "kf20", "kf21", "kf22", "kf23", "kf24", "kf25", "kf26",
            "kf27", "kf28", "kf29", "kf30", "kf31", "kf32", "kf33", "kf34",
            "kf35", "kf36", "kf37", "kf38", "kf39", "kf40", "kf41", "kf42",
            "kf43", "kf44", "kf45", "kf46", "kf47", "kf48", "kf49", "kf50",
            "kf51", "kf52", "kf53", "kf54", "kf55", "kf56", "kf57", "kf58",
            "kf59", "kf60", "kf61", "kf62", "kf63", "el1", "mgc", "smgl",
            "smgr", "fln", "sclk", "dclk", "rmclk", "cwin", "wingo", "hup",
            "dial", "qdial", "tone", "pulse", "hook", "pause", "wait", "u0",
            "u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8",
            "u9", "op", "oc", "initc", "initp", "scp", "setf", "setb",
            "cpi", "lpi", "chr", "cvr", "defc", "swidm", "sdrfq", "sitm",
            "slm", "smicm", "snlq", "snrmq", "sshm", "ssubm", "ssupm", "sum",
            "rwidm", "ritm", "rlm", "rmicm", "rshm", "rsubm", "rsupm", "rum",
            "mhpa", "mcud1", "mcub1", "mcuf1", "mvpa", "mcuu1", "porder", "mcud",
            "mcub", "mcuf", "mcuu", "scs", "smgb", "smgbp", "smglp", "smgrp",
            "smgt", "smgtp", "sbim", "scsd", "rbim", "rcsd", "subcs", "supcs",
            "docr", "zerom", "csnm", "kmous", "minfo", "reqmp", "getm", "setaf",
            "setab", "pfxl", "devt", "csin", "s0ds", "s1ds", "s2ds", "s3ds",
            "smglr", "smgtb", "birep", "binel", "bicr", "colornm", "defbi", "endbi",
            "setcolor", "slines", "dispc", "smpch", "rmpch", "smsc", "rmsc", "pctrm",
            "scesc", "scesa", "ehhlm", "elhlm", "elohlm", "erhlm", "ethlm", "evhlm",
            "sgr1", "slength", "OTi2", "OTrs", "OTnl", "OTbc", "OTko", "OTma",
            "OTG2", "OTG3", "OTG1", "OTG4", "OTGR", "OTGL", "OTGU", "OTGD",
            "OTGH", "OTGV", "OTGC", "meml", "memu", "box1",
    };

    private TerminfoReader() {
    }

    /**
     * Read a compiled terminfo entry and rebuild infocmp-compatible text.
     *
     * @param terminal the terminal name (e.g. "xterm-256color")
     * @return the capabilities text, or null if not found or malformed
     */
    static String readEntry(String terminal) {
        if (terminal == null || terminal.isEmpty()) {
            return null;
        }
        char first = terminal.charAt(0);
        if (!Character.isLetterOrDigit(first)) {
            return null;
        }
        String relative = first + File.separator + terminal;
        for (String dir : candidateDirs()) {
            String text = readFile(new File(dir, relative));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static List<String> candidateDirs() {
        List<String> dirs = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home != null) {
            dirs.add(home + File.separator + ".terminfo");
        }
        String terminfo = System.getenv("TERMINFO");
        if (terminfo != null && !terminfo.isEmpty()) {
            dirs.add(terminfo);
        }
        String terminfoDirs = System.getenv("TERMINFO_DIRS");
        if (terminfoDirs != null && !terminfoDirs.isEmpty()) {
            for (String dir : terminfoDirs.split(":")) {
                if (!dir.isEmpty()) {
                    dirs.add(dir);
                }
            }
        }
        for (String dir : DEFAULT_DIRS) {
            dirs.add(dir);
        }
        return dirs;
    }

    private static String readFile(File file) {
        byte[] data;
        try {
            if (!file.isFile()) {
                return null;
            }
            data = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return null;
        }
        return parse(data);
    }

    private static String parse(byte[] data) {
        if (data.length < 12) {
            return null;
        }
        int magic = u16(data, 0);
        boolean extended;
        if (magic == MAGIC_LEGACY) {
            extended = false;
        } else if (magic == MAGIC_EXTENDED) {
            extended = true;
        } else {
            return null;
        }
        int namesSize = u16(data, 2);
        int boolCount = u16(data, 4);
        int numCount = u16(data, 6);
        int strCount = u16(data, 8);
        int strSize = u16(data, 10);
        int pos = 12;
        if (pos + namesSize > data.length) {
            return null;
        }
        String[] aliases = latin1(data, pos, namesSize).split("\0");
        pos += namesSize;
        if (pos + boolCount > data.length) {
            return null;
        }
        boolean[] bools = new boolean[boolCount];
        for (int i = 0; i < boolCount; i++) {
            bools[i] = data[pos + i] != 0;
        }
        pos += boolCount;
        if ((pos & 1) == 1) {
            pos++;
        }
        int numStep = extended ? 4 : 2;
        if (pos + numStep * numCount > data.length) {
            return null;
        }
        long[] nums = new long[numCount];
        for (int i = 0; i < numCount; i++) {
            nums[i] = extended ? u32(data, pos + 4 * i) : u16(data, pos + 2 * i);
        }
        pos += numStep * numCount;
        if (pos + 2 * strCount > data.length) {
            return null;
        }
        int[] offsets = new int[strCount];
        for (int i = 0; i < strCount; i++) {
            offsets[i] = u16(data, pos + 2 * i);
        }
        pos += 2 * strCount;
        if (pos + strSize > data.length) {
            return null;
        }
        int tableBase = pos;

        StringBuilder out = new StringBuilder();
        out.append(String.join("|", aliases)).append(',');
        // Sections stay separate and each is sorted alphabetically, matching
        // infocmp output. Within-section order is load-bearing: names that
        // map to one Capability (e.g. dl/dl1) overwrite in encounter order.
        List<List<String>> sections = new ArrayList<>();
        List<String> section = new ArrayList<>();
        collectBools(section, bools);
        sortByCapName(section);
        if (!section.isEmpty()) {
            sections.add(section);
        }
        section = new ArrayList<>();
        collectNums(section, nums, extended);
        sortByCapName(section);
        if (!section.isEmpty()) {
            sections.add(section);
        }
        section = new ArrayList<>();
        collectStrings(section, data, tableBase, strSize, offsets);
        sortByCapName(section);
        if (!section.isEmpty()) {
            sections.add(section);
        }
        emitWrapped(out, sections);
        return out.toString();
    }

    /**
     * Emit capabilities with infocmp-compatible line wrapping: each section
     * starts on a fresh indented line, and within a section a capability
     * moves to a new line when separator plus text would exceed
     * {@link #INFOCMP_WIDTH} columns. Column counts text only (separators
     * are invisible to it), matching ncurses {@code wrap_concat}.
     */
    private static final int INFOCMP_WIDTH = 60;
    private static final int INFOCMP_INDENT = 8;

    private static void emitWrapped(StringBuilder out, List<List<String>> sections) {
        out.append("\n\t");
        int column = INFOCMP_INDENT;
        boolean needSeparator = false;
        for (List<String> section : sections) {
            if (needSeparator) {
                out.append(",\n\t");
                column = INFOCMP_INDENT;
                needSeparator = false;
            }
            for (String cap : section) {
                if (needSeparator) {
                    if (column > INFOCMP_INDENT && column + 2 + cap.length() > INFOCMP_WIDTH) {
                        out.append(",\n\t");
                        column = INFOCMP_INDENT;
                    } else {
                        out.append(", ");
                    }
                }
                out.append(cap);
                column += cap.length();
                needSeparator = true;
            }
        }
        out.append(",\n");
    }

    /**
     * Sort capabilities by name (up to '#' or '='), matching infocmp order.
     * Sorting full tokens misorders prefixed names (e.g. "dl=" vs "dl1=":
     * '=' (61) sorts after '1' (49), but name "dl" sorts before "dl1").
     * Name order is load-bearing where names share a Capability.
     */
    private static void sortByCapName(List<String> caps) {
        Collections.sort(caps, CapNameComparator.INSTANCE);
    }

    private static final class CapNameComparator implements java.util.Comparator<String> {
        static final CapNameComparator INSTANCE = new CapNameComparator();

        @Override
        public int compare(String a, String b) {
            return capName(a).compareTo(capName(b));
        }
    }

    private static String capName(String token) {
        int hash = token.indexOf('#');
        int eq = token.indexOf('=');
        int end = token.length();
        if (hash >= 0) {
            end = hash;
        }
        if (eq >= 0 && eq < end) {
            end = eq;
        }
        return token.substring(0, end);
    }

    private static void collectBools(List<String> caps, boolean[] bools) {
        int limit = Math.min(bools.length, BOOL_NAMES.length);
        for (int i = 0; i < limit; i++) {
            if (bools[i]) {
                caps.add(BOOL_NAMES[i]);
            }
        }
    }

    private static void collectNums(List<String> caps, long[] nums, boolean extended) {
        int limit = Math.min(nums.length, NUM_NAMES.length);
        for (int i = 0; i < limit; i++) {
            long value = nums[i];
            if (value == ABSENT_16 || (extended && value == ABSENT_32)) {
                continue;
            }
            caps.add(NUM_NAMES[i] + "#" + value);
        }
    }

    private static void collectStrings(List<String> caps, byte[] data, int tableBase, int strSize,
            int[] offsets) {
        int limit = Math.min(offsets.length, STR_NAMES.length);
        for (int i = 0; i < limit; i++) {
            int offset = offsets[i];
            if (offset == ABSENT_16) {
                continue;
            }
            if (offset < 0 || offset >= strSize) {
                continue;
            }
            int end = offset;
            while (end < strSize && data[tableBase + end] != 0) {
                end++;
            }
            if (end >= strSize) {
                continue;
            }
            StringBuilder escaped = new StringBuilder();
            appendEscaped(escaped, latin1(data, tableBase + offset, end - offset));
            caps.add(STR_NAMES[i] + "=" + escaped);
        }
    }

    /**
     * Rewrite threshold matching ncurses {@code _nc_tic_expand}: octal
     * escapes recorded below are rewritten to caret form when the
     * non-octal content is shorter than this. Value derived empirically
     * against infocmp (caret at 3 non-octal chars, octal kept at 4).
     */
    private static final int MIN_TC_FIXUPS = 4;

    /**
     * Escape a raw capability value to infocmp-compatible text, following
     * ncurses {@code _nc_tic_expand} rules: {@code \E \r \n} backslash
     * forms, {@code \\ \, \^} and {@code \s} for leading/trailing spaces,
     * caret form for a control character directly followed by a digit,
     * octal {@code \ooo} otherwise (DEL always octal, high bytes always
     * permanent octal), with a rewrite pass turning octals back to carets
     * in short strings. Two deliberate deviations: {@code 0x80} emits
     * {@code \200} (ncurses emits {@code \0}, which our Curses consumer
     * cannot parse) and backslash is always doubled (ncurses skips this
     * after a caret, an edge absent from real entries).
     */
    private static void appendEscaped(StringBuilder out, String value) {
        StringBuilder tmp = new StringBuilder(value.length() + 16);
        List<Integer> octalSpans = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0x1B) {
                tmp.append("\\E");
            } else if (c == '\r') {
                tmp.append("\\r");
            } else if (c == '\n') {
                tmp.append("\\n");
            } else if (c == '\\') {
                tmp.append("\\\\");
            } else if (c == ',') {
                tmp.append("\\,");
            } else if (c == '^') {
                tmp.append("\\^");
            } else if (c == ' ' && (i == 0 || trailingSpaces(value, i))) {
                tmp.append("\\s");
            } else if (c < 0x20) {
                if (i + 1 < value.length() && isAsciiDigit(value.charAt(i + 1))) {
                    tmp.append('^').append((char) (c + '@'));
                } else {
                    if (octalSpans == null) {
                        octalSpans = new ArrayList<>();
                    }
                    octalSpans.add(tmp.length());
                    appendOctal(tmp, c);
                }
            } else if (c == 0x7F) {
                if (octalSpans == null) {
                    octalSpans = new ArrayList<>();
                }
                octalSpans.add(tmp.length());
                appendOctal(tmp, c);
            } else if (c >= 0x80) {
                appendOctal(tmp, c);
            } else {
                tmp.append(c);
            }
        }
        if (octalSpans == null || tmp.length() - 4 * octalSpans.size() >= MIN_TC_FIXUPS) {
            out.append(tmp);
            return;
        }
        for (int s = 0; s < octalSpans.size(); s++) {
            int span = octalSpans.get(s);
            int val = Integer.parseInt(tmp.substring(span + 1, span + 4), 8);
            out.append(tmp, s == 0 ? 0 : octalSpans.get(s - 1) + 4, span);
            out.append('^');
            out.append(val == 0x7F ? '?' : (char) (val + '@'));
        }
        out.append(tmp, octalSpans.get(octalSpans.size() - 1) + 4, tmp.length());
    }

    private static boolean trailingSpaces(String value, int from) {
        for (int i = from; i < value.length(); i++) {
            if (value.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static void appendOctal(StringBuilder out, int value) {
        out.append('\\');
        out.append((char) ('0' + ((value >> 6) & 7)));
        out.append((char) ('0' + ((value >> 3) & 7)));
        out.append((char) ('0' + (value & 7)));
    }

    private static int u16(byte[] data, int pos) {
        return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] data, int pos) {
        return (long) u16(data, pos) | ((long) u16(data, pos + 2) << 16);
    }

    private static String latin1(byte[] data, int pos, int len) {
        return new String(data, pos, len, StandardCharsets.ISO_8859_1);
    }
}
