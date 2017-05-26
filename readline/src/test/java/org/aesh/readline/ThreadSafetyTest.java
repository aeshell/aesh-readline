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

import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.utils.Config;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class ThreadSafetyTest {

    @Test
    public void testThreads() throws InterruptedException {
        Assume.assumeTrue(Config.isOSPOSIXCompatible());

        final AtomicBoolean result = new AtomicBoolean(true);
        TestReadline readline = new TestReadline();

        List<Thread> threads = new ArrayList<>();
        for (int i = 'a'; i <= 'f'; i++) {
            final char finalI = (char) i;
            threads.add(new Thread() {
                TestConnection connection = new TestConnection(readline, null, null, null, null );
                @Override
                public void run() {
                    for (int i = 0; i < 20; i++) {
                        try {
                            connection.readline();
                            for (int j = 0; j < 10; j++) {
                                Thread.yield();
                                connection.read(new byte[]{(byte) (finalI)});
                            }
                            connection.read(new byte[]{'\n'});

                            String line = connection.getLine();
                            for (int k = 0; k < line.length(); k++) {
                                if (finalI != line.charAt(k)) {
                                    result.set(false);
                                }
                            }
                        }
                        catch(IllegalStateException ise) {
                            //ignored, this will happen a lot here...
                        }
                    }
                }
            });
        }

        for (Thread th : threads) {
            th.start();
        }
        for (Thread th : threads) {
            th.join();
        }
        assertTrue(result.get());
    }
}
