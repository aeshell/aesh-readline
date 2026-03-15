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
package org.aesh.readline;

import java.util.Arrays;
import java.util.List;

import org.aesh.terminal.formatting.TerminalCharacter;
import org.aesh.terminal.formatting.TerminalString;
import org.aesh.terminal.utils.Parser;

/**
 * The Prompt:
 * If created with a String value that value will be the prompt
 * with the default back and foreground colors.
 * If created with TerminalCharacters the colors can be set individually.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class Prompt {

    private int[] prompt;
    private Character mask;
    private int[] ansiString;

    /**
     * Creates an empty prompt.
     */
    public Prompt() {
        this.prompt = new int[] {};
    }

    /**
     * Creates a prompt with the specified text.
     *
     * @param prompt the prompt text, or null for an empty prompt
     */
    public Prompt(String prompt) {
        if (prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[] {};
    }

    /**
     * Creates a copy of the specified prompt.
     *
     * @param prompt the prompt to copy
     */
    public Prompt(Prompt prompt) {
        this.prompt = prompt.prompt.clone();
        this.mask = prompt.mask;
        this.ansiString = prompt.ansiString.clone();
    }

    /**
     * Creates a prompt with the specified text and ANSI formatting.
     *
     * @param prompt the prompt text for length calculation, or null for an empty prompt
     * @param ansiString the ANSI-formatted string for display
     */
    public Prompt(String prompt, String ansiString) {
        if (prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[] {};
        this.ansiString = Parser.toCodePoints(ansiString);
    }

    /**
     * Creates a prompt with the specified text and input masking character.
     *
     * @param prompt the prompt text, or null for an empty prompt
     * @param mask the character to use for masking input (e.g., for passwords)
     */
    public Prompt(String prompt, Character mask) {
        if (prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[] {};
        this.mask = mask;
    }

    /**
     * Creates a prompt with the specified text, ANSI formatting, and input masking character.
     *
     * @param prompt the prompt text for length calculation, or null for an empty prompt
     * @param ansiString the ANSI-formatted string for display
     * @param mask the character to use for masking input (e.g., for passwords)
     */
    public Prompt(String prompt, String ansiString, Character mask) {
        if (prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[] {};
        this.ansiString = Parser.toCodePoints(ansiString);
        this.mask = mask;
    }

    /**
     * Creates a prompt with the specified code points and input masking character.
     *
     * @param prompt the prompt as an array of Unicode code points, or null for an empty prompt
     * @param mask the character to use for masking input (e.g., for passwords)
     */
    public Prompt(int[] prompt, Character mask) {
        if (prompt != null) {
            this.prompt = new int[prompt.length];
            System.arraycopy(prompt, 0, this.prompt, 0, prompt.length);
        } else
            this.prompt = new int[] {};
        this.mask = mask;
    }

    /**
     * Creates a prompt from a TerminalString with ANSI formatting.
     *
     * @param terminalString the terminal string containing the prompt, or null for an empty prompt
     */
    public Prompt(TerminalString terminalString) {
        if (terminalString != null) {
            ansiString = Parser.toCodePoints(terminalString.toString());
            this.prompt = Parser.toCodePoints(terminalString.getCharacters());
        } else
            this.prompt = new int[] {};
    }

    /**
     * Creates a prompt from a list of terminal characters with individual formatting.
     *
     * @param characters the list of terminal characters that make up the prompt
     */
    public Prompt(List<TerminalCharacter> characters) {
        generateOutString(characters);
    }

    /**
     * Creates a prompt from a list of terminal characters with individual formatting
     * and an input masking character.
     *
     * @param characters the list of terminal characters that make up the prompt
     * @param mask the character to use for masking input (e.g., for passwords)
     */
    public Prompt(List<TerminalCharacter> characters, Character mask) {
        this.mask = mask;
        generateOutString(characters);
    }

    /**
     * Generates the prompt and ANSI strings from a list of terminal characters.
     *
     * @param chars the list of terminal characters to process
     */
    private void generateOutString(List<TerminalCharacter> chars) {
        StringBuilder promptBuilder = new StringBuilder();
        StringBuilder builder = new StringBuilder();
        TerminalCharacter prev = null;
        for (TerminalCharacter c : chars) {
            if (prev == null)
                builder.append(c.toString());
            else
                builder.append(c.toString(prev));
            prev = c;
            promptBuilder.append(c.getCharacter());
        }
        ansiString = Parser.toCodePoints(builder.toString());
        this.prompt = Parser.toCodePoints(promptBuilder.toString());
    }

    /**
     * Returns the masking character used for hiding input.
     *
     * @return the mask character, or null if masking is not enabled
     */
    public Character getMask() {
        return mask;
    }

    /**
     * Checks if input masking is enabled.
     *
     * @return true if a mask character is set, false otherwise
     */
    public boolean isMasking() {
        return mask != null;
    }

    /**
     * Returns the prompt as an int array of characters.
     *
     * @return the prompt characters as an int array
     */
    public int[] getPromptCharacters() {
        return prompt;
    }

    /**
     * Returns the prompt as an int array of characters.
     *
     * @return the prompt characters as an int array
     * @deprecated Use {@link #getPromptCharacters()} instead. The name is misleading since this returns int[], not String.
     */
    @Deprecated
    public int[] getPromptAsString() {
        return getPromptCharacters();
    }

    /**
     * Returns the length of the prompt in characters.
     *
     * @return the prompt length
     */
    public int getLength() {
        return prompt.length;
    }

    /**
     * Checks if the prompt has ANSI formatting.
     *
     * @return true if ANSI formatting is present, false otherwise
     */
    public boolean hasANSI() {
        return ansiString != null;
    }

    /**
     * Returns the ANSI-formatted prompt string, or the plain prompt if no ANSI formatting exists.
     *
     * @return the ANSI string as code points, or the plain prompt if no ANSI formatting
     */
    public int[] getANSI() {
        if (ansiString == null)
            return prompt;
        return ansiString;
    }

    /**
     * Creates a copy of this prompt.
     *
     * @return a new Prompt instance with the same values
     */
    public Prompt copy() {
        return new Prompt(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Prompt))
            return false;

        Prompt prompt1 = (Prompt) o;

        if (ansiString != null ? !Arrays.equals(ansiString, prompt1.ansiString) : prompt1.ansiString != null)
            return false;

        if (mask != null ? !mask.equals(prompt1.mask) : prompt1.mask != null)
            return false;

        return Arrays.equals(prompt, prompt1.prompt);
    }

    @Override
    public int hashCode() {
        int result = ansiString != null ? Arrays.hashCode(ansiString) : 0;
        result = 31 * result + Arrays.hashCode(prompt);
        result = 31 * result + (mask != null ? mask.hashCode() : 0);
        return result;
    }

    /**
     * Creates a new PromptBuilder for constructing Prompt instances.
     *
     * @return a new PromptBuilder
     */
    public static PromptBuilder builder() {
        return new PromptBuilder();
    }

    /**
     * Builder for creating {@link Prompt} instances with a fluent API.
     * <p>
     * The builder supports all the input combinations that the Prompt constructors accept.
     * When multiple prompt sources are set, the precedence is (highest to lowest):
     * <ol>
     * <li>{@code characters} (List&lt;TerminalCharacter&gt;)</li>
     * <li>{@code terminalString} (TerminalString)</li>
     * <li>{@code promptCodePoints} (int[])</li>
     * <li>{@code promptString} (String), optionally combined with {@code ansiString}</li>
     * </ol>
     *
     * <p>
     * Example usage:
     *
     * <pre>{@code
     * Prompt p = Prompt.builder()
     *         .message("$ ")
     *         .mask('*')
     *         .build();
     * }</pre>
     */
    public static class PromptBuilder {
        private String promptString;
        private String ansiString;
        private Character mask;
        private int[] promptCodePoints;
        private TerminalString terminalString;
        private List<TerminalCharacter> characters;

        private PromptBuilder() {
        }

        /**
         * Sets the prompt text.
         *
         * @param prompt the prompt text
         * @return this builder
         */
        public PromptBuilder message(String prompt) {
            this.promptString = prompt;
            return this;
        }

        /**
         * Sets the ANSI-formatted string for display.
         * This is used in combination with {@link #message(String)} to provide
         * a separate display representation with ANSI escape codes.
         *
         * @param ansiString the ANSI-formatted string
         * @return this builder
         */
        public PromptBuilder ansi(String ansiString) {
            this.ansiString = ansiString;
            return this;
        }

        /**
         * Sets the mask character for hiding user input (e.g., for passwords).
         *
         * @param mask the masking character
         * @return this builder
         */
        public PromptBuilder mask(char mask) {
            this.mask = mask;
            return this;
        }

        /**
         * Sets the prompt as an array of Unicode code points.
         *
         * @param codePoints the prompt code points
         * @return this builder
         */
        public PromptBuilder promptCodePoints(int[] codePoints) {
            this.promptCodePoints = codePoints;
            return this;
        }

        /**
         * Sets the prompt from a {@link TerminalString} with ANSI formatting.
         *
         * @param terminalString the terminal string containing the prompt
         * @return this builder
         */
        public PromptBuilder terminalString(TerminalString terminalString) {
            this.terminalString = terminalString;
            return this;
        }

        /**
         * Sets the prompt from a list of individually formatted {@link TerminalCharacter}s.
         *
         * @param characters the list of terminal characters
         * @return this builder
         */
        public PromptBuilder characters(List<TerminalCharacter> characters) {
            this.characters = characters;
            return this;
        }

        /**
         * Builds a new {@link Prompt} instance from the configured parameters.
         * <p>
         * The builder selects the appropriate constructor based on which fields have been set,
         * using the precedence order: characters, terminalString, promptCodePoints, promptString.
         *
         * @return a new Prompt instance
         */
        public Prompt build() {
            if (characters != null) {
                if (mask != null)
                    return new Prompt(characters, mask);
                return new Prompt(characters);
            }
            if (terminalString != null) {
                return new Prompt(terminalString);
            }
            if (promptCodePoints != null) {
                return new Prompt(promptCodePoints, mask);
            }
            if (ansiString != null) {
                if (mask != null)
                    return new Prompt(promptString, ansiString, mask);
                return new Prompt(promptString, ansiString);
            }
            if (mask != null) {
                return new Prompt(promptString, mask);
            }
            if (promptString != null) {
                return new Prompt(promptString);
            }
            return new Prompt();
        }
    }
}
