package org.aesh.terminal.ssh;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.TestBase;
import org.aesh.terminal.ssh.netty.NettySshTtyBootstrap;
import org.aesh.terminal.utils.Parser;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.Test;

public class SSHTtyCommandTest {

    private static final int TIMEOUT_SECS = 5;
    private static final int COMMAND_ITERATIONS = 2;

    @Test
    public void runCommandViaSSHTest() throws Exception {
        int port = TestBase.findAvailablePort();
        NettySshTtyBootstrap bootstrap = new NettySshTtyBootstrap()
                .port(port)
                .host("localhost");

        bootstrap.start(new ShellExample()).get(10, TimeUnit.SECONDS);

        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();

            try (ClientSession session = client.connect("user", "localhost", port)
                    .verify(TIMEOUT_SECS, TimeUnit.SECONDS)
                    .getClientSession()) {
                session.addPasswordIdentity("password");
                CoreModuleProperties.PREFERRED_AUTHS.set(session, "password");
                session.auth().verify(TIMEOUT_SECS, TimeUnit.SECONDS);

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ChannelShell channel = session.createShellChannel()) {
                    channel.setOut(outputStream);
                    channel.setErr(new NoCloseOutputStream(System.err));
                    channel.open().verify(TIMEOUT_SECS, TimeUnit.SECONDS);

                    OutputStream pipedIn = channel.getInvertedIn();
                    StringBuilder expected = new StringBuilder();

                    for (int i = 0; i < COMMAND_ITERATIONS; i++) {
                        String expectedRes = "hello world " + (i + 1);
                        pipedIn.write(("echo " + expectedRes + "\n").getBytes());
                        pipedIn.flush();
                        channel.waitFor(Arrays.asList(
                                ClientChannelEvent.STDOUT_DATA,
                                ClientChannelEvent.EOF), TimeUnit.SECONDS.toMillis(2L));

                        expected.append("echo ").append(expectedRes)
                                .append(expectedRes);

                        String expectedString = expected.toString();
                        String actual = Parser.stripAwayAnsiCodes(outputStream.toString())
                                .replaceAll("[\r\n]", "");

                        assertEquals(expectedString, actual);
                    }
                }
            }
        } finally {
            bootstrap.stop();
        }
    }
}
