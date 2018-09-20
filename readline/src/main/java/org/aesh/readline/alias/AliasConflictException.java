package org.aesh.readline.alias;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class AliasConflictException extends Exception {

    public AliasConflictException() {
    }

    public AliasConflictException(String message) {
        super(message);
    }
}
