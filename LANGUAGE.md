# Language Reference

## Table of Contents

- [Overview](#overview)
- [Names](#names)
- [Types and Declarations](#types-and-declarations)
- [Literals](#literals)
- [Scope](#scope)
- [Expressions](#expressions)
  - [Arithmetic](#arithmetic)
  - [Strings](#strings)
  - [Comparisons](#comparisons)
  - [Logic](#logic)
  - [Assignment](#assignment)
  - [Missing operators](#missing-operators)
- [Statements](#statements)
  - [if and else](#if-and-else)
  - [while](#while)
  - [for](#for)
  - [read and write](#read-and-write)
- [Input and Output](#input-and-output)
- [Files](#files)
- [Errors](#errors)
- [Grammar](#grammar)

## Overview

This document describes the language accepted by the compiler in this repository. Building and running the compiler is covered in the [README](README.md).

A program is a sequence of statements executed top to bottom. Declarations, expressions, `read` and `write` end with a semicolon, whitespace and line breaks carry no meaning, and the empty program is valid.

```
int n;
read n;
write "twice ", n * 2;
```

Comments run from `//` to the end of the line. There is no block comment form.

## Names

An identifier starts with an ASCII letter or an underscore and continues with letters, digits and underscores. Everything is case-sensitive, so `INT` and `True` are ordinary identifiers, not keywords.

These fourteen words are reserved and cannot name a variable:

```
int float bool string file true false read write if else while for open
```

Nothing else is reserved. `break`, `continue` and `return` do not exist in the language, but they are valid identifiers, so `int break;` compiles.

A keyword prefix does not poison a name. `intx` and `iffy` are ordinary identifiers, and `inta` is one identifier, not `int a`.

## Types and Declarations

The five types are `int`, `float`, `bool`, `string` and `file`.

- `int` - a 32-bit signed integer
- `float` - a double precision IEEE 754 number, despite the name
- `bool` - `true` or `false`
- `string` - text
- `file` - a handle for appending lines to a file

A declaration names a type and one or more variables. It cannot carry an initializer, so `int i = 0;` does not parse and must be written as two statements.

```
int i, j;
i = 0;
```

Variables must be declared before their first use, in textual order. Every variable except a `file` starts with a default value. An `int` starts at `0`, a `float` at `0.0`, a `bool` at `false` and a `string` empty. A `file` starts with no value at all, and using it before assigning to it stops the program with a runtime error.

There are only two implicit conversions. An `int` widens to `float` in arithmetic, comparisons and assignment, and a `string` assigned to a `file` variable opens the named file, described under [Files](#files). Nothing narrows back, nothing turns into a `string` by itself, and there is no truthiness.

## Literals

An `int` literal is a run of decimal digits. There is no hex, octal or exponent form, and leading zeros change nothing, so `007` is `7`. Every literal is checked against the 32-bit range at compile time. The minus sign is a separate operator, so `-2147483648` fails the check on `2147483648` and the smallest `int` must be written as `-2147483647 - 1`.

A `float` literal is digits, a dot and digits. Both sides of the dot are required and there is no exponent form, so `.5`, `1.` and `1e3` all fail to parse. Float literals are never range-checked, so `write 99999999999999999999.0;` prints `1.0E20` and a literal beyond the range of a double reads as `Infinity`.

A `bool` literal is lowercase `true` or `false`.

A string literal is double-quoted text on a single line. A raw line break inside a literal is a lexer error. The recognized escapes are `\n`, `\t`, `\r`, `\\` and `\"`. Any other character after a backslash is a compile error.

## Scope

A `{ ... }` block is a scope. A declaration inside it shadows an outer variable of the same name and disappears at the closing brace. The braceless body of an `if`, `else`, `while` or `for` is a scope of its own too, so a declaration there cannot leak out.

```
int a;
a = 1;
{
    float a;
    a = 2.5;
    write a;
}
write a;
```

prints `2.5` and then `1`.

Redeclaring a name in the same scope is a compile error. The `for` header is not a scope and its assignments touch variables of the surrounding scope, so the loop variable must be declared before the loop.

## Expressions

Operators from the tightest binding to the loosest. Binary operators of one level group left to right.

- `!` and unary `-` - logical not and numeric negation, prefixes stack freely
- `* / %` - multiplication, division and remainder
- `+ - .` - addition, subtraction and string concatenation
- `< > <= >=` - ordering
- `== !=` - equality
- `&&` - logical and
- `||` - logical or
- `<<` - file append
- `=` - assignment, greedy to the right, see [Assignment](#assignment)

### Arithmetic

`+`, `-`, `*` and `/` take `int` and `float` in any mix. When a `float` is involved the `int` side widens and the result is `float`, otherwise everything stays `int`. Integer division truncates toward zero, so `7 / 2` is `3` and `-7 / 2` is `-3`. `%` works on two ints only and takes the sign of the dividend, so `-7 % 2` is `-1`.

Integer arithmetic wraps silently on overflow, so `2147483647 + 1` prints `-2147483648` and no error is raised. Floats follow IEEE 754 double arithmetic, so `0.1 + 0.2` prints `0.30000000000000004` and a result too large for a double becomes `Infinity`.

Division and remainder by zero stop the program with a runtime error. This holds for `float` division too, where a zero divisor raises the error instead of producing infinity, and `-0.0` counts as zero.

### Strings

`.` concatenates two strings. Both operands must be strings and nothing is converted, so `"a" . 1` and `1 . 2` are compile errors. `+` never concatenates.

### Comparisons

`<`, `>`, `<=` and `>=` compare ints and floats in any mix and produce a `bool`. They accept nothing else. A chain like `1 < 2 < 3` parses but fails to type-check, because the left half already produced a `bool`.

`==` and `!=` compare two ints or floats in any mix, or two values of the same type for `bool` and `string`. A `file` cannot be compared at all.

### Logic

`&&` and `||` take two bools and always evaluate both sides. There is no short-circuiting, so this program dies with a division by zero even though its left side is already `false`:

```
int x;
write false && (1 / x == 0);
```

### Assignment

Assignment is an expression. It stores the value, and its own value is the assigned value, so assignments chain to the right and can sit inside a bigger expression.

```
int a, b, c;
a = b = c = 5;
while ((a = a - 1) > 0) write a;
```

prints `4` down to `1`.

The target must be a bare variable name, so `(a) = 5` does not parse. Grammatically an assignment is a primary expression, not a binary operator, so wherever it appears it swallows everything to its right, and `a + b = 5` parses as `a + (b = 5)`. Explicit parentheses remove the surprise.

An `int` value assigned to a `float` variable widens. Every other cross-type assignment is a compile error, except the `string` to `file` case described under [Files](#files).

### Missing operators

There is no `++`, `--`, `+=`, unary `+`, ternary `?:`, comma operator or any bitwise operator. `--5` is two negations and prints `5`, and `a--b` is `a - (-b)`.

## Statements

A statement is a declaration, an expression followed by a semicolon, a `read` or `write`, one of the control statements below, a block or the lone `;`. An expression statement discards its value, so `1 + 2;` compiles and does nothing.

### if and else

```
if (n % 2 == 0) write "even"; else write "odd";
```

The condition must be a `bool` in parentheses. The body is one statement, so a block is needed for more. An `else` binds to the nearest unmatched `if`.

### while

```
while (i < 3) {
    write i;
    i = i + 1;
}
```

The condition must be a `bool`.

### for

```
for (i = 0; i < 3; i = i + 1) write i;
```

The header is three parts separated by semicolons. The first and the last are single assignments to already declared variables and the middle is a `bool` condition. Each part may be empty, and an empty condition means forever, so `for (;;) ;` never ends. A declaration, a comma list or `i++` in the header does not parse. Since there is no `break`, a loop only ends through its condition.

### read and write

```
read a, b;
write "a is ", a, " and b is ", b;
```

Both take a bare comma-separated list without parentheses. `write(a, b);` does not parse, and `write(a);` parses only because `(a)` is a parenthesized expression. `read` targets must be declared variables of type `int`, `float`, `bool` or `string`. `write` accepts every type, including a `file`, which prints as `file(<name>)`.

## Input and Output

`write` joins its values with no separator and ends the line with the platform separator, CRLF on Windows. One statement, one line.

- an `int` prints in decimal
- a `bool` prints as `true` or `false`
- a `string` prints verbatim, with its escapes already decoded
- a `file` prints as `file(<name>)`
- a `float` prints in Java's shortest round-trip form, decimal for zero and for magnitudes from `0.001` up to but not including `1.0E7`, scientific outside, always with at least one fractional digit

```
write 3.0;          // prints 3.0
write 9999999.0;    // prints 9999999.0
write 10000000.0;   // prints 1.0E7
write 0.001;        // prints 0.001
write 0.0001;       // prints 1.0E-4
write 0.0;          // prints 0.0
```

`read` consumes one whole input line per variable, so `read a, b;` needs two lines. For `int`, `float` and `bool` the line is trimmed first. A `string` takes its line verbatim, spaces included, and never fails on content.

A `bool` accepts exactly `true` or `false` in any letter case. An `int` accepts an optional sign and decimal digits, and an out-of-range value is a runtime error, not a wrap. A `float` goes through the Java parser, which is far more forgiving than the source syntax and accepts `5`, `1e3`, `Infinity`, `NaN` and even hex float forms. Values read this way follow IEEE rules like any other float, so `NaN != NaN` is `true`.

Unparsable input stops the program with a runtime error quoting the raw line. Input ending early stops it too.

Console output uses the system console encoding. Files are always written in UTF-8.

## Files

A `file` value is created by `open`, an expression whose result is usually assigned to a `file` variable. Like any expression it can stand anywhere, so `open("log.txt", "a") << "x";` is legal, but the handle is lost unless it is saved.

```
file f;
f = open("log.txt", "w");
f << "attempt " << 42;
f << "done";
```

leaves `log.txt` holding the lines `attempt 42` and `done`.

Both arguments of `open` must be string literals, so a variable there does not parse. The mode must be `"w"` or `"a"` and anything else is a compile error. Mode `"w"` truncates or creates the file when the `open` runs, and later appends never truncate. Mode `"a"` creates the file if missing and keeps its content. Opening the same name with `"w"` again truncates again. A relative path is resolved against the working directory of the run.

Assigning a string to a `file` variable is a shorthand that opens the named file in append mode, so `f = "log.txt";` is `open("log.txt", "a")` in disguise.

Each `<<` chain statement writes exactly one line, the values joined with no separator in the same formats as `write`. The chain result is the file itself, which is why `<<` chains. Line endings follow the platform, CRLF on Windows.

A file handle is a plain value. `g = f;` makes both variables append to the same file, and every append reopens the file by name, writes the line and closes it, so the content is on disk after every statement and there is nothing to close. The language cannot read a file back, compare handles or delete files. The right side of `<<` must be an `int`, `float` or `string`, so a `bool` cannot be appended.

Using a declared but never assigned `file` variable is a runtime error. A path that cannot be opened stops the program with `Failed to open file` and a `Caused by:` line carrying the operating system's message.

## Errors

Anything that depends only on names and types is caught at compile time. Anything that depends on a value, such as a zero divisor, an input line, an unassigned file or a refused path, surfaces at run time.

Compile errors are reported as `line,col: message` on stderr, with 1-based lines and 0-based columns. Compilation has two phases. All syntax errors are reported first, then the compiler stops with `Aborted due to syntax errors.` and type checking never runs. With valid syntax, all type errors are collected and reported together, followed by `Aborted due to type errors.`. One broken subexpression produces one message, not a chain of follow-ups.

A runtime error prints `Runtime error: <message>` on stderr and stops the program, keeping the output written so far. The process exit code is `0` for success, `1` for anything that prevented the program from starting and `2` for a runtime error.

The compile-time messages, with type names spelled uppercase in the message text:

- `Variable 'x' is not declared.` - a name used without a declaration, reported once per name
- `Variable 'a' is already declared.` - a second declaration in the same scope
- `Variable 'a' type is INT, but the assigned value is FLOAT.` - incompatible assignment
- `Incompatible types in for-init: INT and FLOAT` - the same mistake in a `for` header, also reported for for-update
- `Condition in if statement must be bool, got INT.` - a non-bool condition, the `while` and `for` variants say `while loop` and `for loop`
- `Invalid operands for arithmetic operation: BOOL, INT` - arithmetic on a non-number
- `Modulo can be used only with integers.` - `%` with a float
- `String concatenation requires both operands to be strings. Got: INT, STRING` - `.` with a non-string
- `Relational operators are only valid for int or float. Got: STRING, STRING` - ordering non-numbers
- `Invalid types for equality: FILE, FILE` - `==` or `!=` on files or mismatched types
- `Logical operator '&&' requires bool operands. Got: INT, BOOL` - `&&` or `||` on non-bools
- `Logical '!' requires bool operand. Got: INT` - `!` on a non-bool
- `Unary minus requires int or float. Got: BOOL` - `-` on a non-number
- `Left side of '<<' must be of type FILE.` - appending to a non-file
- `Right side of '<<' must be INT, FLOAT or STRING. Got: BOOL` - appending a bool
- `Variable 'f' has unsupported type for read: FILE` - reading into a `file` variable
- `Integer literal out of range: 2147483648` - a literal beyond 32 bits
- `Unknown escape sequence '\q' in string literal` - a bad escape
- `File name in open() must not be empty.` - `open("", "w")`
- `File open mode must be "w" or "a". Got: "x"` - a bad mode literal

The runtime messages:

- `Division by zero` - `/` or `%` with a zero divisor, `float` included
- `Invalid int input: "abc"` - `read` got an unparsable line, also reported for float and bool
- `Input ended while reading int` - the input ran out, also reported for float, bool and string
- `Variable 'f' was never assigned a value` - a `file` used before assignment
- `Failed to open file: <name>` - the path cannot be created or truncated, with a `Caused by:` line
- `Failed to append to file: <name>` - a write failed midway, with a `Caused by:` line

## Grammar

The grammar in one notation. Quoted text is literal, a bar separates alternatives, brackets mark an optional part, braces mark zero or more repetitions and parentheses group alternatives.

```
program    = { statement } end-of-file

statement  = ";"
           | type name { "," name } ";"
           | expression ";"
           | "read" name { "," name } ";"
           | "write" expression { "," expression } ";"
           | "{" { statement } "}"
           | "if" "(" expression ")" statement [ "else" statement ]
           | "while" "(" expression ")" statement
           | "for" "(" [ name "=" expression ] ";" [ expression ] ";"
                       [ name "=" expression ] ")" statement

type       = "int" | "float" | "bool" | "string" | "file"

expression = or { "<<" or }
or         = and { "||" and }
and        = equality { "&&" equality }
equality   = relation { ( "==" | "!=" ) relation }
relation   = sum { ( "<" | ">" | "<=" | ">=" ) sum }
sum        = term { ( "+" | "-" | "." ) term }
term       = factor { ( "*" | "/" | "%" ) factor }
factor     = "!" factor
           | "-" factor
           | primary
primary    = name "=" expression
           | "open" "(" string "," string ")"
           | "(" expression ")"
           | name | integer | float | "true" | "false" | string
```

Binary operators are left-associative. Precedence comes from the way the rules nest, so the listing repeats the ladder under [Expressions](#expressions), loosest level first. Assignment is the one exception. The grammar treats it as a `primary`, so it can appear inside a larger expression, and the rest of the expression after the `=` belongs to it.

`integer`, `float`, `string` and `name` are tokens and are described in [Literals](#literals) and [Names](#names). A reserved word cannot be used as a `name`. `end-of-file` marks the end of the source file, and a stray token after the last statement is a syntax error. The rules say nothing about comments and whitespace because the lexer drops them before parsing. They can appear between any two tokens.
