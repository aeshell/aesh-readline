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
package org.aesh.terminal.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class AsyncAuthTest extends AsyncAuthTestBase {

  protected boolean authenticate() throws Exception {

    JSch jsch = new JSch();
    Session session;
    ChannelShell channel;

    session = jsch.getSession("whatever", "localhost", 5000);
    session.setPassword("whocares");
    session.setUserInfo(new UserInfo() {
      @Override
      public String getPassphrase() {
        return null;
      }

      @Override
      public String getPassword() {
        return null;
      }

      @Override
      public boolean promptPassword(String s) {
        return false;
      }

      @Override
      public boolean promptPassphrase(String s) {
        return false;
      }

      @Override
      public boolean promptYesNo(String s) {
        return true;
      } // Accept all server keys

      @Override
      public void showMessage(String s) {
      }
    });
    try {
      session.connect();
    } catch (JSchException e) {
      if (e.getMessage().equals("Auth cancel")) {
        return false;
      } else {
        throw e;
      }
    }
    channel = (ChannelShell) session.openChannel("shell");
    channel.connect();

    if (channel != null) {
      try { channel.disconnect(); } catch (Exception ignore) {}
    }
    if (session != null) {
      try { session.disconnect(); } catch (Exception ignore) {}
    }

    return true;
  }
}
