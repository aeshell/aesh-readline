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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for HttpTtyConnection JSON message handling and HttpDevice capabilities.
 */
public class HttpTtyConnectionTest {

    private HttpTtyConnection createConnection() {
        return new HttpTtyConnection() {
            final List<byte[]> written = new ArrayList<>();

            @Override
            protected void write(byte[] buffer) {
                written.add(buffer);
            }
        };
    }

    @Test
    public void testNotInitializedByDefault() {
        HttpTtyConnection conn = createConnection();
        assertFalse(conn.isInitialized());
    }

    @Test
    public void testInitAction() {
        HttpTtyConnection conn = createConnection();
        conn.writeToDecoder("{\"action\":\"init\",\"type\":\"vt100\",\"cols\":120,\"rows\":40}");

        assertTrue(conn.isInitialized());
        assertEquals("vt100", conn.device().type());
        assertEquals(120, conn.size().getWidth());
        assertEquals(40, conn.size().getHeight());
    }

    @Test
    public void testInitWithColorDepthAndFeatures() {
        HttpTtyConnection conn = createConnection();
        conn.writeToDecoder("{\"action\":\"init\",\"type\":\"xterm-256color\","
                + "\"colorDepth\":\"TRUE_COLOR\","
                + "\"features\":[\"UNICODE\",\"CLIPBOARD\"],"
                + "\"userAgent\":\"TestBrowser/1.0\","
                + "\"cols\":80,\"rows\":24}");

        assertTrue(conn.isInitialized());
        HttpDevice device = (HttpDevice) conn.device();
        assertEquals("xterm-256color", device.type());
        assertEquals("TRUE_COLOR", device.getReportedColorDepth());
        assertNotNull(device.getFeatures());
        assertEquals(Arrays.asList("UNICODE", "CLIPBOARD"), device.getFeatures());
        assertTrue(device.hasFeature("UNICODE"));
        assertTrue(device.hasFeature("CLIPBOARD"));
        assertFalse(device.hasFeature("NONEXISTENT"));
        assertEquals("TestBrowser/1.0", device.getUserAgent());
    }

    @Test
    public void testInitWithoutOptionalFields() {
        HttpTtyConnection conn = createConnection();
        conn.writeToDecoder("{\"action\":\"init\"}");

        assertTrue(conn.isInitialized());
        // Default type should remain
        assertEquals("xterm-256color", conn.device().type());
        // Default size should remain
        assertEquals(80, conn.size().getWidth());
        assertEquals(24, conn.size().getHeight());
    }

    @Test
    public void testResizeAction() throws Exception {
        HttpTtyConnection conn = createConnection();
        AtomicReference<Size> resizedSize = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        conn.setSizeHandler(s -> {
            resizedSize.set(s);
            latch.countDown();
        });

        conn.writeToDecoder("{\"action\":\"resize\",\"cols\":132,\"rows\":50}");

        // Size handler is dispatched to the readline executor asynchronously
        assertTrue("Size handler should fire within 5 seconds",
                latch.await(5, TimeUnit.SECONDS));
        assertNotNull(resizedSize.get());
        assertEquals(132, resizedSize.get().getWidth());
        assertEquals(50, resizedSize.get().getHeight());
        assertEquals(132, conn.size().getWidth());
        assertEquals(50, conn.size().getHeight());
    }

    @Test
    public void testResizeSameSizeDoesNotTriggerHandler() {
        HttpTtyConnection conn = createConnection();
        // Default is 80x24
        AtomicReference<Size> resizedSize = new AtomicReference<>();
        conn.setSizeHandler(resizedSize::set);

        conn.writeToDecoder("{\"action\":\"resize\",\"cols\":80,\"rows\":24}");

        assertNull("Handler should not fire for same size", resizedSize.get());
    }

    @Test
    public void testResizeInvalidDataIgnored() {
        HttpTtyConnection conn = createConnection();
        // Invalid cols/rows should be silently ignored
        conn.writeToDecoder("{\"action\":\"resize\",\"cols\":-1,\"rows\":0}");
        assertEquals(80, conn.size().getWidth());
        assertEquals(24, conn.size().getHeight());
    }

    @Test
    public void testReadActionUpdatesLastAccessedTime() throws Exception {
        HttpTtyConnection conn = createConnection();
        long before = conn.lastAccessedTime();
        Thread.sleep(10);
        conn.setStdinHandler(data -> {
        });
        conn.writeToDecoder("{\"action\":\"read\",\"data\":\"hello\"}");
        assertTrue(conn.lastAccessedTime() >= before);
    }

    @Test
    public void testInvalidJsonIgnored() {
        HttpTtyConnection conn = createConnection();
        // Should not throw
        conn.writeToDecoder("not json at all");
        assertFalse(conn.isInitialized());
    }

    @Test
    public void testUnknownActionIgnored() {
        HttpTtyConnection conn = createConnection();
        conn.writeToDecoder("{\"action\":\"unknown\"}");
        assertFalse(conn.isInitialized());
    }

    @Test
    public void testTerminalTypeHandler() {
        HttpTtyConnection conn = createConnection();
        AtomicReference<String> termType = new AtomicReference<>();
        conn.setTerminalTypeHandler(termType::set);

        conn.writeToDecoder("{\"action\":\"init\",\"type\":\"vt100\"}");
        assertEquals("vt100", termType.get());
    }

    @Test
    public void testDefaultDeviceType() {
        HttpTtyConnection conn = createConnection();
        assertEquals("xterm-256color", conn.device().type());
    }

    @Test
    public void testDeviceTypeUpdate() {
        HttpDevice device = new HttpDevice();
        assertEquals("xterm-256color", device.type());
        device.setType("vt100");
        assertEquals("vt100", device.type());
    }

    @Test
    public void testDeviceHasCapabilities() {
        HttpDevice device = new HttpDevice("xterm-256color");
        // xterm-256color should have basic capabilities
        assertNotNull(device.type());
    }

    @Test
    public void testDeviceFeatureNull() {
        HttpDevice device = new HttpDevice();
        assertFalse(device.hasFeature("ANYTHING"));
        assertNull(device.getFeatures());
    }
}
