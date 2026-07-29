grammar Language;

// === Parser ===
program: statement* EOF;

statement
    : ';'                                                # emptyStatement
    | primitiveType variableList ';'                     # declaration
    | expr ';'                                           # expressionStatement
    | 'read' identifierList ';'                          # readStatement
    | 'write' exprList ';'                               # writeStatement
    | '{' statement* '}'                                 # blockStatement
    | 'if' '(' expr ')' statement ('else' statement)?    # ifStatement
    | 'while' '(' expr ')' statement                     # whileStatement
    | 'for' '(' forInit ';' forCond ';' forUpdate ')' statement  # forStatement
    ;

// each part of the for header may be empty, as in C
forInit: IDENTIFIER '=' expr | ;
forCond: expr?;
forUpdate: IDENTIFIER '=' expr | ;

primitiveType: INT_T | FLOAT_T | BOOL_T | STRING_T | FILE_T;

// same shape as variableList, kept separate so the visitors can tell read targets from declarations
identifierList: IDENTIFIER (',' IDENTIFIER)*;

variableList: IDENTIFIER (',' IDENTIFIER)*;

exprList: expr (',' expr)*;

// alternative order defines precedence: earlier alternatives bind tighter
expr
    : op='!' expr                                      # notExpr
    | op='-' expr                                      # unaryMinusExpr
    | left=expr op=('*' | '/' | '%') right=expr        # multiplicativeExpr
    | left=expr op=('+' | '-' | '.') right=expr        # additiveExpr
    | left=expr op=('<' | '>' | '<=' | '>=') right=expr # relationalExpr
    | left=expr op=('==' | '!=') right=expr            # equalityExpr
    | left=expr op='&&' right=expr                     # andExpr
    | left=expr op='||' right=expr                     # orExpr
    | left=expr op='<<' right=expr                     # fileAppendExpr
    | left=IDENTIFIER '=' right=expr                   # assignExpr
    | 'open' '(' STRING ',' STRING ')'                 # fileOpenExpr
    | '(' expr ')'                                     # parenExpr
    | IDENTIFIER                                       # idExpr
    | INT                                              # intExpr
    | FLOAT                                            # floatExpr
    | BOOL                                             # boolExpr
    | STRING                                           # stringExpr
    ;


// === Lexer ===

// keyword rules must precede IDENTIFIER, on equal-length matches the first rule wins
INT_T: 'int';
FLOAT_T: 'float';
BOOL_T: 'bool';
STRING_T: 'string';
FILE_T: 'file';

BOOL: 'true' | 'false';
// longest match beats rule order: "1.5" lexes as FLOAT even though INT comes first
INT: [0-9]+;
FLOAT: [0-9]+ '.' [0-9]+;
// any backslash pair is lexed, the type checker rejects unknown escapes
STRING: '"' (~["\\\r\n] | '\\' ~[\r\n])* '"';
IDENTIFIER: [a-zA-Z_] [a-zA-Z_0-9]*;

LINE_COMMENT: '//' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;
