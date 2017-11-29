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
package org.aesh.readline.editing;

import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionEvent;
import org.aesh.readline.action.KeyAction;
import org.aesh.readline.action.mappings.ActionMapper;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.Device;
import org.aesh.terminal.tty.Capability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Emacs implements EditMode {

    private ActionEvent currentAction;

    private Map<Key,Action> actions;
    private Map<Variable,String> variables;
    private Map<KeyAction,Action> keyEventActions;

    //counting how many times eof been pressed
    private int eofCounter;
    //default value
    private int ignoreEOFSize = 0;
    private boolean ctrlX;
    private KeyAction prevKey;

    Emacs() {
        actions = new EnumMap<>(Key.class);
        variables = new EnumMap<>(Variable.class);
        keyEventActions = new HashMap<>();
    }

    protected void clearDefaultActions() {
        actions.clear();
        keyEventActions.clear();
    }

    @Override
    public void addAction(int[] input, String action) {
        Key key = Key.getKey(input);
        if(key != null)
            actions.put(key, ActionMapper.mapToAction(action));
        else
            keyEventActions.put(createKeyEvent(input), ActionMapper.mapToAction(action));
    }

    @Override
    public void remapKeysFromDevice(Device device) {
        //need to make sure we remap keys so we have the correct mapping
        remap(Key.HOME_2, device.getStringCapabilityAsInts(Capability.key_home));
        remap(Key.END_2, device.getStringCapabilityAsInts(Capability.key_end));
        remap(Key.UP, device.getStringCapabilityAsInts(Capability.key_up));
        remap(Key.DOWN, device.getStringCapabilityAsInts(Capability.key_down));
        remap(Key.LEFT, device.getStringCapabilityAsInts(Capability.key_left));
        remap(Key.RIGHT, device.getStringCapabilityAsInts(Capability.key_right));
        remap(Key.DELETE, device.getStringCapabilityAsInts(Capability.key_dc));
        remap(Key.CTRL_K, device.getStringCapabilityAsInts(Capability.key_dl));
        //remap(Key.HOME_2, device.getStringCapabilityAsInts(Capability.key_home));
    }

    @Override
    public KeyAction prevKey() {
        return prevKey;
    }

    @Override
    public void setPrevKey(KeyAction event) {
        prevKey = event;
    }

    private void remap(Key key, int[] newMapping) {
        if(newMapping != null && actions.containsKey(key) && !key.equalTo(newMapping)) {
            Action homeAction = actions.remove(key);
            addAction(newMapping, homeAction.name());
        }
    }

    public void addAction(Key input, String action) {
        actions.put(input, ActionMapper.mapToAction(action));
    }

    public Emacs addAction(Key input, Action action) {
        actions.put(input, action);
        return this;
    }

    private Action parseKeyEventActions(KeyAction event) {
        for(KeyAction key : keyEventActions.keySet()) {
            boolean isEquals = true;
            if(key.length() == event.length()) {
                for(int i=0; i<key.length() && isEquals; i++)
                    if(key.getCodePointAt(i) != event.getCodePointAt(i))
                        isEquals = false;

                if(isEquals)
                    return keyEventActions.get(key);
            }
        }
        //if we have ctrlX from the previous input
        if(ctrlX) {
            if (event.length() == 1) {
                ctrlX = false;
                KeyAction customCtrlX = new KeyAction() {
                    @Override
                    public int getCodePointAt(int index) throws IndexOutOfBoundsException {
                        if (index == 0)
                            return Key.CTRL_X.getFirstValue();
                        else
                            return event.getCodePointAt(0);
                    }

                    @Override
                    public int length() {
                        return 2;
                    }

                    @Override
                    public String name() {
                        return "Ctrl-x+" + event.name();
                    }
                };
                return parseKeyEventActions(customCtrlX);
            }
            else {
                ctrlX = false;
                return null;
            }
        }

        if(event.getCodePointAt(0) == Key.CTRL_X.getFirstValue()) {
            ctrlX = true;
        }

        return null;
    }

    @Override
    public void addVariable(Variable variable, String value) {
        variables.put(variable, value);
    }

    @Override
    public void updateIgnoreEOF(int eof) {
        ignoreEOFSize = eof;
    }

    protected void resetEOF()  {
        eofCounter = 0;
    }

    protected int getEofCounter() {
        return eofCounter;
    }

    @Override
    public Mode mode() {
        return Mode.EMACS;
    }

    @Override
    public KeyAction[] keys() {
        List<KeyAction> keys = new ArrayList<>(actions.size()+keyEventActions.size());
        keys.addAll(actions.keySet());
        keys.addAll(keyEventActions.keySet());
        return keys.toArray(new KeyAction[keys.size()]);
    }

    @Override
    public Status status() {
        return Status.EDIT;
    }

    @Override
    public void setStatus(Status status) {
        //nothing to do
    }

    @Override
    public Action parse(KeyAction event) {
        //are we already searching, it need to be processed by search action
        if(currentAction != null) {
            if(currentAction.keepFocus()) {
                currentAction.input(getAction(event), event);
                return currentAction;
            }
            else
                currentAction = null;
        }

        return getAction(event);
    }

    @Override
    public boolean isInChainedAction() {
        return currentAction != null;
    }

    @Override
    public String variableValue(Variable variable) {
        return variables.get(variable);
    }

    private Action getAction(KeyAction event) {
        Action action;
        if(event instanceof Key && actions.containsKey(event)) {
            action = actions.get(event);
        }
        else {
            action  = parseKeyEventActions(event);
        }
        if(action != null && action instanceof ActionEvent) {
            currentAction = (ActionEvent) action;
            currentAction.input(action, event);
        }
        return action;
    }
}
