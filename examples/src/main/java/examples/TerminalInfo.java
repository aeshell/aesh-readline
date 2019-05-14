/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
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
package examples;

import org.aesh.readline.util.TerminalUtil;

/**
 * Show how to easily get some terminal info.
 */
public class TerminalInfo {

    public static void main(String[] args) {
        System.out.println("Terminal width: "+ TerminalUtil.terminalSize().getWidth());
        System.out.println("Terminal height: "+ TerminalUtil.terminalSize().getHeight());
        System.out.println("TerminalSize: "+ TerminalUtil.terminalSize());
        System.out.println("Terminal type: "+ TerminalUtil.terminalType());
    }

}
