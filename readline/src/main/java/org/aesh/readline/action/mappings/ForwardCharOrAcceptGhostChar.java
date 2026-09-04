/*
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
package org.aesh.readline.action.mappings;

import org.aesh.readline.InputProcessor;

/**
 * Accepts a single character from ghost text when the cursor is at the end
 * of the buffer, or moves forward by one word otherwise.
 * <p>
 * This is the Ctrl+Right binding for fish-style per-character ghost text
 * acceptance. Unlike {@link ForwardChar} (which accepts the FULL ghost
 * text on Right arrow), this action accepts only one character at a time.
 * When no ghost text is active, it falls back to forward-word behavior
 * (matching the standard Ctrl+Right convention).
 */
public class ForwardCharOrAcceptGhostChar extends MoveForwardWord {

    /**
     * Creates a new ForwardCharOrAcceptGhostChar action.
     */
    public ForwardCharOrAcceptGhostChar() {
        super();
    }

    @Override
    public String name() {
        return "forward-char-or-accept-ghost-char";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        if (inputProcessor.buffer().buffer().cursor() >= inputProcessor.buffer().buffer().length()
                && inputProcessor.buffer().ghostText() != null) {
            inputProcessor.buffer().acceptGhostTextChar();
        } else {
            // No ghost text — fall back to forward-word (standard Ctrl+Right behavior)
            super.accept(inputProcessor);
        }
    }
}
