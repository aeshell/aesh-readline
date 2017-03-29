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
package org.aesh.terminal.tty;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public enum Signal {
    /**
     * Interrupt, usually caused by Ctrl-c or the ASCII code 3
     */
    INT,
    /**
     * Usually works like INT, caused by Ctrl-\
     */
    QUIT,
    /**
     * Works like INT and QUIT, caused by Ctrl-z.
     * The default action is to suspend the process.
     */
    SUSP,
    /**
     * End of file, first send end-of-file, then causes the next read to send end of file.
     * Ctrl-d by default.
     */
    EOF,
    /**
     * Wake up. This will un-suspend a stopped process.
     */
    CONT,
    /**
     *
     */
    INFO,
    /**
     * Window resize
     */
    WINCH
}
