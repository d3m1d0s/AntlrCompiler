package io.github.d3m1d0s.pjp;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Scope stack for the type checker. Also records how each identifier
 * occurrence resolved, because the code generator runs after the scopes are
 * closed and cannot resolve names itself.
 */
public class SymbolTable {
    public enum Type { INT, FLOAT, BOOL, STRING, FILE }

    public static class VariableInfo {
        public final Type type;
        // name used in generated instructions, unique across all scopes
        public final String runtimeName;

        public VariableInfo(Type type, String runtimeName) {
            this.type = type;
            this.runtimeName = runtimeName;
        }
    }

    private final Deque<Map<String, VariableInfo>> scopes = new ArrayDeque<>();
    private final Map<String, Integer> nameCounters = new HashMap<>();

    // keyed by token identity: every occurrence of a name is its own entry
    private final Map<Token, VariableInfo> resolutions = new IdentityHashMap<>();

    public SymbolTable() {
        // the global scope, never popped
        scopes.push(new HashMap<>());
    }

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        scopes.pop();
    }

    public VariableInfo declare(String name, Type type) throws TypeException {
        if (scopes.peek().containsKey(name)) {
            throw new TypeException("Variable '" + name + "' is already declared.");
        }
        VariableInfo info = new VariableInfo(type, runtimeName(name));
        scopes.peek().put(name, info);
        return info;
    }

    /** Declares a variable in the current scope and records the resolution. */
    public Type declare(Token id, Type type) throws TypeException {
        VariableInfo info = declare(id.getText(), type);
        resolutions.put(id, info);
        return info.type;
    }

    /** Resolves a use of an identifier and records it for the code generator. */
    public Type reference(Token id) throws TypeException {
        VariableInfo info = resolve(id.getText());
        resolutions.put(id, info);
        return info.type;
    }

    /** Returns what the type checker resolved this identifier occurrence to. */
    public VariableInfo resolved(Token id) {
        VariableInfo info = resolutions.get(id);
        if (info == null) {
            throw new IllegalStateException("Identifier was never resolved: " + id.getText());
        }
        return info;
    }

    private VariableInfo resolve(String name) throws TypeException {
        for (Map<String, VariableInfo> scope : scopes) {
            VariableInfo info = scope.get(name);
            if (info != null) {
                return info;
            }
        }
        throw new TypeException("Variable '" + name + "' is not declared.");
    }

    // later declarations get a dotted suffix; '.' cannot appear in identifiers, so no collisions
    private String runtimeName(String name) {
        Integer used = nameCounters.get(name);
        if (used == null) {
            nameCounters.put(name, 1);
            return name;
        }
        nameCounters.put(name, used + 1);
        return name + "." + used;
    }

    /**
     * Re-derives the static type of an expression for the code generator. Assumes
     * the tree passed type checking, so the throws below signal compiler defects,
     * not user errors.
     */
    public Type getExprType(ParserRuleContext ctx) {
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.IdExprContext idCtx) {
            return resolved(idCtx.IDENTIFIER().getSymbol()).type;
        }
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.IntExprContext) return Type.INT;
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.FloatExprContext) return Type.FLOAT;
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.BoolExprContext) return Type.BOOL;
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.StringExprContext) return Type.STRING;
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.ParenExprContext parenCtx) {
            return getExprType(parenCtx.expr());
        }
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.UnaryMinusExprContext unaryCtx) {
            return getExprType(unaryCtx.expr());
        }
        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.FileOpenExprContext
                || ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext) {
            return Type.FILE;
        }

        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.AdditiveExprContext addCtx) {
            Type left = getExprType(addCtx.left);
            Type right = getExprType(addCtx.right);
            if (left == Type.FLOAT || right == Type.FLOAT) return Type.FLOAT;
            if (left == Type.INT && right == Type.INT) return Type.INT;
            if (left == Type.STRING && right == Type.STRING) return Type.STRING;
            throw new RuntimeException("Cannot infer type for additive expr: " + ctx.getText());
        }

        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.MultiplicativeExprContext mulCtx) {
            Type left = getExprType(mulCtx.left);
            Type right = getExprType(mulCtx.right);
            if (left == Type.FLOAT || right == Type.FLOAT) return Type.FLOAT;
            if (left == Type.INT && right == Type.INT) return Type.INT;
            throw new RuntimeException("Cannot infer type for multiplicative expr: " + ctx.getText());
        }

        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.AssignExprContext assignCtx) {
            return resolved(assignCtx.left).type;
        }

        if (ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.EqualityExprContext
                || ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.RelationalExprContext
                || ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.AndExprContext
                || ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.OrExprContext
                || ctx instanceof io.github.d3m1d0s.pjp.LanguageParser.NotExprContext) {
            return Type.BOOL;
        }

        throw new RuntimeException("Cannot statically infer type of expression: " + ctx.getText());
    }
}
