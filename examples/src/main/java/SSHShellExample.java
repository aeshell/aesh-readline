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
import org.aesh.terminal.ssh.netty.NettySshTtyBootstrap;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SSHShellExample {

    public static synchronized void main(String[] args) throws Exception {

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
