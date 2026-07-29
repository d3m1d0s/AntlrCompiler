# ANTLR Compiler

[![Maven](https://github.com/d3m1d0s/PJP_antlr-compiler/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/d3m1d0s/PJP_antlr-compiler/actions/workflows/maven.yml)
![Java 17](https://img.shields.io/badge/Java-17-blue.svg)

## Overview

This is a compiler and stack machine interpreter for a small imperative language, built on ANTLR 4 and made as a semester project for the PJP course (Programming Languages and Compilers) at VSB-TUO. It parses a program, checks the types, generates stack machine instructions and executes them.

The language has `int`, `float`, `bool`, `string` and `file` variables, C-like control flow with block scoping, console input and output, and file writing through the `<<` operator.

The compiler grew out of the ANTLR interpreter labs in [PJP_practical-classes](https://github.com/d3m1d0s/PJP_practical-classes).

## Build and Run

To run the compiler, ensure JDK 17 and Maven are installed and execute:

```
mvn compile exec:java "-Dexec.args=program.lang"
```

The compiler can also be started from an IDE by running the `io.github.d3m1d0s.pjp.App` class with the program path as its argument.

## Usage

The path to the program is the only argument. Sample programs live in `src/test/resources`:

- `example.lang` - the program shown below
- `test.lang` - a small sample program working with files
- `PLC_t1.in` to `PLC_t3.in` - the course reference programs, each with its expected instruction listing in the matching `.out` file

The compiler executes the generated instructions from memory. Their listing is also saved to `output.out` in the working directory (the project root when run through Maven). The program's output goes to stdout and all diagnostics go to stderr. Compile errors are reported as `line,col: message` lines.

The exit code reports the outcome:

- `0` - the program ran to completion
- `1` - wrong usage, an unreadable source or compile errors
- `2` - the program failed at run time

The test suite runs with `mvn test` and also compares the generated listings with the course reference outputs.

## Language

The five types are `int`, `float`, `bool`, `string` and `file`. `int` promotes to `float` automatically and there is no conversion back. Floats have double precision. Integer literals are checked against the 32-bit range at compile time.

Statements are declarations (`int a, b;`), expression statements, `read` and `write`, `if` with an optional `else`, `while`, a C-style `for` whose header parts may all be empty, blocks and the empty statement `;`. Blocks have real scopes. A declaration shadows an outer one and disappears with its block, and a braceless body of `if`, `else`, `while` or `for` counts as a block of its own.

Operators, tightest binding first:

- `!` and unary `-` - logical not and numeric negation
- `* / %` - multiplication, division and remainder (`%` is integer only)
- `+ - .` - addition, subtraction and string concatenation
- `< > <= >=` - ordering
- `== !=` - equality, also for `bool` and `string`
- `&&` - logical and
- `||` - logical or
- `<<` - file append
- `=` - assignment, an expression that chains to the right

`&&` and `||` always evaluate both operands. Numeric operators other than `%` take `int` and `float` in any combination. Division and modulo by zero are runtime errors, and `float` division is no exception.

String literals stay on one line and understand the `\n`, `\t`, `\r`, `\\` and `\"` escapes. Any other character after a backslash is a compile error.

`read` takes one input line per variable and accepts only `true` and `false` for a `bool`, regardless of case. Invalid input stops the program with a runtime error. `write` prints all its values on one line without separators.

`open` takes two string literals, the file name and the mode. `"w"` truncates the file once, when it is opened, and `"a"` keeps its content. Every `<<` chain appends one line. Assigning a string to a `file` variable opens the named file in append mode, and handles can be copied between `file` variables.

Comments are `//` line comments. The keywords `int float bool string file true false read write if else while for open` are reserved.

## Example

`src/test/resources/example.lang` holds a small counting loop:

```
int i;
i = 1;
while (i <= 3) {
    write "i = ", i;
    i = i + 1;
}
write "done";
```

Run it from the project root:

```
mvn compile exec:java "-Dexec.args=src/test/resources/example.lang"
```

The program prints

```
i = 1
i = 2
i = 3
done
```

and leaves this listing in `output.out`:

```
push I 0
save i
push I 1
save i
load i
pop
label 0
load i
push I 3
le I
fjmp 1
push S "i = "
load i
print 2
load i
push I 1
add I
save i
load i
pop
jmp 0
label 1
push S "done"
print 1
```

## Project Structure

```
src/main/antlr4/io.github.d3m1d0s.pjp/          the ANTLR grammar
src/main/java/io/github/d3m1d0s/pjp/            driver, type checker and shared classes
src/main/java/io/github/d3m1d0s/pjp/codegen/    instruction model and code generator
src/main/java/io/github/d3m1d0s/pjp/runtime/    the stack machine
src/test/java/io/github/d3m1d0s/pjp/            the JUnit test suite
src/test/resources/                             sample programs and reference listings
pom.xml
```

## Future Development

The planned next step is a debug metadata table next to the generated code, similar to the JVM's `LineNumberTable`. The instructions stay as they are, and a parallel table maps each one back to its source line and the variable it touches, so a runtime error could point at the place that caused it. Smaller candidates are `break` and `continue`, and a richer set of string escapes.

## License

This project is authored by Demid Ostiakov. All rights reserved.

## Acknowledgments

Thanks to the instructors of the PJP course at VSB-TUO:

- Ing. Marek Běhálek, Ph.D. for the lectures and the course materials this project is built on
- Ing. Michal Vašinek, Ph.D. for leading the exercises and for his advice along the way

Thanks also to the instructors of UTI (Introduction to Theoretical Computer Science), where the theory of formal languages and automata behind this project comes from:

- doc. Ing. Zdeněk Sawa, Ph.D. for the lectures and his presentations covering the whole course
- doc. Mgr. Pavla Dráždilová, Ph.D. for leading the exercises and her clear and illustrative explanations
