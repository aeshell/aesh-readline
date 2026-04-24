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
package org.aesh.readline.alias;

import org.aesh.readline.completion.CompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.terminal.utils.Parser;

/**
 * Completion provider for alias and unalias commands.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class AliasCompletion implements Completion {

    private static final String ALIAS = "alias";
    private static final String ALIAS_SPACE = "alias ";
    private static final String UNALIAS = "unalias";
    private static final String UNALIAS_SPACE = "unalias ";
    private static final String HELP = "--help";
    private final AliasManager manager;
    private final boolean includeAliasInCompletion;

    /**
     * Creates a new AliasCompletion with the specified alias manager.
     * By default, alias and unalias commands are included in completion suggestions.
     *
     * @param manager the alias manager used to retrieve alias names for completion
     */
    public AliasCompletion(AliasManager manager) {
        this.manager = manager;
        this.includeAliasInCompletion = true;
    }

    /**
     * Creates a new AliasCompletion with the specified alias manager and completion behavior.
     *
     * @param manager the alias manager used to retrieve alias names for completion
     * @param includeAliasInCompletion if true, include alias and unalias commands in completion;
     *        if false, only provide alias name completions
     */
    public AliasCompletion(AliasManager manager, boolean includeAliasInCompletion) {
        this.manager = manager;
        this.includeAliasInCompletion = includeAliasInCompletion;
    }

    /**
     * Provides completion candidates for alias-related commands.
     * This method handles completion for:
     * <ul>
     * <li>Alias names matching the current input buffer</li>
     * <li>The 'alias' and 'unalias' commands (if enabled)</li>
     * <li>Arguments to 'alias' and 'unalias' commands</li>
     * <li>The '--help' option for alias commands</li>
     * </ul>
     *
     * @param completeOperation the completion operation containing the current input
     *        and to which completion candidates will be added
     */
    @Override
    public void complete(CompleteOperation completeOperation) {
        completeOperation.addCompletionCandidates(manager.findAllMatchingNames(completeOperation.getBuffer()));

        //only includeAliasIncCompletion if we're running in pure readline, not if we're running in æsh
        if (includeAliasInCompletion) {
            if (completeOperation.getBuffer() == null || completeOperation.getBuffer().isEmpty()) {
                completeOperation.addCompletionCandidate(ALIAS);
                completeOperation.addCompletionCandidate(UNALIAS);
            } else if (ALIAS.startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate(ALIAS);
            else if (UNALIAS.startsWith(completeOperation.getBuffer()))
                completeOperation.addCompletionCandidate(UNALIAS);
            else if (completeOperation.getBuffer().equals(ALIAS_SPACE) ||
                    completeOperation.getBuffer().equals(UNALIAS_SPACE)) {
                completeOperation.addCompletionCandidates(manager.getAllNames());
                completeOperation.addCompletionCandidate(HELP);
                completeOperation.setOffset(completeOperation.getCursor());
            } else if (completeOperation.getBuffer().startsWith(ALIAS_SPACE) ||
                    completeOperation.getBuffer().startsWith(UNALIAS_SPACE)) {
                String word = Parser.findWordClosestToCursor(
                        completeOperation.getBuffer(), completeOperation.getCursor());
                completeOperation.addCompletionCandidates(manager.findAllMatchingNames(word));
                if (!word.isEmpty() && HELP.startsWith(word)) {
                    completeOperation.addCompletionCandidate(HELP);
                }
                completeOperation.setOffset(completeOperation.getCursor() - word.length());
            }
        }
    }

}
