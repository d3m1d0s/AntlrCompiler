package io.github.d3m1d0s.pjp.codegen;

import io.github.d3m1d0s.pjp.StringEscapes;
import io.github.d3m1d0s.pjp.SymbolTable;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Second pass over the type-checked tree: emits the text instruction listing.
 * Identifiers come from what the checker resolved, since the scopes are
 * closed by now.
 */
public class CodeGeneratorVisitor extends io.github.d3m1d0s.pjp.LanguageBaseVisitor<SymbolTable.Type> {

    private final SymbolTable symbolTable;
    private final List<Instruction> instructions = new ArrayList<>();
    // one shared counter for all labels: the reference listings pin the numbering
    private int labelCounter = 0;

    public CodeGeneratorVisitor(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    @Override
    public SymbolTable.Type visitDeclaration(io.github.d3m1d0s.pjp.LanguageParser.DeclarationContext ctx) {
        // every non-file declaration stores a default, so only an unopened file variable can be missing at load
        for (TerminalNode id : ctx.variableList().IDENTIFIER()) {
            SymbolTable.VariableInfo variable = symbolTable.resolved(id.getSymbol());
            String name = variable.runtimeName;

            switch (variable.type) {
                case INT -> {
                    instructions.add(new Instruction(Instruction.OpCode.PUSH_I, "0"));
                    instructions.add(new Instruction(Instruction.OpCode.SAVE_I, name));
                }
                case FLOAT -> {
                    instructions.add(new Instruction(Instruction.OpCode.PUSH_F, "0.0"));
                    instructions.add(new Instruction(Instruction.OpCode.SAVE_F, name));
                }
                case BOOL -> {
                    instructions.add(new Instruction(Instruction.OpCode.PUSH_B, "false"));
                    instructions.add(new Instruction(Instruction.OpCode.SAVE_B, name));
                }
                case STRING -> {
                    instructions.add(new Instruction(Instruction.OpCode.PUSH_S, ""));
                    instructions.add(new Instruction(Instruction.OpCode.SAVE_S, name));
                }
                case FILE -> {
                    // nothing to push: a file becomes loadable only once a handle is saved into it
                }
            }
        }

        return null;
    }

    @Override
    public SymbolTable.Type visitExpressionStatement(io.github.d3m1d0s.pjp.LanguageParser.ExpressionStatementContext ctx) {
        // the trailing pop discards the expression value and is pinned by the reference listings
        SymbolTable.Type type = visit(ctx.expr());
        instructions.add(new Instruction(Instruction.OpCode.POP));
        return type;
    }

    @Override
    public SymbolTable.Type visitIdExpr(io.github.d3m1d0s.pjp.LanguageParser.IdExprContext ctx) {
        SymbolTable.VariableInfo variable = symbolTable.resolved(ctx.IDENTIFIER().getSymbol());
        instructions.add(new Instruction(Instruction.OpCode.LOAD, variable.runtimeName));
        return variable.type;
    }

    @Override
    public SymbolTable.Type visitIntExpr(io.github.d3m1d0s.pjp.LanguageParser.IntExprContext ctx) {
        instructions.add(new Instruction(Instruction.OpCode.PUSH_I, ctx.getText()));
        return SymbolTable.Type.INT;
    }

    @Override
    public SymbolTable.Type visitFloatExpr(io.github.d3m1d0s.pjp.LanguageParser.FloatExprContext ctx) {
        instructions.add(new Instruction(Instruction.OpCode.PUSH_F, ctx.getText()));
        return SymbolTable.Type.FLOAT;
    }

    @Override
    public SymbolTable.Type visitBoolExpr(io.github.d3m1d0s.pjp.LanguageParser.BoolExprContext ctx) {
        instructions.add(new Instruction(Instruction.OpCode.PUSH_B, ctx.getText()));
        return SymbolTable.Type.BOOL;
    }

    @Override
    public SymbolTable.Type visitStringExpr(io.github.d3m1d0s.pjp.LanguageParser.StringExprContext ctx) {
        instructions.add(new Instruction(Instruction.OpCode.PUSH_S, StringEscapes.decode(ctx.getText())));
        return SymbolTable.Type.STRING;
    }

    @Override
    public SymbolTable.Type visitParenExpr(io.github.d3m1d0s.pjp.LanguageParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public SymbolTable.Type visitAdditiveExpr(io.github.d3m1d0s.pjp.LanguageParser.AdditiveExprContext ctx) {
        var leftExpr = ctx.expr(0);
        var rightExpr = ctx.expr(1);

        // types are needed up front: each int operand takes its itof right after its own code
        SymbolTable.Type leftType = symbolTable.getExprType(leftExpr);
        SymbolTable.Type rightType = symbolTable.getExprType(rightExpr);

        String op = ctx.op.getText();
        if (op.equals(".")) {
            if (leftType != SymbolTable.Type.STRING || rightType != SymbolTable.Type.STRING) {
                throw new RuntimeException("Both operands must be strings for '.' operator.");
            }

            visit(leftExpr);
            visit(rightExpr);
            instructions.add(new Instruction(Instruction.OpCode.CONCAT));
            return SymbolTable.Type.STRING;
        }

        SymbolTable.Type resultType = (leftType == SymbolTable.Type.FLOAT || rightType == SymbolTable.Type.FLOAT)
                ? SymbolTable.Type.FLOAT
                : SymbolTable.Type.INT;

        visit(leftExpr);
        if (resultType == SymbolTable.Type.FLOAT && leftType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        visit(rightExpr);
        if (resultType == SymbolTable.Type.FLOAT && rightType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        instructions.add(new Instruction(
                switch (op) {
                    case "+" -> resultType == SymbolTable.Type.FLOAT ? Instruction.OpCode.ADD_F : Instruction.OpCode.ADD_I;
                    case "-" -> resultType == SymbolTable.Type.FLOAT ? Instruction.OpCode.SUB_F : Instruction.OpCode.SUB_I;
                    default -> throw new RuntimeException("Unknown op: " + op);
                }
        ));

        return resultType;
    }

    @Override
    public SymbolTable.Type visitMultiplicativeExpr(io.github.d3m1d0s.pjp.LanguageParser.MultiplicativeExprContext ctx) {
        var leftExpr = ctx.expr(0);
        var rightExpr = ctx.expr(1);

        SymbolTable.Type leftType = symbolTable.getExprType(leftExpr);
        SymbolTable.Type rightType = symbolTable.getExprType(rightExpr);

        SymbolTable.Type resultType = (leftType == SymbolTable.Type.FLOAT || rightType == SymbolTable.Type.FLOAT)
                ? SymbolTable.Type.FLOAT
                : SymbolTable.Type.INT;

        visit(leftExpr);
        if (resultType == SymbolTable.Type.FLOAT && leftType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        visit(rightExpr);
        if (resultType == SymbolTable.Type.FLOAT && rightType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        String op = ctx.op.getText();
        instructions.add(new Instruction(
                switch (op) {
                    case "*" -> resultType == SymbolTable.Type.FLOAT ? Instruction.OpCode.MUL_F : Instruction.OpCode.MUL_I;
                    case "/" -> resultType == SymbolTable.Type.FLOAT ? Instruction.OpCode.DIV_F : Instruction.OpCode.DIV_I;
                    case "%" -> Instruction.OpCode.MOD;
                    default -> throw new RuntimeException("Unknown op: " + op);
                }
        ));

        return resultType;
    }

    @Override
    public SymbolTable.Type visitEqualityExpr(io.github.d3m1d0s.pjp.LanguageParser.EqualityExprContext ctx) {
        var leftExpr = ctx.expr(0);
        var rightExpr = ctx.expr(1);
        String op = ctx.op.getText();

        int line = ctx.getStart().getLine();
        SymbolTable.Type leftType = symbolTable.getExprType(leftExpr);
        SymbolTable.Type rightType = symbolTable.getExprType(rightExpr);

        boolean floatComparison = (leftType == SymbolTable.Type.FLOAT || rightType == SymbolTable.Type.FLOAT);

        visit(leftExpr);
        if (floatComparison && leftType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        visit(rightExpr);
        if (floatComparison && rightType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        if (floatComparison) {
            instructions.add(new Instruction(Instruction.OpCode.EQ_F));
        } else if (leftType == rightType) {
            switch (leftType) {
                case INT -> instructions.add(new Instruction(Instruction.OpCode.EQ_I));
                case STRING -> instructions.add(new Instruction(Instruction.OpCode.EQ_S));
                case BOOL -> instructions.add(new Instruction(Instruction.OpCode.EQ_B));
                default -> throw new RuntimeException("Unsupported EQ type: " + leftType);
            }
        } else {
            throw new RuntimeException("Type mismatch in equality expression at line " + line);
        }

        // no not-equal opcode: '!=' is an equality test followed by not
        if (op.equals("!=")) {
            instructions.add(new Instruction(Instruction.OpCode.NOT));
        }

        return SymbolTable.Type.BOOL;
    }

    @Override
    public SymbolTable.Type visitRelationalExpr(io.github.d3m1d0s.pjp.LanguageParser.RelationalExprContext ctx) {
        var leftExpr = ctx.expr(0);
        var rightExpr = ctx.expr(1);
        String op = ctx.op.getText();

        SymbolTable.Type leftType = symbolTable.getExprType(leftExpr);
        SymbolTable.Type rightType = symbolTable.getExprType(rightExpr);
        boolean floatComparison = (leftType == SymbolTable.Type.FLOAT || rightType == SymbolTable.Type.FLOAT);

        visit(leftExpr);
        if (floatComparison && leftType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        visit(rightExpr);
        if (floatComparison && rightType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        instructions.add(new Instruction(switch (op) {
            case "<" -> floatComparison ? Instruction.OpCode.LT_F : Instruction.OpCode.LT_I;
            case ">" -> floatComparison ? Instruction.OpCode.GT_F : Instruction.OpCode.GT_I;
            case "<=" -> floatComparison ? Instruction.OpCode.LE_F : Instruction.OpCode.LE_I;
            case ">=" -> floatComparison ? Instruction.OpCode.GE_F : Instruction.OpCode.GE_I;
            default -> throw new RuntimeException("Unknown relational operator: " + op);
        }));
        return SymbolTable.Type.BOOL;
    }

    @Override
    public SymbolTable.Type visitNotExpr(io.github.d3m1d0s.pjp.LanguageParser.NotExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());
        if (type == SymbolTable.Type.BOOL) {
            instructions.add(new Instruction(Instruction.OpCode.NOT));
            return SymbolTable.Type.BOOL;
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitAndExpr(io.github.d3m1d0s.pjp.LanguageParser.AndExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if (left == SymbolTable.Type.BOOL && right == SymbolTable.Type.BOOL) {
            instructions.add(new Instruction(Instruction.OpCode.AND));
            return SymbolTable.Type.BOOL;
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitOrExpr(io.github.d3m1d0s.pjp.LanguageParser.OrExprContext ctx) {
        SymbolTable.Type left = visit(ctx.expr(0));
        SymbolTable.Type right = visit(ctx.expr(1));

        if (left == SymbolTable.Type.BOOL && right == SymbolTable.Type.BOOL) {
            instructions.add(new Instruction(Instruction.OpCode.OR));
            return SymbolTable.Type.BOOL;
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitWriteStatement(io.github.d3m1d0s.pjp.LanguageParser.WriteStatementContext ctx) {
        int count = 0;
        for (var expr : ctx.exprList().expr()) {
            visit(expr);
            count++;
        }

        // print's operand is the value count, popped and printed in source order
        instructions.add(new Instruction(Instruction.OpCode.PRINT, String.valueOf(count)));
        return null;
    }

    @Override
    public SymbolTable.Type visitReadStatement(io.github.d3m1d0s.pjp.LanguageParser.ReadStatementContext ctx) {
        for (var id : ctx.identifierList().IDENTIFIER()) {
            SymbolTable.VariableInfo variable = symbolTable.resolved(id.getSymbol());

            switch (variable.type) {
                case INT -> instructions.add(new Instruction(Instruction.OpCode.READ_I));
                case FLOAT -> instructions.add(new Instruction(Instruction.OpCode.READ_F));
                case BOOL -> instructions.add(new Instruction(Instruction.OpCode.READ_B));
                case STRING -> instructions.add(new Instruction(Instruction.OpCode.READ_S));
            }

            addSaveInstruction(variable.type, variable.runtimeName);
        }
        return null;
    }

    @Override
    public SymbolTable.Type visitWhileStatement(io.github.d3m1d0s.pjp.LanguageParser.WhileStatementContext ctx) {
        String startLabel = nextLabel();
        String endLabel = nextLabel();

        instructions.add(new Instruction(Instruction.OpCode.LABEL, startLabel));

        visit(ctx.expr());
        instructions.add(new Instruction(Instruction.OpCode.FJMP, endLabel));

        visit(ctx.statement());

        instructions.add(new Instruction(Instruction.OpCode.JMP, startLabel));

        instructions.add(new Instruction(Instruction.OpCode.LABEL, endLabel));

        return null;
    }

    @Override
    public SymbolTable.Type visitIfStatement(io.github.d3m1d0s.pjp.LanguageParser.IfStatementContext ctx) {
        String elseLabel = nextLabel();
        String endLabel = nextLabel();

        visit(ctx.expr());
        instructions.add(new Instruction(Instruction.OpCode.FJMP, elseLabel));

        visit(ctx.statement(0)); // then-branch

        instructions.add(new Instruction(Instruction.OpCode.JMP, endLabel));
        instructions.add(new Instruction(Instruction.OpCode.LABEL, elseLabel));

        if (ctx.statement().size() > 1) {
            visit(ctx.statement(1)); // else-branch
        }

        instructions.add(new Instruction(Instruction.OpCode.LABEL, endLabel));

        return null;
    }

    @Override
    public SymbolTable.Type visitForStatement(io.github.d3m1d0s.pjp.LanguageParser.ForStatementContext ctx) {
        String startLabel = nextLabel();
        String endLabel = nextLabel();

        if (ctx.forInit() != null && ctx.forInit().getChildCount() > 0) {
            generateHeaderAssignment(ctx.forInit().IDENTIFIER(), ctx.forInit().expr());
        }

        instructions.add(new Instruction(Instruction.OpCode.LABEL, startLabel));

        // an empty condition emits no fjmp, so a for without one never exits
        if (ctx.forCond() != null && ctx.forCond().expr() != null) {
            visit(ctx.forCond().expr());
            instructions.add(new Instruction(Instruction.OpCode.FJMP, endLabel));
        }

        visit(ctx.statement());

        if (ctx.forUpdate() != null && ctx.forUpdate().getChildCount() > 0) {
            generateHeaderAssignment(ctx.forUpdate().IDENTIFIER(), ctx.forUpdate().expr());
        }

        instructions.add(new Instruction(Instruction.OpCode.JMP, startLabel));
        instructions.add(new Instruction(Instruction.OpCode.LABEL, endLabel));

        return null;
    }

    @Override
    public SymbolTable.Type visitUnaryMinusExpr(io.github.d3m1d0s.pjp.LanguageParser.UnaryMinusExprContext ctx) {
        SymbolTable.Type type = visit(ctx.expr());

        switch (type) {
            case INT -> instructions.add(new Instruction(Instruction.OpCode.UMINUS_I));
            case FLOAT -> instructions.add(new Instruction(Instruction.OpCode.UMINUS_F));
            default -> throw new RuntimeException("Unary minus not supported for type: " + type);
        }

        return type;
    }

    @Override
    public SymbolTable.Type visitAssignExpr(io.github.d3m1d0s.pjp.LanguageParser.AssignExprContext ctx) {
        SymbolTable.Type valueType = visit(ctx.right);

        SymbolTable.VariableInfo variable = symbolTable.resolved(ctx.left);

        if (variable.type == SymbolTable.Type.FLOAT && valueType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        // a string assigned to a file variable opens that file in append mode
        if (variable.type == SymbolTable.Type.FILE && valueType == SymbolTable.Type.STRING) {
            instructions.add(new Instruction(Instruction.OpCode.PUSH_S, "a"));
            instructions.add(new Instruction(Instruction.OpCode.FOPEN));
        }

        addSaveInstruction(variable.type, variable.runtimeName);

        // no dup in the machine, so reload to leave the assigned value on the stack
        instructions.add(new Instruction(Instruction.OpCode.LOAD, variable.runtimeName));

        return variable.type;
    }

    // one fappend per chain, not per '<<': the machine writes one line per fappend
    @Override
    public SymbolTable.Type visitFileAppendExpr(io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext ctx) {
        List<ParserRuleContext> exprs = new ArrayList<>();
        io.github.d3m1d0s.pjp.LanguageParser.ExprContext current = ctx;

        while (current instanceof io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext appendCtx) {
            exprs.add(((io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext) current).right);
            current = ((io.github.d3m1d0s.pjp.LanguageParser.FileAppendExprContext) current).left;
        }

        SymbolTable.Type fileType = visit(current);
        if (fileType != SymbolTable.Type.FILE) {
            throw new RuntimeException("Left side of << must be FILE");
        }

        // the walk collected them right to left
        Collections.reverse(exprs);
        for (ParserRuleContext arg : exprs) {
            visit(arg);
        }

        instructions.add(new Instruction(Instruction.OpCode.FAPPEND_N, String.valueOf(exprs.size())));
        return SymbolTable.Type.FILE;
    }

    @Override
    public SymbolTable.Type visitFileOpenExpr(io.github.d3m1d0s.pjp.LanguageParser.FileOpenExprContext ctx) {
        String filename = StringEscapes.decode(ctx.STRING(0).getText());
        String mode = StringEscapes.decode(ctx.STRING(1).getText());

        if (!mode.equals("w") && !mode.equals("a")) {
            throw new RuntimeException("Invalid mode in open(): " + mode);
        }

        instructions.add(new Instruction(Instruction.OpCode.PUSH_S, filename));
        instructions.add(new Instruction(Instruction.OpCode.PUSH_S, mode));
        instructions.add(new Instruction(Instruction.OpCode.FOPEN));

        return SymbolTable.Type.FILE;
    }

    /** Writes the listing, one instruction per line, in the text form the machine executes. */
    public void saveToFile(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, StandardCharsets.UTF_8))) {
            for (Instruction instr : instructions) {
                writer.println(instr);
            }
        }
    }

    // === Helpers ===

    private void generateHeaderAssignment(TerminalNode id, io.github.d3m1d0s.pjp.LanguageParser.ExprContext expr) {
        SymbolTable.VariableInfo variable = symbolTable.resolved(id.getSymbol());

        SymbolTable.Type valueType = visit(expr);
        if (variable.type == SymbolTable.Type.FLOAT && valueType == SymbolTable.Type.INT) {
            instructions.add(new Instruction(Instruction.OpCode.ITOF));
        }

        addSaveInstruction(variable.type, variable.runtimeName);
    }

    private void addSaveInstruction(SymbolTable.Type type, String name) {
        switch (type) {
            case INT -> instructions.add(new Instruction(Instruction.OpCode.SAVE_I, name));
            case FLOAT -> instructions.add(new Instruction(Instruction.OpCode.SAVE_F, name));
            case BOOL -> instructions.add(new Instruction(Instruction.OpCode.SAVE_B, name));
            case STRING -> instructions.add(new Instruction(Instruction.OpCode.SAVE_S, name));
            case FILE -> instructions.add(new Instruction(Instruction.OpCode.SAVE_FILE, name));
        }
    }

    private String nextLabel() {
        return String.valueOf(labelCounter++);
    }
}
