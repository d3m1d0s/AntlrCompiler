package cz.university;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class SymbolTable {
    public enum Type { INT, FLOAT, BOOL, STRING, FILE }

    public static class VariableInfo {
        public final Type type;
        /** Name used in generated instructions, unique across all scopes. */
        public final String runtimeName;
        public Object value;

        public VariableInfo(Type type, String runtimeName) {
            this.type = type;
            this.runtimeName = runtimeName;
            this.value = defaultValue(type);
        }

        private Object defaultValue(Type type) {
            return switch (type) {
                case INT -> 0;
                case FLOAT -> 0.0;
                case BOOL -> false;
                case STRING -> "";
                case FILE -> null;
            };
        }
    }

    private final Deque<Map<String, VariableInfo>> scopes = new ArrayDeque<>();
    private final Map<String, Integer> nameCounters = new HashMap<>();

    // Identifier occurrences resolved during type checking. The code generator
    // runs after the scopes have been closed again, so it cannot resolve names
    // itself; it looks up what the checker recorded here instead.
    private final Map<Token, VariableInfo> resolutions = new IdentityHashMap<>();

    public SymbolTable() {
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

    public Type getType(String name) throws TypeException {
        return resolve(name).type;
    }

    public Object getValue(String name) throws TypeException {
        return resolve(name).value;
    }

    public void setValue(String name, Object value) throws TypeException {
        resolve(name).value = value;
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

    // The first declaration of a name keeps it; later ones in other scopes get
    // a numbered variant. The dot cannot appear in source identifiers, so the
    // generated names never collide with user variables.
    private String runtimeName(String name) {
        Integer used = nameCounters.get(name);
        if (used == null) {
            nameCounters.put(name, 1);
            return name;
        }
        nameCounters.put(name, used + 1);
        return name + "." + used;
    }

    public Type getExprType(ParserRuleContext ctx) {
        if (ctx instanceof cz.university.LanguageParser.IdExprContext idCtx) {
            return resolved(idCtx.IDENTIFIER().getSymbol()).type;
        }
        if (ctx instanceof cz.university.LanguageParser.IntExprContext) return Type.INT;
        if (ctx instanceof cz.university.LanguageParser.FloatExprContext) return Type.FLOAT;
        if (ctx instanceof cz.university.LanguageParser.BoolExprContext) return Type.BOOL;
        if (ctx instanceof cz.university.LanguageParser.StringExprContext) return Type.STRING;
        if (ctx instanceof cz.university.LanguageParser.ParenExprContext parenCtx) {
            return getExprType(parenCtx.expr());
        }
        if (ctx instanceof cz.university.LanguageParser.UnaryMinusExprContext unaryCtx) {
            return getExprType(unaryCtx.expr());
        }
        if (ctx instanceof cz.university.LanguageParser.FileOpenExprContext
                || ctx instanceof cz.university.LanguageParser.FileAppendExprContext) {
            return Type.FILE;
        }

        if (ctx instanceof cz.university.LanguageParser.AdditiveExprContext addCtx) {
            Type left = getExprType(addCtx.left);
            Type right = getExprType(addCtx.right);
            if (left == Type.FLOAT || right == Type.FLOAT) return Type.FLOAT;
            if (left == Type.INT && right == Type.INT) return Type.INT;
            if (left == Type.STRING && right == Type.STRING) return Type.STRING;
            throw new RuntimeException("Cannot infer type for additive expr: " + ctx.getText());
        }

        if (ctx instanceof cz.university.LanguageParser.MultiplicativeExprContext mulCtx) {
            Type left = getExprType(mulCtx.left);
            Type right = getExprType(mulCtx.right);
            if (left == Type.FLOAT || right == Type.FLOAT) return Type.FLOAT;
            if (left == Type.INT && right == Type.INT) return Type.INT;
            throw new RuntimeException("Cannot infer type for multiplicative expr: " + ctx.getText());
        }

        if (ctx instanceof cz.university.LanguageParser.AssignExprContext assignCtx) {
            return resolved(assignCtx.left).type;
        }

        if (ctx instanceof cz.university.LanguageParser.EqualityExprContext
                || ctx instanceof cz.university.LanguageParser.RelationalExprContext
                || ctx instanceof cz.university.LanguageParser.AndExprContext
                || ctx instanceof cz.university.LanguageParser.OrExprContext
                || ctx instanceof cz.university.LanguageParser.NotExprContext) {
            return Type.BOOL;
        }

        throw new RuntimeException("Cannot statically infer type of expression: " + ctx.getText());
    }
}
