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

import java.io.File;
import java.util.function.Consumer;

import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.completion.SimpleCompletionHandler;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.history.FileHistory;
import org.aesh.readline.history.History;
import org.aesh.readline.history.InMemoryHistory;

/**
 * Builder for creating and configuring Readline instances.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ReadlineBuilder {

    private EditMode editMode;
    private History history;
    private CompletionHandler completionHandler;
    private int historySize = 50;
    private String historyFile;
    private boolean enableHistory = true;

    /**
     * Creates a new ReadlineBuilder instance.
     *
     * @return a new builder instance
     */
    public static ReadlineBuilder builder() {
        return new ReadlineBuilder();
    }

    /**
     * Private constructor to enforce use of the builder() factory method.
     */
    private ReadlineBuilder() {
    }

    /**
     * Applies a consumer function to this builder and returns the builder for chaining.
     *
     * @param consumer the consumer function to apply
     * @return this builder instance
     */
    private ReadlineBuilder apply(Consumer<ReadlineBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Sets the edit mode for the Readline instance.
     *
     * @param editMode the edit mode to use (e.g., Emacs or Vi mode)
     * @return this builder instance
     */
    public ReadlineBuilder editMode(EditMode editMode) {
        return apply(c -> c.editMode = editMode);
    }

    /**
     * Sets the history implementation for the Readline instance.
     *
     * @param history the history implementation to use
     * @return this builder instance
     */
    public ReadlineBuilder history(History history) {
        return apply(c -> c.history = history);
    }

    /**
     * Enables or disables history tracking.
     *
     * @param enableHistory true to enable history, false to disable
     * @return this builder instance
     */
    public ReadlineBuilder enableHistory(boolean enableHistory) {
        return apply(c -> c.enableHistory = enableHistory);
    }

    /**
     * Sets the maximum number of history entries to retain.
     *
     * @param historySize the maximum history size
     * @return this builder instance
     */
    public ReadlineBuilder historySize(int historySize) {
        return apply(c -> c.historySize = historySize);
    }

    /**
     * Sets the file path for persistent history storage.
     *
     * @param historyFile the path to the history file
     * @return this builder instance
     */
    public ReadlineBuilder historyFile(String historyFile) {
        return apply(c -> c.historyFile = historyFile);
    }

    /**
     * Sets the completion handler for tab completion.
     *
     * @param completionHandler the completion handler to use
     * @return this builder instance
     */
    public ReadlineBuilder completionHandler(CompletionHandler completionHandler) {
        return apply(c -> c.completionHandler = completionHandler);
    }

    /**
     * Builds and returns a configured Readline instance.
     * If no edit mode is specified, a default one is created.
     * If history is enabled but not specified, an in-memory or file-based history is created.
     * If no completion handler is specified, a SimpleCompletionHandler is used.
     *
     * @return a new configured Readline instance
     */
    public Readline build() {
        if (editMode == null)
            editMode = EditModeBuilder.builder().build();
        if (!enableHistory) {
            history = null;
        } else if (history == null) {
            if (historyFile == null || !new File(historyFile).isFile())
                history = new InMemoryHistory(historySize);
            else
                history = new FileHistory(new File(historyFile), historySize);
        }
        if (completionHandler == null)
            completionHandler = new SimpleCompletionHandler();

        return new Readline(editMode, history, completionHandler);
    }
}
