/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;

import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Endpoint dimension lifecycle for negotiated telnet sizes (#382).
 * <p>
 * Size reports update only their own endpoint. No connection carries a
 * measured size before negotiation, and defaults are never recorded as
 * measurements.
 */
public class TelnetTtyConnectionTest {

    private static TelnetTtyConnection connection() {
        return new TelnetTtyConnection(false, false, StandardCharsets.UTF_8, conn -> {
        });
    }

    @Test
    public void testNoSizeBeforeNegotiation() {
        // Never opened: onOpen assigns the delegate, so there is no close
        // to call and no thread or stream to release.
        TelnetTtyConnection conn = connection();
        assertNull(conn.endpointContext().dimensions());
        assertEquals(EndpointContext.Source.UNKNOWN, conn.endpointContext().dimensionsSource());
    }

    @Test
    public void testSizeStaysPerConnection() {
        TelnetTtyConnection first = connection();
        TelnetTtyConnection second = connection();
        first.onSize(132, 50);
        assertEquals(new Size(132, 50), first.endpointContext().dimensions());
        assertEquals(EndpointContext.Source.NEGOTIATED, first.endpointContext().dimensionsSource());
        assertNull("another client's snapshot is unaffected",
                second.endpointContext().dimensions());
    }
}
