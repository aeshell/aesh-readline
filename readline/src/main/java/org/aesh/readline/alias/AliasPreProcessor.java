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

import java.util.Optional;
import java.util.function.Function;

/**
 * Pre-processor that expands alias names in input commands.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class AliasPreProcessor implements Function<String, Optional<String>> {

    private final AliasManager manager;

    /**
     * Creates a new AliasPreProcessor with the specified alias manager.
     *
     * @param manager the alias manager used to look up and expand aliases
     */
    public AliasPreProcessor(AliasManager manager) {
        this.manager = manager;
    }

    /**
     * Applies alias expansion to the given input string.
     * If the input matches an alias name, returns the expanded alias value.
     *
     * @param input the input string to process for alias expansion
     * @return an Optional containing the expanded alias value if found, or empty if no alias matches
     */
    @Override
    public Optional<String> apply(String input) {
        //return manager.getAliasName(input);
        return manager.getAliasName(input);
    }
}
