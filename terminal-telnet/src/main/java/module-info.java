module org.aesh.terminal.telnet {
    requires org.aesh.terminal.api;
    requires netty.transport;
    requires netty.common;
    requires netty.handler;
    requires netty.buffer;
    exports org.aesh.terminal.telnet;
}