package cz.university.codegen;

import cz.university.StringEscapes;

public class Instruction {

    public enum OpCode {
        ADD_I, ADD_F,
        SUB_I, SUB_F,
        MUL_I, MUL_F,
        DIV_I, DIV_F,
        MOD,
        UMINUS_I, UMINUS_F,
        CONCAT,
        AND, OR,
        GT_I, GT_F,
        LT_I, LT_F,
        EQ_I, EQ_F, EQ_S, EQ_B,
        NOT,
        ITOF,
        PUSH_I, PUSH_F,
        PUSH_S, PUSH_B,
        POP,
        LOAD,
        SAVE_I, SAVE_F,
        SAVE_S, SAVE_B,
        SAVE_FILE,
        LABEL,
        JMP,
        FJMP,
        PRINT,
        READ_I, READ_F, READ_S, READ_B,
        FOPEN,
        FAPPEND_N,
    }

    private final OpCode opCode;
    private final String operand;

    public Instruction(OpCode opCode, String operand) {
        this.opCode = opCode;
        this.operand = operand;
    }

    public Instruction(OpCode opCode) {
        this(opCode, null);
    }

    public OpCode getOpCode() {
        return opCode;
    }

    public String getOperand() {
        return operand;
    }

    @Override
    public String toString() {
        switch (opCode) {
            case LOAD:
            case POP:
            case PRINT:
            case LABEL:
            case JMP:
            case FJMP:
                return operand != null ? opCode.name().toLowerCase() + " " + operand : opCode.name().toLowerCase();

            case FAPPEND_N:
                return operand != null ? "fappend " + operand : "fappend";

            // The operand holds the raw value; quote and escape it so the
            // line based instruction format cannot be broken by its content.
            case PUSH_S:
                return "push S " + StringEscapes.encode(operand);


            case SAVE_I:
            case SAVE_F:
            case SAVE_S:
            case SAVE_B:
            case SAVE_FILE:
                return "save " + operand;

            default:
                break;
        }

        String name = opCode.name();
        if (name.contains("_")) {
            String[] parts = name.split("_");
            String op = parts[0].toLowerCase();
            String type = parts[1].toUpperCase();
            return operand != null
                    ? op + " " + type + " " + operand
                    : op + " " + type;
        }

        return operand != null
                ? name.toLowerCase() + " " + operand
                : name.toLowerCase();
    }



}

