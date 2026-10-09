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

import java.nio.charset.StandardCharsets;

import org.aesh.terminal.detect.TerminalReplyFramer;

/**
 * An immutable OSC 7501 program status report.
 * <p>
 * A report tells the terminal what a program is doing. The terminal keeps the
 * record and decides how to present it. Supporting this protocol does not
 * guarantee any particular presentation such as a desktop notification.
 * <p>
 * Every report completely replaces its addressed record. A key missing from a
 * report is missing from the record afterwards, so callers repeat metadata
 * they want retained, including app and title. An absent id addresses the
 * root record. Clearing is an operation, not a stored state, see
 * {@link #clearSequence(String)} and {@link #clearAllSequence()}.
 * <p>
 * Pairs are encoded in a fixed order: state, kind, id, app, title, progress,
 * message. Any order is valid on the wire. This fixed order keeps output
 * deterministic and testable.
 * <p>
 * Wire limits, checked in UTF-8 bytes before anything is written:
 * message at most 2048 decoded and 2732 encoded bytes, title at most 192
 * decoded and 256 encoded bytes, app 1 through 32 characters of
 * {@code [A-Za-z0-9_.+-]}, id at most 128 bytes in at most 8 segments of at
 * most 32 characters from the same set, progress an integer 0 through 100,
 * and the complete sequence at most 4096 bytes. Message and title must not
 * contain control characters ({@code U+0000} through {@code U+001F},
 * {@code U+007F}, {@code U+0080} through {@code U+009F}) or malformed text.
 * Invalid reports throw instead of truncating.
 */
public final class ProgramStatus {

    /** Report states. */
    public enum State {
        /** At rest, waiting for the next instruction. */
        IDLE("idle"),
        /** Running. May carry progress. */
        WORKING("working"),
        /** Finished. The result is ready and not yet seen. */
        DONE("done"),
        /** Cannot continue until the user acts. Kind says what. */
        BLOCKED("blocked"),
        /** Failed and stopped. */
        ERROR("error");

        private final String wire;

        State(String wire) {
            this.wire = wire;
        }
    }

    /** What a blocked program waits for. Only valid with state blocked. */
    public enum BlockedKind {
        /** Approval to do something. */
        PERMISSION("permission"),
        /** The user must type an answer. */
        QUESTION("question"),
        /** A login, token, or credential. */
        AUTH("auth");

        private final String wire;

        BlockedKind(String wire) {
            this.wire = wire;
        }
    }

    private static final int MAX_SEQUENCE_BYTES = 4096;
    private static final int MAX_MESSAGE_DECODED_BYTES = 2048;
    private static final int MAX_MESSAGE_ENCODED_BYTES = 2732;
    private static final int MAX_TITLE_DECODED_BYTES = 192;
    private static final int MAX_TITLE_ENCODED_BYTES = 256;
    private static final int MAX_ID_BYTES = 128;
    private static final int MAX_ID_SEGMENTS = 8;
    private static final int MAX_NAME_BYTES = 32;

    private final State state;
    private final BlockedKind kind;
    private final String id;
    private final String app;
    private final String title;
    private final Integer progress;
    private final String message;
    private final String sequence;

    private ProgramStatus(Builder builder) {
        if (builder.kind != null && builder.state != State.BLOCKED)
            throw new IllegalArgumentException("kind requires state BLOCKED");
        if (builder.progress != null) {
            if (builder.state != State.WORKING && builder.state != State.BLOCKED)
                throw new IllegalArgumentException("progress requires state WORKING or BLOCKED");
            int value = builder.progress.intValue();
            if (value < 0 || value > 100)
                throw new IllegalArgumentException("progress must be 0 through 100");
        }
        if (builder.id != null)
            requireValidId(builder.id);
        if (builder.app != null)
            requireValidName(builder.app, "app");

        String encodedTitle = null;
        if (builder.title != null)
            encodedTitle = encodeText(builder.title, "title",
                    MAX_TITLE_DECODED_BYTES, MAX_TITLE_ENCODED_BYTES);
        String encodedMessage = null;
        if (builder.message != null)
            encodedMessage = encodeText(builder.message, "message",
                    MAX_MESSAGE_DECODED_BYTES, MAX_MESSAGE_ENCODED_BYTES);

        this.state = builder.state;
        this.kind = builder.kind;
        this.id = builder.id;
        this.app = builder.app;
        this.title = builder.title;
        this.progress = builder.progress;
        this.message = builder.message;
        StringBuilder body = new StringBuilder();
        body.append("state=").append(state.wire);
        if (kind != null)
            body.append(":kind=").append(kind.wire);
        if (id != null)
            body.append(":id=").append(id);
        if (app != null)
            body.append(":app=").append(app);
        if (encodedTitle != null)
            body.append(":title=").append(encodedTitle);
        if (progress != null)
            body.append(":progress=").append(progress.intValue());
        if (encodedMessage != null)
            body.append(":msg=").append(encodedMessage);
        this.sequence = ANSI.OSC_START + ANSI.OSC_PROGRAM_STATUS + ";" + body.toString() + ANSI.ST;
        if (sequence.getBytes(StandardCharsets.UTF_8).length > MAX_SEQUENCE_BYTES)
            throw new IllegalArgumentException("report exceeds 4096 bytes");
    }

