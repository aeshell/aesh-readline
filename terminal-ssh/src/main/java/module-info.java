module org.aesh.terminal.ssh {
    requires org.aesh.terminal.api;
    requires sshd.core;
    requires netty.transport;
    requires netty.handler;
    requires netty.common;
    requires java.logging;
    requires netty.buffer;
    exports org.aesh.terminal.ssh;
}