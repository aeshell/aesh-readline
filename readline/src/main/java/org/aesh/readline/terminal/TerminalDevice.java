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
package org.aesh.readline.terminal;

import org.aesh.terminal.Device;
import org.aesh.terminal.tty.Capability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class TerminalDevice implements Device {

    private String type;
    private final Set<Capability> bools = new HashSet<>();
    private final Map<Capability, Integer> ints = new HashMap<>();
    private final Map<Capability, String> strings = new HashMap<>();

    static final Pattern A = Pattern.compile("^\\\\([0-9]{1,3})");
    static final Pattern B = Pattern.compile("^\\\\x([0-9,A-F,a-f]{1,2})");

    public TerminalDevice(String type) {
        this.type = type;
    }

    @Override
    public String type() {
        return type;
    }

    public void addCapability(Capability capability, Integer integer) {
        this.ints.put(capability, integer);
    }

    public void addAllCapabilityInts(Map<Capability,Integer> integers) {
        this.ints.putAll(integers);
    }

    public void addCapability(Capability capability) {
        bools.add(capability);
    }

    public void addAllCapabilityBooleans(Set<Capability> capabilities) {
        bools.addAll(capabilities);
    }

    public void addCapability(Capability capability, String s) {
        strings.put(capability, s);
    }

    public void addAllCapabilityStrings(Map<Capability,String> strings) {
        this.strings.putAll(strings);
    }

    @Override
    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    @Override
    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    @Override
    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }

    @Override
    public int[] getStringCapabilityAsInts(Capability capability) {
        String str = getStringCapability(capability);
        if(str != null)
            return parseKeySeq(str);
        else
            return null;
    }

    @Override public boolean puts(Consumer<int[]> output, Capability capability) {
        String str = getStringCapability(capability);
        if (str == null) {
            return false;
        }
        output.accept(parseKeySeq(str.toLowerCase()));
        return true;
    }

  static int[] parseKeySeq(String keyseq) {
    ArrayList<Integer> builder = new ArrayList<>();
    while (keyseq.length() > 0) {
      if (keyseq.startsWith("\\C-") && keyseq.length() > 3) {
        int c = (Character.toUpperCase(keyseq.charAt(3)) - '@') & 0x7F;
        builder.add(c);
        keyseq = keyseq.substring(4);
      } else if (keyseq.startsWith("\\M-") && keyseq.length() > 3) {
        int c = (Character.toUpperCase(keyseq.charAt(3)) - '@') & 0x7F;
        builder.add(27);
        builder.add(c);
        keyseq = keyseq.substring(4);
      }
      else if(keyseq.startsWith("^") && keyseq.length() > 1) {
          int c = (Character.toUpperCase(keyseq.charAt(1)) - '@') & 0x7F;
          builder.add(c);
          keyseq = keyseq.substring(2);
      }
      else if (keyseq.startsWith("\\e") || keyseq.startsWith("\\E")) {
        builder.add(27);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\\\")) {
        builder.add((int)'\\');
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\\"")) {
        builder.add((int)'"');
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\'")) {
        builder.add((int)'\'');
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\a")) {
        builder.add(7);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\b")) {
        builder.add(8);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\d")) {
        builder.add(127);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\f")) {
        builder.add(12);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\n")) {
        builder.add(10);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\r")) {
        builder.add(13);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\t")) {
        builder.add(9);
        keyseq = keyseq.substring(2);
      } else if (keyseq.startsWith("\\v")) {
        builder.add(11);
        keyseq = keyseq.substring(2);
      } else {
        Matcher matcher = A.matcher(keyseq);
        if (matcher.find()) {
          builder.add(Integer.parseInt(matcher.group(1), 8));
          keyseq = keyseq.substring(matcher.end());
        } else {
          matcher = B.matcher(keyseq);
          if (matcher.find()) {
            builder.add(Integer.parseInt(matcher.group(1), 16));
            keyseq = keyseq.substring(matcher.end());
          } else {
            builder.add((int) keyseq.charAt(0));
            keyseq = keyseq.substring(1);
          }
        }
      }
    }
    int[] f = new int[builder.size()];
    for (int i = 0;i < builder.size();i++) {
      f[i] = builder.get(i);
    }
    return f;
  }
}
