package io.github.d3m1d0s.pjp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TypeCheckerVisitor extends io.github.d3m1d0s.pjp.LanguageBaseVisitor<SymbolTable.Type> {

    private final SymbolTable symbolTable = new SymbolTable();
    private final List<String> errors = new ArrayList<>();
    private final Set<String> reportedUndeclared = new HashSet<>();

    public List<String> getErrors() {
        return errors;
    }

    // === Statements ===

    @Override
    public SymbolTable.Type visitDeclaration(io.github.d3m1d0s.pjp.LanguageParser.DeclarationContext ctx) {
        SymbolTable.Type declaredType = getTypeFromKeyword(ctx.primitiveType().getText());
        for (var id : ctx.variableList().IDENTIFIER()) {
            try {
                symbolTable.declare(id.getSymbol(), declaredType);
            } catch (TypeException e) {
                typeError(id.getSymbol(), e.getMessage());
            }
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitExpressionStatement(io.github.d3m1d0s.pjp.LanguageParser.ExpressionStatementContext ctx) {
        return visit(ctx.expr());
    }

    // === Expressions ===
    @Override
    public SymbolTable.Type visitEmptyStatement(io.github.d3m1d0s.pjp.LanguageParser.EmptyStatementContext ctx) {
        return null;
    }

    @Override
    public SymbolTable.Type visitFileAppendExpr(io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext ctx) {
        SymbolTable.Type leftType = visit(ctx.expr(0));
        SymbolTable.Type rightType = visit(ctx.expr(1));

        if (leftType == null || rightType == null) return null;

        if (leftType != SymbolTable.Type.FILE) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Left side of '<<' must be of type FILE.");
            return null;
        }

        if (rightType != SymbolTable.Type.INT &&
                rightType != SymbolTable.Type.FLOAT &&
                rightType != SymbolTable.Type.STRING) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Right side of '<<' must be INT, FLOAT or STRING. Got: " + rightType);
            return null;
        }

        // can be this:
        // (fname << 5) << "abc"
        return SymbolTable.Type.FILE;
    }

    @Override
    public SymbolTable.Type visitIdExpr(io.github.d3m1d0s.pjp.LanguageParser.IdExprContext ctx) {
        try {
            return symbolTable.reference(ctx.getStart());
        } catch (TypeException e) {
            reportUndeclared(ctx.getStart(), ctx.IDENTIFIER().getText(), e);
            return null;
        }
    }

    @Override
    public SymbolTable.Type visitIntExpr(io.github.d3m1d0s.pjp.LanguageParser.IntExprContext ctx) {
        try {
            Integer.parseInt(ctx.getText());
        } catch (NumberFormatException e) {
            typeError(ctx.getStart(), "Integer literal out of range: " + ctx.getText());
        }
        return SymbolTable.Type.INT;
    }

    @Override
    public SymbolTable.Type visitFloatExpr(io.github.d3m1d0s.pjp.LanguageParser.FloatExprContext ctx) {
        return SymbolTable.Type.FLOAT;
    }

    @Override
    public SymbolTable.Type visitBoolExpr(io.github.d3m1d0s.pjp.LanguageParser.BoolExprContext ctx) {
        return SymbolTable.Type.BOOL;
    }

    @Override
    public SymbolTable.Type visitStringExpr(io.github.d3m1d0s.pjp.LanguageParser.StringExprContext ctx) {
        decodeLiteral(ctx.getStart());
        return SymbolTable.Type.STRING;
    }

    @Override
    public SymbolTable.Type visitParenExpr(io.github.d3m1d0s.pjp.LanguageParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public SymbolTable.Type visitAdditiveExpr(io.github.d3m1d0s.pjp.LanguageParser.AdditiveExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));
        String op = ctx.getChild(1).getText();

        if (left == null || right == null) return null;

        if (".".equals(op)) {
            if (left == SymbolTable.Type.STRING && right == SymbolTable.Type.STRING) {
                return SymbolTable.Type.STRING;
            } else {
                Token opToken = (Token) ctx.getChild(1).getPayload();
                typeError(opToken, "String concatenation requires both operands to be strings. Got: " + left + ", " + right);
                return null;
            }
        }

        return computeBinaryNumericType(left, right, ctx);
    }

    @Override
    public SymbolTable.Type visitMultiplicativeExpr(io.github.d3m1d0s.pjp.LanguageParser.MultiplicativeExprContext ctx) {
        String op = ctx.getChild(1).getText();
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if ("%".equals(op)) {
            if (left != SymbolTable.Type.INT || right != SymbolTable.Type.INT) {
                Token opToken = (Token) ctx.getChild(1).getPayload();
                typeError(opToken, "Modulo can be used only with integers.");
            }
            return SymbolTable.Type.INT;
        }

        return computeBinaryNumericType(left, right, ctx);
    }

    @Override
    public SymbolTable.Type visitEqualityExpr(io.github.d3m1d0s.pjp.LanguageParser.EqualityExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if (left == null || right == null) return null;

        // int == float or float == int -> OK
        if ((left == SymbolTable.Type.INT && right == SymbolTable.Type.FLOAT) ||
                (left == SymbolTable.Type.FLOAT && right == SymbolTable.Type.INT)) {
            return SymbolTable.Type.BOOL;
        }

        // same-type comparison works for every value type, just not for files
        if (left == right && left != SymbolTable.Type.FILE) {
            return SymbolTable.Type.BOOL;
        }

        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Invalid types for equality: " + left + ", " + right);
        return null;
    }


    @Override
    public SymbolTable.Type visitRelationalExpr(io.github.d3m1d0s.pjp.LanguageParser.RelationalExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if (left == null || right == null) return null;

        // int < int, float < float, int < float, float < int -> OK
        if ((left == SymbolTable.Type.INT || left == SymbolTable.Type.FLOAT) &&
                (right == SymbolTable.Type.INT || right == SymbolTable.Type.FLOAT)) {
            return SymbolTable.Type.BOOL;
        }

        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Relational operators are only valid for int or float. Got: " + left + ", " + right);
        return null;
    }

    @Override
    public SymbolTable.Type visitReadStatement(io.github.d3m1d0s.pjp.LanguageParser.ReadStatementContext ctx) {
        for (var id : ctx.identifierList().IDENTIFIER()) {
            String name = id.getText();
            try {
                SymbolTable.Type varType = symbolTable.reference(id.getSymbol());
                if (varType != SymbolTable.Type.INT &&
                        varType != SymbolTable.Type.FLOAT &&
                        varType != SymbolTable.Type.BOOL &&
                        varType != SymbolTable.Type.STRING) {
                    typeError(id.getSymbol(), "Variable '" + name + "' has unsupported type for read: " + varType);
                }
            } catch (TypeException e) {
                reportUndeclared(id.getSymbol(), name, e);
            }
        }
        return null;
    }


    @Override
    public SymbolTable.Type visitWriteStatement(io.github.d3m1d0s.pjp.LanguageParser.WriteStatementContext ctx) {
        for (var expr : ctx.exprList().expr()) {
            visit(expr);
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitIfStatement(io.github.d3m1d0s.pjp.LanguageParser.IfStatementContext ctx) {
        SymbolTable.Type conditionType = visit(ctx.expr());
        if (conditionType != null && conditionType != SymbolTable.Type.BOOL) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Condition in if statement must be bool, got " + conditionType + ".");
        }
        visitBody(ctx.statement(0)); // if-branch
        if (ctx.statement().size() > 1) {
            visitBody(ctx.statement(1)); // else-branch (if exist)
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitWhileStatement(io.github.d3m1d0s.pjp.LanguageParser.WhileStatementContext ctx) {
        SymbolTable.Type conditionType = visit(ctx.expr());
        if (conditionType != null && conditionType != SymbolTable.Type.BOOL) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Condition in while loop must be bool, got " + conditionType + ".");
        }
        visitBody(ctx.statement());
        return null;
    }

    @Override
    public SymbolTable.Type visitBlockStatement(io.github.d3m1d0s.pjp.LanguageParser.BlockStatementContext ctx) {
        symbolTable.enterScope();
        for (var stmt : ctx.statement()) {
            visit(stmt);
        }
        symbolTable.exitScope();
        return null;
    }

    // The body of a control statement is its own scope even without braces,
    // so a declaration used as the body cannot leak into the surrounding one.
    private void visitBody(io.github.d3m1d0s.pjp.LanguageParser.StatementContext body) {
        symbolTable.enterScope();
        visit(body);
        symbolTable.exitScope();
    }


    @Override
    public SymbolTable.Type visitAndExpr(io.github.d3m1d0s.pjp.LanguageParser.AndExprContext ctx) {
        return visitLogicalBinary(ctx.expr(0), ctx.expr(1), ctx,"&&");
    }

    @Override
    public SymbolTable.Type visitOrExpr(io.github.d3m1d0s.pjp.LanguageParser.OrExprContext ctx) {
        return visitLogicalBinary(ctx.expr(0), ctx.expr(1), ctx, "||");
    }

    private SymbolTable.Type visitLogicalBinary(io.github.d3m1d0s.pjp.LanguageParser.ExprContext leftExpr, io.github.d3m1d0s.pjp.LanguageParser.ExprContext rightExpr, ParserRuleContext ctx, String op) {
        SymbolTable.Type left = visit(leftExpr);
        SymbolTable.Type right = visit(rightExpr);

        if (left == null || right == null) return null;

        if (left == SymbolTable.Type.BOOL && right == SymbolTable.Type.BOOL) {
            return SymbolTable.Type.BOOL;
        }
        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Logical operator '" + op + "' requires bool operands. Got: " + left + ", " + right);
        return null;
    }

    @Override
    public SymbolTable.Type visitNotExpr(io.github.d3m1d0s.pjp.LanguageParser.NotExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());
        if (type == null) return null;

        if (type == SymbolTable.Type.BOOL) {
            return SymbolTable.Type.BOOL;
        }
        typeError(ctx.op, "Logical '!' requires bool operand. Got: " + type);
        return null;
    }

    @Override
    public SymbolTable.Type visitUnaryMinusExpr(io.github.d3m1d0s.pjp.LanguageParser.UnaryMinusExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());
        if (type == null) return null;

        if (type == SymbolTable.Type.INT || type == SymbolTable.Type.FLOAT) {
            return type;
        }

        typeError(ctx.op, "Unary minus requires int or float. Got: " + type);
        return null;
    }


    @Override
    public SymbolTable.Type visitForStatement(io.github.d3m1d0s.pjp.LanguageParser.ForStatementContext ctx) {
        if (ctx.forInit() != null && ctx.forInit().getChildCount() > 0) {
            checkHeaderAssignment(ctx.forInit().IDENTIFIER(), ctx.forInit().expr(), "for-init");
        }

        if (ctx.forCond() != null && ctx.forCond().expr() != null) {
            SymbolTable.Type condType = visit(ctx.forCond().expr());
            if (condType != null && condType != SymbolTable.Type.BOOL) {
                typeError(ctx.forCond().start, "Condition in for loop must be bool, got " + condType);
            }
        }

        if (ctx.forUpdate() != null && ctx.forUpdate().getChildCount() > 0) {
            checkHeaderAssignment(ctx.forUpdate().IDENTIFIER(), ctx.forUpdate().expr(), "for-update");
        }

        visitBody(ctx.statement());

        return null;
    }

    @Override
    public SymbolTable.Type visitAssignExpr(io.github.d3m1d0s.pjp.LanguageParser.AssignExprContext ctx) {
        String varName = ctx.left.getText();

        try {
            SymbolTable.Type varType = symbolTable.reference(ctx.left);
            SymbolTable.Type valueType = visit(ctx.right);

            // A null type means the right-hand side already produced an error.
            if (valueType == null) {
                return null;
            }

            if (!isCompatible(varType, valueType)) {
                Token opToken = (Token) ctx.getChild(1).getPayload();
                typeError(opToken, "Variable '" + varName + "' type is " + varType + ", but the assigned value is " + valueType + ".");
            }

            return varType;
        } catch (TypeException e) {
            reportUndeclared(ctx.left, varName, e);
            return null;
        }
    }

    @Override
    public SymbolTable.Type visitFileOpenExpr(io.github.d3m1d0s.pjp.LanguageParser.FileOpenExprContext ctx) {
        String name = decodeLiteral(ctx.STRING(0).getSymbol());
        if (name != null && name.isEmpty()) {
            typeError(ctx.STRING(0).getSymbol(), "File name in open() must not be empty.");
        }

        String mode = decodeLiteral(ctx.STRING(1).getSymbol());
        if (mode != null && !mode.equals("w") && !mode.equals("a")) {
            typeError(ctx.STRING(1).getSymbol(), "File open mode must be \"w\" or \"a\". Got: " + ctx.STRING(1).getText());
        }

        return SymbolTable.Type.FILE;
    }

    /** Decodes a string token, reporting bad escapes; returns null if the literal is malformed. */
    private String decodeLiteral(Token token) {
        try {
            return StringEscapes.decode(token.getText());
        } catch (IllegalArgumentException e) {
            typeError(token, e.getMessage() + " in string literal");
            return null;
        }
    }

    // === Helpers ===

    private void checkHeaderAssignment(TerminalNode id, io.github.d3m1d0s.pjp.LanguageParser.ExprContext expr, String clause) {
        SymbolTable.Type varType = null;
        try {
            varType = symbolTable.reference(id.getSymbol());
        } catch (TypeException e) {
            reportUndeclared(id.getSymbol(), id.getText(), e);
        }

        SymbolTable.Type valueType = visit(expr);

        if (varType != null && valueType != null && !isCompatible(varType, valueType)) {
            typeError(id.getSymbol(), "Incompatible types in " + clause + ": " + varType + " and " + valueType);
        }
    }

    private SymbolTable.Type computeBinaryNumericType(SymbolTable.Type left, SymbolTable.Type right,  ParserRuleContext ctx) {
        if (left == null || right == null) return null;
        if (left == SymbolTable.Type.FLOAT || right == SymbolTable.Type.FLOAT) {
            return SymbolTable.Type.FLOAT;
        }
        if (left == SymbolTable.Type.INT && right == SymbolTable.Type.INT) {
            return SymbolTable.Type.INT;
        }
        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Invalid operands for arithmetic operation: " + left + ", " + right);
        return null;
    }

    private boolean isCompatible(SymbolTable.Type target, SymbolTable.Type value) {
        if (target == value) {
            return true;
        }
        if (target == SymbolTable.Type.FLOAT && value == SymbolTable.Type.INT) {
            return true;
        }
        if (target == SymbolTable.Type.FILE && value == SymbolTable.Type.STRING) {
            return true;
        }
        return false;
    }

    private SymbolTable.Type getTypeFromKeyword(String keyword) {
        if (keyword.equals("int")) return SymbolTable.Type.INT;
        if (keyword.equals("float")) return SymbolTable.Type.FLOAT;
        if (keyword.equals("bool")) return SymbolTable.Type.BOOL;
        if (keyword.equals("string")) return SymbolTable.Type.STRING;
        if (keyword.equals(("file"))) return SymbolTable.Type.FILE;
        throw new RuntimeException("Unknown type: " + keyword);
    }

    private void typeError(Token token, String message) {
        int line = token.getLine();
        int pos = token.getCharPositionInLine();
        errors.add(line + "," + pos + ": " + message);
    }

    // An undeclared variable is reported once per name, so a single missing
    // declaration does not flood the output at every use site.
    private void reportUndeclared(Token token, String name, TypeException e) {
        if (reportedUndeclared.add(name)) {
            typeError(token, e.getMessage());
        }
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }


}



