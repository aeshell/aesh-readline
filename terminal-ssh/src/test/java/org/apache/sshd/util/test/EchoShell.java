package org.apache.sshd.util.test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class EchoShell extends CommandExecutionHelper {
    public EchoShell() {
        super();
    }

    @Override
    protected boolean handleCommandLine(String command) throws Exception {
        OutputStream out = getOutputStream();
        out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        return !"exit".equals(command);

    }
}