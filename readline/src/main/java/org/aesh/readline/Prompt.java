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

import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.util.Parser;
import org.aesh.readline.terminal.formatting.TerminalCharacter;

import java.util.Arrays;
import java.util.List;

/**
 * The Prompt:
 * If created with a String value that value will be the prompt
 * with the default back and foreground colors.
 * If created with TerminalCharacters the colors can be set individually.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Prompt {

    private int[] prompt;
    private Character mask;
    private int[] ansiString;

    public Prompt(String prompt) {
        if(prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[]{};
    }

    public Prompt(Prompt prompt) {
        this.prompt = prompt.prompt.clone();
        this.mask = prompt.mask;
        this.ansiString = prompt.ansiString.clone();
    }

    public Prompt(String prompt, String ansiString) {
        if(prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[]{};
        this.ansiString = Parser.toCodePoints(ansiString);
    }

    public Prompt(String prompt, Character mask) {
        if(prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[]{};
        this.mask = mask;
    }

    public Prompt(String prompt, String ansiString, Character mask) {
        if(prompt != null)
            this.prompt = Parser.toCodePoints(prompt);
        else
            this.prompt = new int[]{};
        this.ansiString = Parser.toCodePoints(ansiString);
        this.mask = mask;
    }

    public Prompt(int[] prompt, Character mask) {
        if(prompt != null) {
            this.prompt = new int[prompt.length];
            System.arraycopy(prompt, 0, this.prompt, 0, prompt.length);
        }
        else
            this.prompt = new int[]{};
        this.mask = mask;
    }

    public Prompt(TerminalString terminalString) {
        if(terminalString != null) {
            ansiString = Parser.toCodePoints(terminalString.toString());
            this.prompt = Parser.toCodePoints(terminalString.getCharacters());
        }
        else
            this.prompt = new int[]{};
    }

    public Prompt(List<TerminalCharacter> characters) {
        generateOutString(characters);
    }

    public Prompt(List<TerminalCharacter> characters, Character mask) {
        this.mask = mask;
        generateOutString(characters);
    }

    private void generateOutString(List<TerminalCharacter> chars) {
        StringBuilder promptBuilder = new StringBuilder();
        StringBuilder builder = new StringBuilder();
        TerminalCharacter prev = null;
        for(TerminalCharacter c : chars) {
            if(prev == null)
                builder.append(c.toString());
            else
                builder.append(c.toString(prev));
            prev = c;
            promptBuilder.append(c.getCharacter());
        }
        ansiString = Parser.toCodePoints(builder.toString());
        this.prompt = Parser.toCodePoints(promptBuilder.toString());
    }

    public Character getMask() {
        return mask;
    }

    public boolean isMasking() {
        return mask != null;
    }

    public int[] getPromptAsString() {
        return prompt;
    }

    public int getLength() {
        return prompt.length;
    }

    public boolean hasANSI() {
        return ansiString != null;
    }

    public int[] getANSI() {
        if(ansiString == null)
            return prompt;
        return ansiString;
    }

    public Prompt copy() {
        return new Prompt(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Prompt)) return false;

        Prompt prompt1 = (Prompt) o;

        if (ansiString != null ? !Arrays.equals(ansiString, prompt1.ansiString) : prompt1.ansiString != null) return false;

        if (mask != null ? !mask.equals(prompt1.mask) : prompt1.mask != null) return false;

        return Arrays.equals(prompt, prompt1.prompt);
    }

    @Override
    public int hashCode() {
        int result = ansiString != null ? Arrays.hashCode(ansiString) : 0;
        result = 31 * result + Arrays.hashCode(prompt);
        result = 31 * result + (mask != null ? mask.hashCode() : 0);
        return result;
    }
}
