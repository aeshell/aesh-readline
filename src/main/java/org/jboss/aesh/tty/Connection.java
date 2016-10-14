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
package org.jboss.aesh.tty;

import java.util.function.Consumer;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface Connection {

    String terminalType();

    Size size();

    Consumer<Size> getSizeHandler();

    void setSizeHandler(Consumer<Size> handler);

    Consumer<Signal> getSignalHandler();

    void setSignalHandler(Consumer<Signal> handler);

    Consumer<int[]> getStdinHandler();

    void setStdinHandler(Consumer<int[]> handler);

    Consumer<int[]> stdoutHandler();

    void setCloseHandler(Consumer<Void> closeHandler);

    Consumer<Void> getCloseHandler();

    void close();

    void open();

    void openNonBlockingReader();

    boolean put(Capability capability, Object... params);

    default Connection write(String s) {
        int[] codePoints = s.codePoints().toArray();
        stdoutHandler().accept(codePoints);
        return this;
    }
}
