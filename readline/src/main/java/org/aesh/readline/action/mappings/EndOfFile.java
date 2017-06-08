/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline.action.mappings;

import org.aesh.readline.InputProcessor;
import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.action.Action;
import org.aesh.readline.terminal.Key;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class EndOfFile implements Action {

    private int EOFCounter = 0;
    private int ignoreEOFSize = -1;

    @Override
    public String name() {
        return "eof";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        //always do this first
        if(ignoreEOFSize < 0) {
            ignoreEOFSize = inputProcessor.flags().getOrDefault(ReadlineFlag.IGNORE_EOF, 0);
        }
        //if buffer.length > 0 delete-char
        if(inputProcessor.buffer().buffer().length() > 0) {
            new DeleteChar().accept(inputProcessor);
        }
        else {
            //reset EOFCounter if prev key != ctrl-d
            if(EOFCounter > 0 && inputProcessor.editMode().prevKey() != null &&
                    inputProcessor.editMode().prevKey().getCodePointAt(0) != Key.CTRL_D.getFirstValue())
                EOFCounter = 0;

            if(ignoreEOFSize > EOFCounter)
                EOFCounter++;
            else {
                //we got a eof, close the connection and call finish
                inputProcessor.connection().close();
            }
        }

    }
}
