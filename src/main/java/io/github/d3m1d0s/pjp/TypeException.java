package io.github.d3m1d0s.pjp;

/**
 * Checked on purpose: declaration and lookup failures are caught and collected
 * as ordinary type errors instead of crashing the visitor.
 */
public class TypeException extends Exception {
    public TypeException(String message) {
        super(message);
    }
}
