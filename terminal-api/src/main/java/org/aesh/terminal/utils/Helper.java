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
package org.aesh.terminal.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility methods for handling asynchronous operations with CompletableFuture.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class Helper {

    private Helper() {
        // utility class
    }

    /**
     * Create a handler that completes a future when a start operation finishes.
     * If the operation succeeds (null error), the future completes normally.
     * If the operation fails, the future completes exceptionally.
     *
     * @param fut the future to complete
     * @return a consumer that handles the completion result
     */
    public static Consumer<Throwable> startedHandler(CompletableFuture<?> fut) {
        return err -> {
            if (err == null) {
                fut.complete(null);
            } else {
                fut.completeExceptionally(err);
            }
        };
    }

    /**
     * Create a handler that completes a future when a stop operation finishes.
     * The future always completes normally, regardless of any error.
     *
     * @param fut the future to complete
     * @return a consumer that handles the completion result
     */
    public static Consumer<Throwable> stoppedHandler(CompletableFuture<?> fut) {
        return err -> {
            fut.complete(null);
        };
    }

}
