/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import org.aesh.terminal.ssh.netty.NettySshTtyBootstrap;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SSHShellExample {

    public static void main(String[] args) throws Exception {

         AbstractGeneratorHostKeyProvider hostKeyProvider =
            new SimpleGeneratorHostKeyProvider(     new File("hostkey.ser").toPath());
         hostKeyProvider.setAlgorithm("RSA");
        NettySshTtyBootstrap bootstrap = new NettySshTtyBootstrap().
                setPort(5000).
                setHost("localhost")
                .setKeyPairProvider(hostKeyProvider)
                //.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("/tmp/mysample").toPath()))

                ;
        bootstrap.start(new ShellExample()).get(10, TimeUnit.SECONDS);
        System.out.println("SSH started on localhost:5000");
        SSHShellExample.class.wait();
    }
}
