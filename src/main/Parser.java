package main;

import main.ast.*;

import java.util.*;

public class Parser {

    private final List<Token> tokens;
    private int pos = 0;
    static Map<String, Boolean> scope = new TreeMap<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private void error(String message) {
        if (pos < tokens.size()) {
            Token t = tokens.get(pos);
            throw new RuntimeException(message + " в позиции " + t.pos);
        } else {
            throw new RuntimeException(message + " в конце файла");
        }
    }

    private Token match(TokenType... expected) {
        if (pos < tokens.size()) {
            Token curr = tokens.get(pos);
            if (Arrays.asList(expected).contains(curr.type)) {
                pos++;
                return curr;
            }
        }
        return null;
    }

    private Token require(TokenType... expected) {
        Token t = match(expected);
        if (t == null)
            error("Ожидается " + Arrays.toString(expected));
        return t;
    }

    private ExprNode parseElem() {
        Token num = match(TokenType.TRUE, TokenType.FALSE);
        if (num != null)
            return new BooleanNode(num);
        Token id = match(TokenType.ID);
        if (id != null)
            return new VarNode(id);
        error("Ожидается число или переменная");
        return null;
    }

    private ExprNode parseParentheses() {
        if (match(TokenType.LPAR) != null) {
            ExprNode e = parseOrAndXor();
            require(TokenType.RPAR);
            return e;
        } else {
            return parseElem();
        }
    }

    private ExprNode parsePrint() {
        Token t;
        if ((t = match(TokenType.PRINT)) == null) {
            throw new IllegalStateException();
        }
        return new UnarOpNode(t, parseOrAndXor());
    }

    public ExprNode parseUnaryOps() {
        ExprNode e = parseParentheses();
        Token t = match(
                TokenType.ID,
                TokenType.TRUE,
                TokenType.FALSE,
                TokenType.NOT);
        if (t != null) {
            if (t.type == TokenType.TRUE || t.type == TokenType.FALSE || t.type == TokenType.ID) {
                pos--;
                if ((t = match(TokenType.NOT, TokenType.PRINT)) != null) {
                    e = new UnarOpNode(t, e);
                    require(TokenType.SEMICOLON);
                    return e;
                }
            } else {
                e = new UnarOpNode(t, parseParentheses());
                require(TokenType.SEMICOLON);
                return e;
            }

        } else {
            return e;
        }
        throw new IllegalStateException();
    }

    public ExprNode parseAssign() {
        if (match(TokenType.ID) == null) {
            ExprNode e = parsePrint();
            require(TokenType.SEMICOLON);
            return e;
        }
        pos--;
        ExprNode e1 = parseElem();
        Token op;
        if ((op = match(TokenType.ASSIGN)) != null) {
            ExprNode e2 = parseOrAndXor();
            e1 = new BinOpNode(op, e1, e2);
            require(TokenType.SEMICOLON);
            return e1;
        }
        pos--;
        return null;
    }

    public ExprNode parseAnd() {
        ExprNode e1 = parseUnaryOps();
        Token op;
        while ((op = match(TokenType.AND)) != null) {
            ExprNode e2 = parseUnaryOps();
            e1 = new BinOpNode(op, e1, e2);
        }
        return e1;
    }

    public ExprNode parseOrAndXor() {
        ExprNode e1 = parseAnd();
        Token op;
        while ((op = match(TokenType.OR, TokenType.XOR)) != null) {
            ExprNode e2 = parseAnd();
            e1 = new BinOpNode(op, e1, e2);
        }
        return e1;
    }

    public ExprNode parseExpression() {
        StatementsNode e1 = new StatementsNode();
        while (pos < tokens.size()) {
            ExprNode e2 = parseAssign();
            e1.addNode(e2);
        }
        return e1;

    }

    public static boolean eval(ExprNode node) {
        if (node instanceof BooleanNode) {
            BooleanNode num = (BooleanNode) node;
            return Boolean.parseBoolean(num.number.text);
        } else if (node instanceof BinOpNode) {
            BinOpNode binOp = (BinOpNode) node;
            switch (binOp.op.type) {
                case AND: return eval(binOp.left) && eval(binOp.right);
                case OR: return eval(binOp.left) || eval(binOp.right);
                case XOR: return eval(binOp.left) ^ eval(binOp.right);
                case ASSIGN:
                    VarNode var = (VarNode) binOp.left;
                    boolean result = eval(binOp.right);
                    scope.put(var.id.text, result);
                    return result;
            }
        } else if( node instanceof UnarOpNode){
            switch (((UnarOpNode) node).operator.type) {
                case PRINT:
                    System.out.println(eval(((UnarOpNode) node).operand));
                    return false;
                case NOT:
                    return !Boolean.parseBoolean(((UnarOpNode) node).operator.text);
            }
        } else if (node instanceof VarNode) {
            VarNode var = (VarNode) node;
            if (scope.containsKey(var.id.text)) {
                return scope.get(var.id.text);
            } else {
                throw new IllegalStateException();
            }
        } else if (node instanceof StatementsNode) {
            StatementsNode sn = (StatementsNode) node;
            for (ExprNode e : sn.getPipeline()) {
                eval(e);
            }
            return true;
        }
        throw new IllegalStateException();
    }

    public static void main(String[] args) {
        String text = "z := (true xor false) and true;" +
                      "print z;" +
                      "x := false or true;" +
                      "print x;" +
                      "z := false or true and false;" +
                      "print z;";

        Lexer l = new Lexer(text);
        List<Token> tokens = l.lex();
        tokens.removeIf(t -> t.type == TokenType.SPACE);

        Parser p = new Parser(tokens);
        ExprNode node = p.parseExpression();

        eval(node);
    }
}
