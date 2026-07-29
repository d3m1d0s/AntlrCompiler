package io.github.d3m1d0s.pjp;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Replaces ANTLR's console listener on both the lexer and the parser. Prints
 * syntax errors in the type checker's "line,col: message" shape and counts
 * them so the driver can stop before type checking.
 */
public class VerboseListener extends BaseErrorListener {
    private int errorCount = 0;

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        errorCount++;
        System.err.println(line + "," + charPositionInLine + ": " + msg);
    }

    public int getErrorCount() {
        return errorCount;
    }
}
