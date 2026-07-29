package io.github.d3m1d0s.pjp.codegen;

import io.github.d3m1d0s.pjp.StringEscapes;

/** One stack machine instruction: an opcode with an optional operand. */
public class Instruction {

    // most names follow operation_type: toString splits on '_' to build the text form
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
        GE_I, GE_F,
        LE_I, LE_F,
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

    /**
     * Renders the instruction as the line the stack machine parses. For non-file
     * instructions this text is pinned byte for byte by the reference listings.
     */
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

            // the N is arity, not a type letter: the machine reads "fappend <count>"
            case FAPPEND_N:
                return "fappend " + operand;

            // quote and escape the raw value so its content cannot break the line format
            case PUSH_S:
                return "push S " + StringEscapes.encode(operand);

            // save prints without a type suffix: the machine stores by name only
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
