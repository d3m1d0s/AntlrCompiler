package cz.university;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

public class TypeCheckerVisitor extends cz.university.LanguageBaseVisitor<SymbolTable.Type> {

    private final SymbolTable symbolTable = new SymbolTable();
    private final List<String> errors = new ArrayList<>();

    public List<String> getErrors() {
        return errors;
    }

    // === Statements ===

    @Override
    public SymbolTable.Type visitDeclaration(cz.university.LanguageParser.DeclarationContext ctx) {
        SymbolTable.Type declaredType = getTypeFromKeyword(ctx.primitiveType().getText());
        for (var id : ctx.variableList().IDENTIFIER()) {
            String name = id.getText();
            int line = id.getSymbol().getLine();
            try {
                symbolTable.declare(name, declaredType, line);
            } catch (TypeException e) {
                errors.add(e.getMessage());
            }
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitExpressionStatement(cz.university.LanguageParser.ExpressionStatementContext ctx) {
        return visit(ctx.expr());
    }

    // === Expressions ===
    @Override
    public SymbolTable.Type visitEmptyStatement(cz.university.LanguageParser.EmptyStatementContext ctx) {
        return null;
    }

    @Override
    public SymbolTable.Type visitFileAppendExpr(cz.university.LanguageParser.FileAppendExprContext ctx) {
        SymbolTable.Type leftType = visit(ctx.expr(0));
        SymbolTable.Type rightType = visit(ctx.expr(1));

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
    public SymbolTable.Type visitIdExpr(cz.university.LanguageParser.IdExprContext ctx) {
        String name = ctx.IDENTIFIER().getText();
        int line = ctx.getStart().getLine();
        try {
            return symbolTable.getType(name, line);
        } catch (TypeException e) {
            errors.add(e.getMessage());
            return null;
        }
    }

    @Override
    public SymbolTable.Type visitIntExpr(cz.university.LanguageParser.IntExprContext ctx) {
        return SymbolTable.Type.INT;
    }

    @Override
    public SymbolTable.Type visitFloatExpr(cz.university.LanguageParser.FloatExprContext ctx) {
        return SymbolTable.Type.FLOAT;
    }

    @Override
    public SymbolTable.Type visitBoolExpr(cz.university.LanguageParser.BoolExprContext ctx) {
        return SymbolTable.Type.BOOL;
    }

    @Override
    public SymbolTable.Type visitStringExpr(cz.university.LanguageParser.StringExprContext ctx) {
        return SymbolTable.Type.STRING;
    }

    @Override
    public SymbolTable.Type visitParenExpr(cz.university.LanguageParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public SymbolTable.Type visitAdditiveExpr(cz.university.LanguageParser.AdditiveExprContext ctx) {
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
    public SymbolTable.Type visitMultiplicativeExpr(cz.university.LanguageParser.MultiplicativeExprContext ctx) {
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
    public SymbolTable.Type visitEqualityExpr(cz.university.LanguageParser.EqualityExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if (left == null || right == null) return null;

        // int == float or float == int -> OK
        if ((left == SymbolTable.Type.INT && right == SymbolTable.Type.FLOAT) ||
                (left == SymbolTable.Type.FLOAT && right == SymbolTable.Type.INT)) {
            return SymbolTable.Type.BOOL;
        }

        // int == int, float == float, string == string -> OK
        if ((left == right) &&
                (left == SymbolTable.Type.INT || left == SymbolTable.Type.FLOAT || left == SymbolTable.Type.STRING)) {
            return SymbolTable.Type.BOOL;
        }

        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Invalid types for equality: " + left + ", " + right);
        return null;
    }


    @Override
    public SymbolTable.Type visitRelationalExpr(cz.university.LanguageParser.RelationalExprContext ctx) {
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
    public SymbolTable.Type visitReadStatement(cz.university.LanguageParser.ReadStatementContext ctx) {
        for (var id : ctx.identifierList().IDENTIFIER()) {
            String name = id.getText();
            int line = id.getSymbol().getLine();
            try {
                SymbolTable.Type varType = symbolTable.getType(name, line);
                if (varType != SymbolTable.Type.INT &&
                        varType != SymbolTable.Type.FLOAT &&
                        varType != SymbolTable.Type.BOOL &&
                        varType != SymbolTable.Type.STRING) {
                    typeError(id.getSymbol(), "Variable '" + name + "' has unsupported type for read: " + varType);
                }
            } catch (TypeException e) {
                errors.add(e.getMessage());
            }
        }
        return null;
    }


    @Override
    public SymbolTable.Type visitWriteStatement(cz.university.LanguageParser.WriteStatementContext ctx) {
        for (var expr : ctx.exprList().expr()) {
            visit(expr);
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitIfStatement(cz.university.LanguageParser.IfStatementContext ctx) {
        SymbolTable.Type conditionType = visit(ctx.expr());
        if (conditionType != null && conditionType != SymbolTable.Type.BOOL) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Condition in if statement must be bool, got " + conditionType + ".");
        }
        visit(ctx.statement(0)); // if-branch
        if (ctx.statement().size() > 1) {
            visit(ctx.statement(1)); // else-branch (if exist)
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitWhileStatement(cz.university.LanguageParser.WhileStatementContext ctx) {
        SymbolTable.Type conditionType = visit(ctx.expr());
        if (conditionType != null && conditionType != SymbolTable.Type.BOOL) {
            Token opToken = (Token) ctx.getChild(1).getPayload();
            typeError(opToken, "Condition in while loop must be bool, got " + conditionType + ".");
        }
        visit(ctx.statement());
        return null;
    }

    @Override
    public SymbolTable.Type visitBlockStatement(cz.university.LanguageParser.BlockStatementContext ctx) {
        for (var stmt : ctx.statement()) {
            visit(stmt);
        }
        return null;
    }


    @Override
    public SymbolTable.Type visitAndExpr(cz.university.LanguageParser.AndExprContext ctx) {
        return visitLogicalBinary(ctx.expr(0), ctx.expr(1), ctx,"&&");
    }

    @Override
    public SymbolTable.Type visitOrExpr(cz.university.LanguageParser.OrExprContext ctx) {
        return visitLogicalBinary(ctx.expr(0), ctx.expr(1), ctx, "||");
    }

    private SymbolTable.Type visitLogicalBinary(cz.university.LanguageParser.ExprContext leftExpr, cz.university.LanguageParser.ExprContext rightExpr, ParserRuleContext ctx, String op) {
        SymbolTable.Type left = visit(leftExpr);
        SymbolTable.Type right = visit(rightExpr);

        if (left == SymbolTable.Type.BOOL && right == SymbolTable.Type.BOOL) {
            return SymbolTable.Type.BOOL;
        }
        Token opToken = (Token) ctx.getChild(1).getPayload();
        typeError(opToken, "Logical operator '" + op + "' requires bool operands. Got: " + left + ", " + right);
        return null;
    }

    @Override
    public SymbolTable.Type visitNotExpr(cz.university.LanguageParser.NotExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());

        if (type == SymbolTable.Type.BOOL) {
            return SymbolTable.Type.BOOL;
        }
        typeError(ctx.op, "Logical '!' requires bool operand. Got: " + type);
        return null;
    }

    @Override
    public SymbolTable.Type visitUnaryMinusExpr(cz.university.LanguageParser.UnaryMinusExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());

        if (type == SymbolTable.Type.INT || type == SymbolTable.Type.FLOAT) {
            return type;
        }

        typeError(ctx.op, "Unary minus requires int or float. Got: " + type);
        return null;
    }


    @Override
    public SymbolTable.Type visitForStatement(cz.university.LanguageParser.ForStatementContext ctx) {
        if (ctx.forInit() != null && ctx.forInit().getChildCount() > 0) {
            String var = ctx.forInit().IDENTIFIER().getText();
            SymbolTable.Type varType = null;
            try {
                varType = symbolTable.getType(var, ctx.getStart().getLine());
            } catch (TypeException e) {
                throw new RuntimeException(e);
            }
            SymbolTable.Type valueType = visit(ctx.forInit().expr());
            if (!isCompatible(varType, valueType)) {
                typeError(ctx.forInit().start, "Incompatible types in for-init: " + varType + " and " + valueType);
            }
        }

        if (ctx.forCond() != null && ctx.forCond().expr() != null) {
            SymbolTable.Type condType = visit(ctx.forCond().expr());
            if (condType != SymbolTable.Type.BOOL) {
                typeError(ctx.forCond().start, "Condition in for loop must be bool, got " + condType);
            }
        }

        if (ctx.forUpdate() != null && ctx.forUpdate().getChildCount() > 0) {
            String var = ctx.forUpdate().IDENTIFIER().getText();
            SymbolTable.Type varType = null;
            try {
                varType = symbolTable.getType(var, ctx.getStart().getLine());
            } catch (TypeException e) {
                throw new RuntimeException(e);
            }
            SymbolTable.Type valueType = visit(ctx.forUpdate().expr());
            if (!isCompatible(varType, valueType)) {
                typeError(ctx.forUpdate().start, "Incompatible types in for-update: " + varType + " and " + valueType);
            }
        }

        visit(ctx.statement());

        return null;
    }

    @Override
    public SymbolTable.Type visitAssignExpr(cz.university.LanguageParser.AssignExprContext ctx) {
        String varName = ctx.left.getText();
        int line = ctx.getStart().getLine();

        try {
            SymbolTable.Type varType = symbolTable.getType(varName, line);
            SymbolTable.Type valueType = visit(ctx.right);

            if (valueType == null) {
                Token opToken = (Token) ctx.getChild(1).getPayload();
                typeError(opToken, "Right-hand side of assignment to '" + varName + "' has invalid type.");
                return null;
            }

            if (!isCompatible(varType, valueType)) {
                Token opToken = (Token) ctx.getChild(1).getPayload();
                typeError(opToken, "Variable '" + varName + "' type is " + varType + ", but the assigned value is " + valueType + ".");
            }

            return varType;
        } catch (TypeException e) {
            errors.add(e.getMessage());
            return null;
        }
    }

    @Override
    public SymbolTable.Type visitFileOpenExpr(cz.university.LanguageParser.FileOpenExprContext ctx) {
        return SymbolTable.Type.FILE;
    }

    // === Helpers ===

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

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }


}



