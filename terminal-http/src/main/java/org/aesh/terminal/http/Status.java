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
package org.aesh.terminal.http;

/**
 * Represents the lifecycle status of a terminal process.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public enum Status {

    /** Initial state before the process has started. */
    NEW(false),
    /** The process is currently executing. */
    RUNNING(false),
    /** The process has completed successfully. */
    COMPLETED(true),
    /** The process has failed. */
    FAILED(true),
    /** The process was interrupted before completion. */
    INTERRUPTED(true);

    private final boolean final_;

    Status(boolean finalFlag) {
        this.final_ = finalFlag;
    }

    /**
     * Checks whether this status represents a final state where no further status changes will occur.
     *
     * @return true when master won't wait anymore for the process to end, the process may have terminated or not.
     */
    public boolean isFinal() {
        return final_;
    }

}
