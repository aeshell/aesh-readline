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
package org.aesh.terminal.endpointcheck;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.StreamConnection;
import org.aesh.terminal.tty.Size;

/** Named packaged JVM/native check; supplied facts are fixtures, not live observations. */
public final class NativeContextProbe {
    private NativeContextProbe() {
    }

    public static void main(String[] args) {
        try (StreamConnection connection = new StreamConnection(StandardCharsets.UTF_8, new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("metadata attempted input I/O");
            }
        }, new OutputStream() {
            @Override
            public void write(int value) {
                throw new AssertionError("metadata attempted output I/O");
            }
        })) {
            if (connection.endpointContext().locality() != EndpointContext.Locality.UNKNOWN
                    || connection.endpointContext().dimensions() != null) {
                throw new AssertionError("default metadata inferred host facts or fallback dimensions");
            }
            EndpointContext.Locality origin = EndpointContext.Locality.valueOf(args[0]);
            EndpointContext context = EndpointContext.builder().locality(origin)
                    .input(EndpointContext.StreamFacts.supplied(
                            EndpointContext.Truth.YES, EndpointContext.Truth.NO))
                    .dimensions(new Size(160, 50), EndpointContext.Source.CALLER).build();
            connection.setEndpointContext(context);
            if (connection.endpointContext() != context || context.locality() != origin
                    || context.input().terminal() != EndpointContext.Truth.YES
                    || context.input().nativeConsole() != EndpointContext.Truth.NO
                    || context.error().terminal() != EndpointContext.Truth.UNKNOWN
                    || !new Size(160, 50).equals(context.dimensions()) || connection.reading()) {
                throw new AssertionError("supplied context was not retained independently of I/O");
            }
            connection.close();
            connection.setEndpointContext(context);
            if (connection.endpointContext() != EndpointContext.unknown()) {
                throw new AssertionError("metadata survived or resurrected after close");
            }
        }
        System.out.println("CONTEXT-PROBE-OK origin=" + args[0] + " native="
                + (System.getProperty("org.graalvm.nativeimage.imagecode") != null));
    }
}
