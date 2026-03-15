/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.readline;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.history.History;
import org.aesh.terminal.Connection;

/**
 * Encapsulates all parameters for a {@link Readline#readline(ReadlineRequest)} call.
 * Use the {@link #builder()} method to create instances via the builder pattern.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * ReadlineRequest request = ReadlineRequest.builder()
 *         .connection(conn)
 *         .prompt(new Prompt("$ "))
 *         .requestHandler(line -> System.out.println("Got: " + line))
 *         .completions(myCompletions)
 *         .build();
 * readline.readline(request);
 * }</pre>
 */
public class ReadlineRequest {

    private final Connection connection;
    private final Prompt prompt;
    private final Consumer<String> requestHandler;
    private final List<Completion> completions;
    private final List<Function<String, Optional<String>>> preProcessors;
    private final History history;
    private final CursorListener cursorListener;
    private final EnumMap<ReadlineFlag, Integer> flags;

    private ReadlineRequest(Builder builder) {
        this.connection = builder.connection;
        this.prompt = builder.prompt;
        this.requestHandler = builder.requestHandler;
        this.completions = builder.completions;
        this.preProcessors = builder.preProcessors;
        this.history = builder.history;
        this.cursorListener = builder.cursorListener;
        this.flags = builder.flags;
    }

    /**
     * Creates a new builder for constructing a {@link ReadlineRequest}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the terminal connection.
     *
     * @return the connection, never null
     */
    public Connection connection() {
        return connection;
    }

    /**
     * Returns the prompt to display.
     *
     * @return the prompt, never null
     */
    public Prompt prompt() {
        return prompt;
    }

    /**
     * Returns the callback to receive the completed input line.
     *
     * @return the request handler, never null
     */
    public Consumer<String> requestHandler() {
        return requestHandler;
    }

    /**
     * Returns the list of completions for tab completion, or null if not set.
     *
     * @return the completions list, or null
     */
    public List<Completion> completions() {
        return completions;
    }

    /**
     * Returns the list of input pre-processors, or null if not set.
     *
     * @return the pre-processors list, or null
     */
    public List<Function<String, Optional<String>>> preProcessors() {
        return preProcessors;
    }

    /**
     * Returns the history instance to use, or null to use the default.
     *
     * @return the history instance, or null
     */
    public History history() {
        return history;
    }

    /**
     * Returns the cursor listener, or null if not set.
     *
     * @return the cursor listener, or null
     */
    public CursorListener cursorListener() {
        return cursorListener;
    }

    /**
     * Returns the readline flags controlling behavior.
     *
     * @return the flags map, never null
     */
    public EnumMap<ReadlineFlag, Integer> flags() {
        return flags;
    }

    /**
     * Builder for constructing {@link ReadlineRequest} instances.
     * The {@link #connection(Connection)}, {@link #prompt(Prompt)}, and
     * {@link #requestHandler(Consumer)} fields are required.
     */
    public static class Builder {
        private Connection connection;
        private Prompt prompt;
        private Consumer<String> requestHandler;
        private List<Completion> completions;
        private List<Function<String, Optional<String>>> preProcessors;
        private History history;
        private CursorListener cursorListener;
        private EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);

        private Builder() {
        }

        /**
         * Sets the terminal connection (required).
         *
         * @param connection the terminal connection
         * @return this builder
         */
        public Builder connection(Connection connection) {
            this.connection = connection;
            return this;
        }

        /**
         * Sets the prompt to display (required).
         *
         * @param prompt the prompt
         * @return this builder
         */
        public Builder prompt(Prompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the prompt to display using a string (required).
         *
         * @param prompt the prompt string
         * @return this builder
         */
        public Builder prompt(String prompt) {
            this.prompt = new Prompt(prompt);
            return this;
        }

        /**
         * Sets the callback to receive the completed input line (required).
         *
         * @param requestHandler the request handler
         * @return this builder
         */
        public Builder requestHandler(Consumer<String> requestHandler) {
            this.requestHandler = requestHandler;
            return this;
        }

        /**
         * Sets the list of completions for tab completion.
         *
         * @param completions the completions list
         * @return this builder
         */
        public Builder completions(List<Completion> completions) {
            this.completions = completions;
            return this;
        }

        /**
         * Sets the list of input pre-processors.
         *
         * @param preProcessors the pre-processors list
         * @return this builder
         */
        public Builder preProcessors(List<Function<String, Optional<String>>> preProcessors) {
            this.preProcessors = preProcessors;
            return this;
        }

        /**
         * Sets the history instance to use for this readline operation.
         *
         * @param history the history instance
         * @return this builder
         */
        public Builder history(History history) {
            this.history = history;
            return this;
        }

        /**
         * Sets the cursor listener to receive cursor movement events.
         *
         * @param cursorListener the cursor listener
         * @return this builder
         */
        public Builder cursorListener(CursorListener cursorListener) {
            this.cursorListener = cursorListener;
            return this;
        }

        /**
         * Sets the readline flags controlling behavior.
         *
         * @param flags the flags map
         * @return this builder
         */
        public Builder flags(EnumMap<ReadlineFlag, Integer> flags) {
            this.flags = flags != null ? flags : new EnumMap<>(ReadlineFlag.class);
            return this;
        }

        /**
         * Builds a new {@link ReadlineRequest} from this builder's configuration.
         *
         * @return a new ReadlineRequest instance
         * @throws NullPointerException if connection, prompt, or requestHandler is null
         */
        public ReadlineRequest build() {
            Objects.requireNonNull(connection, "connection must not be null");
            Objects.requireNonNull(prompt, "prompt must not be null");
            Objects.requireNonNull(requestHandler, "requestHandler must not be null");
            return new ReadlineRequest(this);
        }
    }
}
