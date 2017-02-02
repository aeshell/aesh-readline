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
package org.aesh.parser;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ParsedLineIterator {

    private final ParsedLine parsedLine;
    private int word = 0;
    private int character = 0;

    public ParsedLineIterator(ParsedLine parsedLine) {
        this.parsedLine = parsedLine;
    }

    public boolean hasNextWord() {
        return parsedLine.words().size() > word;
    }

    public boolean hasNextChar() {
        return parsedLine.line().length() > character;
    }

    public ParsedWord nextParsedWord() {
        if(hasNextWord()) {
            //set correct next char
            if(parsedLine.words().size() > (word+1))
                character = parsedLine.words().get(word+1).lineIndex();
            else
                character = -1;
            return parsedLine.words().get(word++);
        }
        else
            return new ParsedWord(null, -1);
    }

    public ParsedWord prevParsedWord() {
        if(word > 0)
            return parsedLine.words().get(word-1);
        else
            return new ParsedWord(null, -1);
    }

    public String prevWord() {
        return prevParsedWord().word();
    }

    public String nextWord() {
        return nextParsedWord().word();
    }

    public char nextChar() {
        if(hasNextChar()) {
            if(hasNextWord() &&
                    character+1 >= parsedLine.words().get(word).lineIndex()+
                            parsedLine.words().get(word).word().length())
                word++;
            return parsedLine.line().charAt(character++);
        }
        return '\u0000';
    }

    public boolean finished() {
        return parsedLine.words().size() == word || parsedLine.line().length() == character;
    }

}
