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
package org.jboss.aesh.readline;

import org.jboss.aesh.readline.completion.CompletionHandler;
import org.jboss.aesh.readline.completion.SimpleCompletionHandler;
import org.jboss.aesh.readline.editing.EditMode;
import org.jboss.aesh.readline.editing.EditModeBuilder;
import org.jboss.aesh.readline.history.FileHistory;
import org.jboss.aesh.readline.history.History;
import org.jboss.aesh.readline.history.InMemoryHistory;

import java.io.File;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ReadlineBuilder {

    private EditMode editMode;
    private History history;
    private CompletionHandler completionHandler;
    private int historySize = 50;
    private String historyFile;
    private boolean enableHistory = true;

    public static ReadlineBuilder builder() {
        return new ReadlineBuilder();
    }

    private ReadlineBuilder() {
    }

    public ReadlineBuilder editMode(EditMode editMode) {
        this.editMode = editMode;
        return this;
    }

    public ReadlineBuilder history(History history) {
        this.history = history;
        return this;
    }

    public ReadlineBuilder enableHistory(boolean history) {
        this.enableHistory = history;
        return this;
    }

    public ReadlineBuilder historySize(int historySize) {
        this.historySize = historySize;
        return this;
    }

     public ReadlineBuilder historyFile(String historyFile) {
        this.historyFile = historyFile;
        return this;
    }

    public ReadlineBuilder completionHandler(CompletionHandler completionHandler) {
        this.completionHandler = completionHandler;
        return this;
    }

    public Readline build() {
        if(editMode == null)
            editMode = EditModeBuilder.builder().create();
        if(!enableHistory) {
            history = null;
        }
        else if(history == null) {
            if(historyFile == null || !new File(historyFile).isFile())
                history = new InMemoryHistory(historySize);
            else
                history = new FileHistory(new File(historyFile), historySize);
        }
        if(completionHandler == null)
            completionHandler = new SimpleCompletionHandler();

       return new Readline(editMode, history, completionHandler);
    }
}
