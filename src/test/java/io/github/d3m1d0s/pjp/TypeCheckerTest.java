package io.github.d3m1d0s.pjp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// collected diagnostics: what the type checker rejects, with exact
// positions and messages, and what it deliberately allows
public class TypeCheckerTest extends CompilerTestSupport {

    @Test
    public void testDoubleDeclarationThrowsException() throws TypeException {
        SymbolTable st = new SymbolTable();
        st.declare("x", SymbolTable.Type.INT);
        assertThrows(TypeException.class, () -> st.declare("x", SymbolTable.Type.FLOAT));
    }

    @Test
    public void testNotOnNonBoolIsReported() {
        assertEquals(List.of(
                "1,6: Logical '!' requires bool operand. Got: INT"
        ), typeErrors("write !5;\n"));
    }

    @Test
    public void testUnaryMinusOnStringIsReported() {
        assertEquals(List.of(
                "1,6: Unary minus requires int or float. Got: STRING"
        ), typeErrors("write -\"abc\";\n"));
    }

    @Test
    public void testReadIntoFileVariableIsReported() {
        assertEquals(List.of(
                "2,5: Variable 'f' has unsupported type for read: FILE"
        ), typeErrors("""
        file f;
        read f;
        """));
    }

    @Test
    public void testFileEqualityIsRejected() {
        // equality is defined for bools but must not extend to FILE operands
        assertEquals(List.of(
                "3,8: Invalid types for equality: FILE, FILE"
        ), typeErrors("""
        file f;
        file g;
        write f == g;
        """));
    }

    @Test
    public void testOrderingOperatorsRejectStrings() {
        assertEquals(List.of(
                "1,10: Relational operators are only valid for int or float. Got: STRING, STRING"
        ), typeErrors("write \"a\" <= \"b\";\n"));
    }

    @Test
    public void testUndeclaredVariableInForInitIsReported() {
        assertEquals(List.of(
                "1,5: Variable 'q' is not declared."
        ), typeErrors("for (q = 0; true; ) ;\n"));
    }

    @Test
    public void testUndeclaredVariableInForUpdateIsReported() {
        assertEquals(List.of(
                "2,18: Variable 'q' is not declared."
        ), typeErrors("""
        int i;
        for (i = 0; true; q = 1) ;
        """));
    }

    @Test
    public void testIncompatibleTypeInForInitIsReported() {
        assertEquals(List.of(
                "2,5: Incompatible types in for-init: INT and STRING"
        ), typeErrors("""
        int i;
        for (i = "text"; true; ) ;
        """));
    }

    @Test
    public void testNonBoolForConditionIsReported() {
        assertEquals(List.of(
                "2,12: Condition in for loop must be bool, got INT"
        ), typeErrors("int i;\nfor (i = 0; i + 1; i = i + 1) ;\n"));
    }

    @Test
    public void testUndeclaredVariableErrorsCarryPositions() {
        assertEquals(List.of(
                "1,0: Variable 'x' is not declared.",
                "2,6: Variable 'y' is not declared.",
                "3,7: Variable 'x' is already declared."
        ), typeErrors("""
        x = 5;
        write y;
        int x, x;
        """));
    }

    @Test
    public void testOneErrorPerMistakeInAssignment() {
        assertEquals(List.of(
                "2,8: Invalid operands for arithmetic operation: STRING, INT"
        ), typeErrors("int a;\na = \"s\" + 1;\n"));
    }

    @Test
    public void testErroredOperandDoesNotCascadeIntoNot() {
        assertEquals(List.of(
                "2,5: Variable 'x' is not declared."
        ), typeErrors("bool b;\nb = !x;\n"));
    }

    @Test
    public void testErroredOperandsDoNotCascadeIntoLogicalOperator() {
        assertEquals(List.of(
                "2,4: Variable 'x' is not declared.",
                "2,9: Variable 'y' is not declared."
        ), typeErrors("bool b;\nb = x && y;\n"));
    }

    @Test
    public void testUndeclaredVariableReportedOncePerName() {
        assertEquals(List.of(
                "1,5: Variable 'q' is not declared."
        ), typeErrors("for (q = 0; q < 3; q = q + 1) ;\n"));
    }

    @Test
    public void testIntLiteralOutOfRangeIsReported() {
        assertEquals(List.of(
                "1,6: Integer literal out of range: 2147483648"
        ), typeErrors("write 2147483648;\n"));
    }

    @Test
    public void testMostNegativeIntLiteralIsRejected() {
        // parsed as unary minus applied to 2147483648, which overflows int (same parse as C and Java)
        assertEquals(List.of(
                "1,7: Integer literal out of range: 2147483648"
        ), typeErrors("write -2147483648;\n"));
    }

    @Test
    public void testInvalidOpenModeIsReported() {
        assertEquals(List.of(
                "2,18: File open mode must be \"w\" or \"a\". Got: \"r\""
        ), typeErrors("""
        file f;
        f = open("x.txt", "r");
        """));
    }

    @Test
    public void testEmptyFileNameInOpenIsReported() {
        assertEquals(List.of(
                "2,9: File name in open() must not be empty."
        ), typeErrors("""
        file f;
        f = open("", "w");
        """));
    }

    @Test
    public void testUnknownEscapeIsReported() {
        assertEquals(List.of(
                "1,6: Unknown escape sequence '\\q' in string literal"
        ), typeErrors("write \"a\\qb\";\n"));
    }

    @Test
    public void testSiblingBlocksMayReuseAName() {
        assertEquals(List.of(), typeErrors("{ int x; }\n{ int x; }\n"));
    }

    @Test
    public void testInnerDeclarationShadowsOuter() {
        assertEquals(List.of(), typeErrors("int x;\n{ int x; }\n"));
    }

    @Test
    public void testRedeclarationInSameScopeIsAnError() {
        assertEquals(List.of(
                "2,13: Variable 'q' is already declared."
        ), typeErrors("int x;\n{ int q; int q; }\n"));
    }

    @Test
    public void testBlockLocalVariableIsInvisibleOutside() {
        assertEquals(List.of(
                "2,6: Variable 'y' is not declared."
        ), typeErrors("{ int y; }\nwrite y;\n"));
    }

    @Test
    public void testBranchLocalVariableIsInvisibleOutside() {
        assertEquals(List.of(
                "3,6: Variable 'z' is not declared."
        ), typeErrors("""
        int c;
        if (c == 0) { int z; z = 5; }
        write z;
        """));
    }
}
