package org.apache.sshd.util.test;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class EchoShellFactory implements ShellFactory {
    public static final EchoShellFactory INSTANCE = new EchoShellFactory();

    public EchoShellFactory() {
        super();
    }

    @Override
    public Command createShell(ChannelSession channel) {
        return new EchoShell();
    }
}