    /**
     * Start building a report.
     *
     * @param state the report state, required
     * @return a builder for the remaining fields
     */
    public static Builder builder(State state) {
        if (state == null)
            throw new NullPointerException("state is required");
        return new Builder(state);
    }

    /**
     * Build a clear operation for one record subtree.
     *
     * @param id the record id, required and validated
     * @return the clear sequence for that id and everything beneath it
     */
    public static String clearSequence(String id) {
        if (id == null || id.isEmpty())
            throw new IllegalArgumentException("clear requires a record id, use clearAllSequence to clear every record");
        requireValidId(id);
        String sequence = ANSI.OSC_START + ANSI.OSC_PROGRAM_STATUS + ";state=clear:id=" + id + ANSI.ST;
        if (sequence.getBytes(StandardCharsets.UTF_8).length > MAX_SEQUENCE_BYTES)
            throw new IllegalArgumentException("report exceeds 4096 bytes");
        return sequence;
    }

    /**
     * Build a clear operation for every record on the terminal.
     *
     * @return the sequence that removes all records
     */
    public static String clearAllSequence() {
        return ANSI.OSC_START + ANSI.OSC_PROGRAM_STATUS + ";state=clear" + ANSI.ST;
    }

    /**
     * Decide support from an accumulated query buffer holding the support
     * probe and its device-attributes fence.
     * <p>
     * The first decisive frame wins: a complete {@code 7501;?} reply means
     * supported, a device-attributes reply means unsupported, since every
     * terminal answers the fence query. A lone ACK already proves support;
     * a lone fence reply proves the probe went unanswered. Anything else
     * waits for more bytes. Unknown future reply extensions after the
     * {@code ?} are ignored.
     *
     * @param input the accumulated code points, possibly holding fragments
     * @return true when supported, false when the fence won, null to wait
     */
    public static Boolean parseSupportReply(int[] input) {
        if (input == null || input.length == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder(input.length);
        for (int i = 0; i < input.length; i++) {
            text.appendCodePoint(input[i]);
        }
        String buffer = text.toString();
        for (TerminalReplyFramer.Span span : TerminalReplyFramer.split(buffer)) {
            TerminalReplyFramer.Kind kind = span.kind;
            if (kind == TerminalReplyFramer.Kind.PARTIAL) {
                break;
            }
            if (kind == TerminalReplyFramer.Kind.OSC) {
                if (isSupportAck(buffer.substring(span.start, span.end))) {
                    return Boolean.TRUE;
                }
            } else if (kind == TerminalReplyFramer.Kind.DEVICE_ATTRIBUTES) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Check whether a complete OSC frame is a program status support reply:
     * {@code 7501;?} with optional future extensions after the {@code ?}.
     *
     * @param frame the complete frame including terminator
     * @return true for a support acknowledgement
     */
    public static boolean isSupportAck(String frame) {
        if (frame.length() < 4 || frame.charAt(0) != 27 || frame.charAt(1) != ']') {
            return false;
        }
        int end = frame.length();
        char last = frame.charAt(end - 1);
        if (last == 7) {
            end--;
        } else if (last == '\\' && end >= 2 && frame.charAt(end - 2) == 27) {
            end -= 2;
        } else {
            return false;
        }
        String body = frame.substring(2, end).trim();
        if (!body.startsWith("7501;")) {
            return false;
        }
        String rest = body.substring(5);
        return !rest.isEmpty() && rest.charAt(0) == '?';
    }

    /**
     * The report state.
     *
     * @return the report state
     */
    public State state() {
        return state;
    }

    /**
     * What a blocked program waits for.
     *
     * @return the blocked kind, or null when absent
     */
    public BlockedKind kind() {
        return kind;
    }

    /**
     * The addressed record id.
     *
     * @return the record id, or null for the root record
     */
    public String id() {
        return id;
    }

    /**
     * The stable machine-readable program name.
     *
     * @return the app name, or null when absent
     */
    public String app() {
        return app;
    }

    /**
     * The short plain-text label.
     *
     * @return the plain-text title, or null when absent
     */
    public String title() {
        return title;
    }

    /**
     * The completion percentage.
     *
     * @return the progress 0 through 100, or null when indeterminate
     */
    public Integer progress() {
        return progress;
    }

    /**
     * The one plain-text status line.
     *
     * @return the plain-text message, or null when absent
     */
    public String message() {
        return message;
    }

    /**
     * The complete escape sequence for this report, terminated by ST.
     *
     * @return the sequence to write to the terminal
     */
    public String toSequence() {
        return sequence;
    }

    /** Builder for {@link ProgramStatus}. */
    public static final class Builder {
        private final State state;
        private BlockedKind kind;
        private String id;
        private String app;
        private String title;
        private Integer progress;
        private String message;

        private Builder(State state) {
            this.state = state;
        }

        /**
         * Set what a blocked program waits for.
         *
         * @param kind what a blocked program waits for; blocked state only
         * @return this builder
         */
        public Builder kind(BlockedKind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Set the addressed record.
         *
         * @param id hierarchical record id of at most 8 segments from
         *        {@code [A-Za-z0-9_.+-]}, null addresses the root; empty or
         *        malformed ids are rejected, never silently treated as root
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Set the reporting program name.
         *
         * @param app stable machine-readable program name, 1 through 32
         *        characters of {@code [A-Za-z0-9_.+-]}
         * @return this builder
         */
        public Builder app(String app) {
            this.app = app;
            return this;
        }

        /**
         * Set the short record label.
         *
         * @param title short plain-text label, at most 192 UTF-8 bytes,
         *        no control characters
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Set the completion percentage.
         *
         * @param progress 0 through 100; working and blocked states only.
         *        Absent means indeterminate, never zero.
         * @return this builder
         */
        public Builder progress(int progress) {
            this.progress = Integer.valueOf(progress);
            return this;
        }

        /**
         * Set the one status line.
         *
         * @param message one plain-text line, at most 2048 UTF-8 bytes,
         *        no control characters
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Build the validated report.
         *
         * @return the validated report
         */
        public ProgramStatus build() {
            return new ProgramStatus(this);
        }
    }

    private static void requireValidId(String id) {
        if (id.isEmpty())
            throw new IllegalArgumentException("id must not be empty, null addresses the root");
        if (id.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES)
            throw new IllegalArgumentException("id exceeds 128 bytes");
        String[] segments = id.split("/", -1);
        if (segments.length > MAX_ID_SEGMENTS)
            throw new IllegalArgumentException("id exceeds 8 segments");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || segment.length() > MAX_NAME_BYTES)
                throw new IllegalArgumentException("id segment must be 1 through 32 characters");
            for (int j = 0; j < segment.length(); j++) {
                if (!isNameChar(segment.charAt(j)))
                    throw new IllegalArgumentException("id holds an illegal character: " + segment);
            }
        }
    }

    private static void requireValidName(String value, String field) {
        if (value.isEmpty() || value.length() > MAX_NAME_BYTES)
            throw new IllegalArgumentException(field + " must be 1 through 32 characters");
        for (int i = 0; i < value.length(); i++) {
            if (!isNameChar(value.charAt(i)))
                throw new IllegalArgumentException(field + " holds an illegal character");
        }
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '+' || c == '-';
    }

    private static String encodeText(String value, String field, int maxDecoded, int maxEncoded) {
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            if (codePoint >= 0xD800 && codePoint <= 0xDFFF)
                throw new IllegalArgumentException(field + " is malformed");
            if (codePoint <= 0x1F || codePoint == 0x7F || (codePoint >= 0x80 && codePoint <= 0x9F))
                throw new IllegalArgumentException(field + " must not contain control characters");
            i += Character.charCount(codePoint);
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxDecoded)
            throw new IllegalArgumentException(field + " exceeds its decoded size limit");
        String encoded = java.util.Base64.getEncoder().encodeToString(utf8);
        if (encoded.length() > maxEncoded)
            throw new IllegalArgumentException(field + " exceeds its encoded size limit");
        return encoded;
    }
}
