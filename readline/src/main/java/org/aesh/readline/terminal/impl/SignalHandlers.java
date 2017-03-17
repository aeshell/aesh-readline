/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aesh.readline.terminal.impl;

import org.aesh.terminal.Terminal.SignalHandler;

/**
 *
 * @author jdenise@redhat.com
 */
public interface SignalHandlers {

    SignalHandler SIG_DFL = NativeSignalHandler.SIG_DFL;
    SignalHandler SIG_IGN = NativeSignalHandler.SIG_IGN;
}
