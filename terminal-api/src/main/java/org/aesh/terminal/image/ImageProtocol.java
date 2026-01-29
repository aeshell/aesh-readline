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
package org.aesh.terminal.image;

/**
 * Supported terminal image protocols.
 */
public enum ImageProtocol {
    /**
     * No image protocol supported.
     */
    NONE,

    /**
     * iTerm2 inline images protocol.
     * Supported by: iTerm2, WezTerm, Mintty, VSCode terminal, Tabby, Hyper
     * Format: OSC 1337 ; File=[args]:base64data BEL
     */
    ITERM2,

    /**
     * Kitty graphics protocol.
     * Supported by: Kitty, Konsole (partial)
     * Format: APC G [control];[payload] ST
     */
    KITTY,

    /**
     * Sixel graphics protocol.
     * Supported by: xterm, mlterm, foot, Windows Terminal, etc.
     * Not currently implemented.
     */
    SIXEL
}
