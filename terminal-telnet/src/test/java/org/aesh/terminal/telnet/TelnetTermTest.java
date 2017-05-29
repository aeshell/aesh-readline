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
package org.aesh.terminal.telnet;

import org.apache.commons.net.telnet.WindowSizeOptionHandler;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TelnetTermTest extends TelnetTestBase {

  @Test
  public void testSizeHandler() throws Exception {
    final CountDownLatch latch1 = new CountDownLatch(1);
    final CountDownLatch latch2 = new CountDownLatch(1);
    server.start(() -> {
      final AtomicInteger count = new AtomicInteger();
      return new TelnetTtyConnection(false, false, StandardCharsets.UTF_8, conn -> {
        conn.setSizeHandler(size -> {
          switch (count.getAndIncrement()) {
            case 0:
              assertEquals(20, size.getWidth());
              assertEquals(10, size.getHeight());
              latch1.countDown();
              break;
            case 1:
              assertEquals(80, size.getWidth());
              assertEquals(24, size.getHeight());
              latch2.countDown();
              break;
            case 2:
              assertEquals(180, size.getWidth());
              assertEquals(160, size.getHeight());
              testComplete();
              break;
            default:
              fail("Was not expecting that");
          }
        });
      });
    });
    WindowSizeOptionHandler optionHandler = new WindowSizeOptionHandler(20, 10, false, false, true, false);
    client.setOptionHandler(optionHandler);
    client.connect("localhost", 4000);
    latch1.await(30, TimeUnit.SECONDS);
    client.writeDirectAndFlush(new byte[]{TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SB, 31, 0, 80, 0, 24, TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SE});
    latch2.await(30, TimeUnit.SECONDS);
    client.writeDirectAndFlush(new byte[]{TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SB, 31, 0, (byte) 180, 0, (byte) 160, TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SE});
    await();
  }
}
