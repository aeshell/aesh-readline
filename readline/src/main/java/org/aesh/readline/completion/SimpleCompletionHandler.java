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
package org.aesh.readline.completion;

/**
 * Simple implementation of CompletionHandler using the default CompleteOperation.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class SimpleCompletionHandler extends CompletionHandler<CompleteOperation> {

    /**
     * Creates a new SimpleCompletionHandler.
     */
    public SimpleCompletionHandler() {
        super();
    }

    /**
     * {@inheritDoc}
     *
     * @param buffer the current input buffer content
     * @param cursor the current cursor position in the buffer
     * @return a new CompleteOperationImpl instance for the given buffer and cursor
     */
    @Override
    public CompleteOperation createCompleteOperation(String buffer, int cursor) {
        return new CompleteOperationImpl(buffer, cursor);
    }
}
