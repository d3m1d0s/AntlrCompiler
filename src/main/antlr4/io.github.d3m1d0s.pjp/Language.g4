grammar Language;

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

forInit: IDENTIFIER '=' expr | ;
forCond: expr?;
forUpdate: IDENTIFIER '=' expr | ;

primitiveType: INT_T | FLOAT_T | BOOL_T | STRING_T | FILE_T;

identifierList: IDENTIFIER (',' IDENTIFIER)*;

variableList: IDENTIFIER (',' IDENTIFIER)*;

exprList: expr (',' expr)*;

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


// === LEXER ===
INT_T: 'int';
FLOAT_T: 'float';
BOOL_T: 'bool';
STRING_T: 'string';
FILE_T: 'file';

BOOL: 'true' | 'false';
INT: [0-9]+;
FLOAT: [0-9]+ '.' [0-9]+;
STRING: '"' (~["\\\r\n] | '\\' ~[\r\n])* '"';
IDENTIFIER: [a-zA-Z_] [a-zA-Z_0-9]*;

// COMMENTS AND SKIPPED
LINE_COMMENT: '//' ~[\r\n]* -> skip;
WS: [ \t\r\n]+ -> skip;
