/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import org.aesh.terminal.telnet.netty.NettyTelnetTtyBootstrap;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TelnetShellExample {

    public static synchronized void main(String[] args) throws Exception {
        NettyTelnetTtyBootstrap bootstrap = new NettyTelnetTtyBootstrap().
                setHost("localhost").
                setPort(4000);
        bootstrap.start(new ShellExample()).get(10, TimeUnit.SECONDS);
        System.out.println("Telnet server started on localhost:4000");
        TelnetShellExample.class.wait();
    }
}
