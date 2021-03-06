package AppleCoreCompiler.Syntax;

import java.io.*;
import java.util.*;
import java.math.*;
import AppleCoreCompiler.AST.*;
import AppleCoreCompiler.AST.Node;
import AppleCoreCompiler.AST.Node.*;
import AppleCoreCompiler.Errors.*;
import AppleCoreCompiler.Syntax.*;

public class Parser {

    /**
     * Construct a new Parser object with the specified input file
     */
    public Parser(String sourceFileName) {
	this.sourceFileName = sourceFileName;
    }

    /**
     * The input file name
     */
    private String sourceFileName;

    /**
     * Scanner to provide the token stream
     */
    private Scanner scanner;

    /**
     * Main method for testing the parser
     */

    public static void main(String args[]) 
	throws ACCError, IOException
    {
	Parser parser = null;
	try {
	    parser = new Parser(args[0]);
	    parser.debug = true;
	    System.err.println("Parsing " + args[0] + "...");
	}
	catch (ArrayIndexOutOfBoundsException e) {
	    System.err.println("usage: java Parser [filename]");
	    System.exit(1);
	}
	SourceFile sourceFile = parser.parse();
	if (sourceFile != null) {
	    // Print out the AST
	    ASTPrintingPass app = new ASTPrintingPass(System.out);
	    app.runOn(sourceFile);
	}
    }

    /**
     * Parse an AppleCore source file.
     */
    public SourceFile parse() 
	throws ACCError, IOException
    {
	SourceFile sourceFile = null;
	FileReader fr = null;
	try {
	    fr = new FileReader(sourceFileName);
	    scanner = new Scanner(new BufferedReader(fr),
				  sourceFileName);
	    scanner.getNextToken();
	    sourceFile = parseSourceFile();
	}
	finally {
	    if (fr != null) fr.close();
	}
	return sourceFile;
    }

    /**
     * SourceFile ::= Decl*
     */
    private SourceFile parseSourceFile() 
	throws SyntaxError, IOException
    {
	SourceFile sourceFile = new SourceFile();
	sourceFile.name = sourceFileName;
	setLineNumberOf(sourceFile);
	while (scanner.getCurrentToken() != Token.END) {
	    Declaration decl = parseDecl();
	    if (decl == null) break;
	    sourceFile.decls.add(decl);
	}
	return sourceFile;
    }

    public boolean debug = false;
    private void printStatus(String s) {
	if (debug) {
	    System.err.print("Line " + scanner.getCurrentToken().lineNumber);
	    System.err.println(": " + s);
	}
    }

    private void printStatus() {
	printStatus("parsed " + scanner.getCurrentToken());
    }

    /**
     * Decl ::= Const-Decl | Data-Decl | Var-Decl | Fn-Decl 
     *          | Include-Decl
     */
    private Declaration parseDecl() 
	throws SyntaxError, IOException
    {
	Declaration result = null;
	Token token = scanner.getCurrentToken();
	switch (token) {
	case INCLUDE:
	    printStatus();
	    result = parseIncludeDecl();
	    break;
	case CONST:
	    printStatus();
	    result = parseConstDecl();
	    break;
	case DATA:
	    printStatus();
	    result = parseDataDecl();
	    break;
	case VAR:
	    printStatus();
	    result = parseVarDecl(false);
	    break;
	case FN:
	    printStatus();
	    scanner.getNextToken();
	    result = parseFunctionDecl();
	    break;
	default:
	    throw SyntaxError.expected("declaration",
				       sourceFileName,
				       token);
	}
	return result;
    }

    /**
     * Include-Decl ::= INCLUDE String-Const ';'
     */
    private IncludeDecl parseIncludeDecl() 
	throws SyntaxError, IOException
    {
	IncludeDecl includeDecl = new IncludeDecl();
	setLineNumberOf(includeDecl);
	expectAndConsume(Token.INCLUDE);
	expect(Token.STRING_CONST);
	includeDecl.filename = parseStringConstant().value;
	expectAndConsume(Token.SEMI);
	return includeDecl;
    }

    /**
     * Const-Decl ::= CONST Ident [Numeric-Const] ';'
     */
    private ConstDecl parseConstDecl() 
	throws SyntaxError, IOException
    {
	ConstDecl constDecl = new ConstDecl();
	setLineNumberOf(constDecl);
	expectAndConsume(Token.CONST);
	constDecl.label = parseName();
	if (scanner.getCurrentToken() != Token.SEMI) {
	    constDecl.expr = parseExpression();
	}
	expectAndConsume(Token.SEMI);
	return constDecl;
    }

    /**
     * Data-Decl ::= DATA [Ident] Const ';'
     */
    private DataDecl parseDataDecl() 
	throws SyntaxError, IOException
    {
	DataDecl dataDecl = new DataDecl();
	setLineNumberOf(dataDecl);
	expectAndConsume(Token.DATA);
	String name = parsePossibleName();
	Token token = scanner.getCurrentToken();
	if (token==Token.STRING_CONST) {
	    dataDecl.label = name;
	    dataDecl.stringConstant = parseStringConstant();
	    // Check for unterminated string
	    if (scanner.getCurrentToken() == Token.BACKSLASH) {
		scanner.getNextToken();
	    }
	    else {
		dataDecl.isTerminatedString = true;
	    }
	}
	else if (token==Token.SEMI) {
	    Identifier ident = new Identifier();
	    setLineNumberOf(ident);
	    ident.name = name;
	    dataDecl.expr = ident;
	}
	else {
	    dataDecl.label = name;
	    dataDecl.expr = parseExpression();
	}
	expectAndConsume(Token.SEMI);
	return dataDecl;
    }

    /**
     * Var-Decl ::= VAR Ident ':' Type ['=' Expr] ';'
     */
    private VarDecl parseVarDecl(boolean isLocalVariable)
	throws SyntaxError, IOException
    {
	printStatus("parsing variable declaration");
	VarDecl varDecl = new VarDecl();
	setLineNumberOf(varDecl);
	expectAndConsume(Token.VAR);
	varDecl.isLocalVariable = isLocalVariable;
	varDecl.name = parseName();
	expectAndConsume(Token.COLON);
	varDecl.type = parseType();
	if (scanner.getCurrentToken() == Token.EQUALS) {
	    scanner.getNextToken();
	    varDecl.init = parseExpression();
	}
	expectAndConsume(Token.SEMI);
	return varDecl;
    }

    /**
     * Fn-Decl ::= FN [':' Type] Ident '(' Params ')' Fn-Body
     * Fn-Body ::= '{' Var-Decl* Stmt* '}' | ';'
     */
    private FunctionDecl parseFunctionDecl()
	throws SyntaxError, IOException
    {
	FunctionDecl functionDecl = new FunctionDecl();
	setLineNumberOf(functionDecl);
	functionDecl.name = parseName();
	expectAndConsume(Token.LPAREN);
	parseFunctionParams(functionDecl.params);
	expectAndConsume(Token.RPAREN);
	if (scanner.getCurrentToken() == Token.COLON) {
	    scanner.getNextToken();
	    functionDecl.type = parseType();
	}
	else {
	    functionDecl.type = new Type();
	    setLineNumberOf(functionDecl.type);
	}
	if (scanner.getCurrentToken() == Token.LBRACE) {
	    scanner.getNextToken();
	    parseVarDecls(functionDecl.varDecls,true);
	    parseStatements(functionDecl.statements);
	    expectAndConsume(Token.RBRACE);
	}
	else {
	    expectAndConsume(Token.SEMI);
	    functionDecl.isExternal = true;
	}
	return functionDecl;
    }

    /**
     * Params ::= [ Param (',' Param)* ]
     * Param  ::= IDENT ':' Type
     */
    private void parseFunctionParams(List<VarDecl> params)
	throws SyntaxError, IOException
    {
	String name = parsePossibleName();
	while (name != null) {
	    printStatus("parsing function parameter");
	    VarDecl param = new VarDecl();
	    param.isLocalVariable = true;
	    param.isFunctionParam = true;
	    setLineNumberOf(param);
	    param.name = name;
	    expectAndConsume(Token.COLON);
	    param.type = parseType();
	    params.add(param);
	    name = null;
	    if (scanner.getCurrentToken() == Token.COMMA) {
		scanner.getNextToken();
		name = parseName();
	    }
	}
    }

    /**
     * Var-Decls ::= Var-Decl*
     */
    private void parseVarDecls(List<VarDecl> varDecls,
			       boolean isLocalVariable) 
	throws SyntaxError, IOException
    {
	while (scanner.getCurrentToken() == Token.VAR) {
	    VarDecl varDecl = parseVarDecl(isLocalVariable);
	    varDecls.add(varDecl);
	}
    }

    /**
     * Stmt-List ::= Stmt*
     */
    private void parseStatements(List<Statement> statements) 
	throws SyntaxError, IOException
    {
	while (scanner.getCurrentToken() != Token.RBRACE) {
	    Statement statement = parseStatement();
	    statements.add(statement);
	} 
    }

    /**
     * Stmt ::= If-Stmt | While-Stmt | Set-Stmt | Call-Stmt |
     *          Incr-Stmt | Decr-Stmt | Return-Stmt | Block-Stmt
     */
    private Statement parseStatement() 
	throws SyntaxError, IOException
    {
	Statement result = null;
	Token token = scanner.getCurrentToken();
	switch (token) {
	case IF:
	    result = parseIfStatement();
	    break;
	case WHILE:
	    result = parseWhileStatement();
	    break;
	case SET:
	    result = parseSetStatement();
	    break;
	case INCR:
	    result = parseIncrStatement();
	    break;
	case DECR:
	    result = parseDecrStatement();
	    break;
	case RETURN:
	    result = parseReturnStatement();
	    break;
	case LBRACE:
	    result = parseBlockStatement();
	    break;
	default:
	    result = parseCallStatement();
	    break;
	}
	return result;
    }

    /**
     * If-Stmt ::= IF '(' Expr ')' Stmt [ELSE Stmt]
     */
    private IfStatement parseIfStatement() 
	throws SyntaxError, IOException
    {
	IfStatement ifStmt = new IfStatement();
	setLineNumberOf(ifStmt);
	expectAndConsume(Token.IF);
	expectAndConsume(Token.LPAREN);
	ifStmt.test = parseExpression();
	expectAndConsume(Token.RPAREN);
	ifStmt.thenPart = parseStatement();
	if (scanner.getCurrentToken() == Token.ELSE) {
	    scanner.getNextToken();
	    ifStmt.elsePart = parseStatement();
	}
	return ifStmt;
    }

    /**
     * While-Stmt ::= WHILE '(' Expr ')' Stmt
     */
    private WhileStatement parseWhileStatement() 
	throws SyntaxError, IOException
    {
	WhileStatement whileStmt = new WhileStatement();
	setLineNumberOf(whileStmt);
	expectAndConsume(Token.WHILE);
	expectAndConsume(Token.LPAREN);
	whileStmt.test = parseExpression();
	expectAndConsume(Token.RPAREN);
	whileStmt.body = parseStatement();
	return whileStmt;
    }

    /**
     * Set-Stmt ::= SET Term '=' Expr ';'
     */
    private SetStatement parseSetStatement() 
	throws SyntaxError, IOException
    {
	SetStatement setStmt =  new SetStatement();
	setLineNumberOf(setStmt);
	expectAndConsume(Token.SET);
	setStmt.lhs = parseTerm();
	expectAndConsume(Token.EQUALS);
	setStmt.rhs = parseExpression();
	expectAndConsume(Token.SEMI);
	return setStmt;
    }

    /**
     * Incr-Stmt ::= INCR Expr ';'
     */
    private IncrStatement parseIncrStatement() 
	throws SyntaxError, IOException
    {
	IncrStatement incrStmt = new IncrStatement();
	setLineNumberOf(incrStmt);
	expectAndConsume(Token.INCR);
	incrStmt.expr = parseExpression();
	expectAndConsume(Token.SEMI);
	return incrStmt;
    }

    /**
     * Decr-Stmt ::= DECR Expr ';'
     */
    private DecrStatement parseDecrStatement() 
	throws SyntaxError, IOException
    {
	DecrStatement decrStmt = new DecrStatement();
	setLineNumberOf(decrStmt);
	expectAndConsume(Token.DECR);
	decrStmt.expr = parseExpression();
	expectAndConsume(Token.SEMI);
	return decrStmt;
    }

    /**
     * Return-Stmt ::= RETURN [Expr] ';'
     */ 
    private ReturnStatement parseReturnStatement()
	throws SyntaxError, IOException
    {
	ReturnStatement returnStmt = new ReturnStatement();
	setLineNumberOf(returnStmt);
	expectAndConsume(Token.RETURN);
	if (scanner.getCurrentToken() != Token.SEMI) {
	    returnStmt.expr = parseExpression();
	}
	expectAndConsume(Token.SEMI);
	return returnStmt;
    }

    /**
     * Block-Stmt ::= '{' Stmt-List '}'
     */
    private BlockStatement parseBlockStatement() 
	throws SyntaxError, IOException
    {
	BlockStatement blockStmt = new BlockStatement();
	setLineNumberOf(blockStmt);
	expectAndConsume(Token.LBRACE);
	parseStatements(blockStmt.statements);
	expectAndConsume(Token.RBRACE);
	return blockStmt;
    }

    /**
     * Call-Stmt ::= Call-Expr ';'
     */
    private Statement parseCallStatement() 
	throws SyntaxError, IOException
    {
	CallStatement callStmt = new CallStatement();
	setLineNumberOf(callStmt);
	Expression expr = parseTerm();
	if (!(expr instanceof CallExpression)) {
	    throw new SyntaxError("not a statement",
				  sourceFileName,
				  scanner.getLineNumber());
	}
	callStmt.expr = (CallExpression) expr;
	expectAndConsume(Token.SEMI);
	return callStmt;
    }

    /**
     * Expr ::= Binop-Expr | Term
     */
    private Expression parseExpression()
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	Expression result = parseTerm();
	while (true) {
	    // Check for binary op
	    Node.BinopExpression.Operator op =
		getBinaryOperatorFor(scanner.getCurrentToken());
	    if (op == null) {
		return result;
	    }
	    scanner.getNextToken();
	    result=parseBinopExpression(result, op);
	}
    }

    /**
     * Term ::= Identifier | Numeric-Const | Register-Expr |
     *          Indexed-Expr | Call-Expr | Unop-Expr |
     *          Parens-Expr | Typed-Expr
     */
    private Expression parseTerm()
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	Expression result = null;
	switch (token) {
	case IDENT:
	    result = parseIdentifier();
	    break;
	case CHAR_CONST:
	case INT_CONST:
	    result = parseNumericConstant();
	    break;
	case CARET:
	    result = parseRegisterExpr();
	    break;
	case LPAREN:
	    result = parseParensExpr();
	    break;
	default:
	    Node.UnopExpression.Operator op =
		getUnaryOperatorFor(token);
	    if (op != null) {
		result = parseUnopExpr(op);
	    }
	    break;
	}
	if (result == null) {
	    throw SyntaxError.expected("expression", 
				       sourceFileName,
				       token);
	}
	while (true) {
	    // Check for call expr
	    if (scanner.getCurrentToken() == 
		Token.LPAREN) {
		result = parseCallExpression(result);
	    }
	    // Check for indexed expr
	    else if (scanner.getCurrentToken() == 
		     Token.LBRACKET) {
		result = parseIndexedExpression(result);
	    }
	    // Check for typed expr
	    else if (scanner.getCurrentToken() == 
		     Token.COLON) {
		result = parseTypedExpression(result);
	    }
	    else return result;
	}
    }

    /**
     * IndexedExpr ::= Term '[' Expr ',' Type ']'
     */
    private IndexedExpression parseIndexedExpression(Expression indexed) 
	throws SyntaxError, IOException
    {
	IndexedExpression indexedExp = new IndexedExpression();
	setLineNumberOf(indexedExp);
	indexedExp.indexed = indexed;
	expectAndConsume(Token.LBRACKET);
	indexedExp.index = parseExpression();
	expectAndConsume(Token.COMMA);
	indexedExp.type = parseType();
	expectAndConsume(Token.RBRACKET);
	return indexedExp;
    }

    /**
     * TypedExpr ::= Expr ':' Type
     */
    private TypedExpression parseTypedExpression(Expression expr)
	throws SyntaxError, IOException
    {
	TypedExpression typedExp = new TypedExpression();
	setLineNumberOf(typedExp);
	typedExp.expr = expr;
	expectAndConsume(Token.COLON);
	typedExp.type = parseType();
	return typedExp;
    }

    /**
     * Call-Expr ::= Term '(' Args ')'
     * Args      ::= [Expr [',' Expr]*]
     */
    private CallExpression parseCallExpression(Expression fn)
	throws SyntaxError, IOException
    {
	CallExpression callExp = new CallExpression();
	copyLineNumber(fn, callExp);
	callExp.fn = fn;
	expectAndConsume(Token.LPAREN);
	if (scanner.getCurrentToken() == Token.RPAREN) {
	    printStatus();
	    scanner.getNextToken();
	    return callExp;
	}
	while (true) {
	    callExp.args.add(parseExpression());
	    if (scanner.getCurrentToken() != Token.COMMA)
		break;
	    scanner.getNextToken();
	}
	expectAndConsume(Token.RPAREN);
	return callExp;
    }

    /**
     * Register-Expr ::= '^' Reg-Name
     * Reg-Name      ::= 'X' | 'Y' | 'S' | 'P' | 'A'
     */
    private RegisterExpression parseRegisterExpr() 
	throws SyntaxError, IOException
    {
	RegisterExpression regExp = new RegisterExpression();
	setLineNumberOf(regExp);
	expectAndConsume(Token.CARET);
	Token token = scanner.getCurrentToken();
	Identifier ident = parseIdentifier();
	if (ident.name.length() == 1) {
	    char name = ident.name.charAt(0);
	    for (RegisterExpression.Register reg : 
		     RegisterExpression.Register.values()) {
		if (reg.name == name) {
		    regExp.register = reg;
		    return regExp;
		}
	    }
	}
	throw SyntaxError.expected("register name",
				   sourceFileName, token);
    }

    /**
     * Unop-Expr ::= Unop Term
     * Unop      ::= '@' | 'NOT' | '-'
     */
    private UnopExpression parseUnopExpr(Node.UnopExpression.Operator op) 
	throws SyntaxError, IOException
    {
	UnopExpression unopExpr = new UnopExpression();
	setLineNumberOf(unopExpr);
	unopExpr.operator = op;
	scanner.getNextToken();
	unopExpr.expr = parseTerm();
	return unopExpr;
    }

    /**
     * Return the unary operator corresponding to the token, null if
     * none.
     */
    private Node.UnopExpression.Operator getUnaryOperatorFor(Token token) {
	for (Node.UnopExpression.Operator op : 
		 Node.UnopExpression.Operator.values()) {
	    if (token.stringValue.equals(op.symbol))
		return op;
	}
	return null;
    }

    /**
     * Parens-Expr ::= '(' Expr ')'
     */
    private ParensExpression parseParensExpr() 
	throws SyntaxError, IOException
    {
	ParensExpression parensExp = new ParensExpression();
	setLineNumberOf(parensExp);
	expectAndConsume(Token.LPAREN);
	parensExp.expr = parseExpression();
	expectAndConsume(Token.RPAREN);
	return parensExp;
    }

    /**
     * Binop-Expr ::= Expr Binop Expr
     * Binop      ::= '=' | '>' | '<' | '<=' | '>=' | 
     *                'AND' | 'OR' | 'XOR' | '+' | '-' | 
     *                '*' | '/' | '<<' | '>>'
     */
    private BinopExpression parseBinopExpression(Expression left, 
						 Node.BinopExpression.Operator op) 
	throws SyntaxError, IOException
    {
	BinopExpression result = new BinopExpression();
	setLineNumberOf(result);
	result.left = left;
	result.operator = op;
	result.right = parseTerm();
	if (left instanceof BinopExpression) {
	    BinopExpression binop = (BinopExpression) left;
	    if (op.precedence < binop.operator.precedence) {
		// Switch precedence
		result.left = binop.right;
		binop.right = result;
		result = binop;
	    }
	}
	return result;
    }

    /**
     * Return the binary operator corresponding to the token, null if
     * none.
     */
    private Node.BinopExpression.Operator getBinaryOperatorFor(Token token) {
	for (Node.BinopExpression.Operator op : 
		 Node.BinopExpression.Operator.values()) {
	    if (token.stringValue.equals(op.symbol))
		return op;
	}
	return null;
    }

    /**
     * If token is there, consume it and return true.  Otherwise
     * return false.
     */
    private boolean parsePossibleToken(Token token) 
	throws SyntaxError, IOException
    {
	if (scanner.getCurrentToken() == token) {
	    scanner.getNextToken();
	    return true;
	}
	return false;	
    }

    /**
     * Look for an 'S' indicating a signed value.  If it's there
     * return true and advance the token stream; otherwise return
     * false.
     */
    private boolean parseIsSigned() 
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	if (token == Token.IDENT && token.stringValue.equals("S")) {
	    scanner.getNextToken();
	    return true;
	}
	return false;
    }

    /**
     * Parse and return an identifier if it's there.  If not, return
     * null.
     */
    private Identifier parsePossibleIdentifier() 
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	if (token == Token.IDENT) {
	    return parseIdentifier();
	}
	return null;
    }

    /**
     * Expect and consume an identifier token and return an AST node
     * for that token.
     */
    private Identifier parseIdentifier() 
	throws SyntaxError, IOException
    {
	expect(Token.IDENT);
	Token token = scanner.getCurrentToken();
	Identifier ident = new Identifier();
	setLineNumberOf(ident);
	ident.name = token.stringValue;
	scanner.getNextToken();
	return ident;
    }

    /**
     * Parse and return an identifier if it's there.  If not, return
     * null.
     */
    private String parsePossibleName() 
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	if (token == Token.IDENT) {
	    return parseName();
	}
	return null;
    }

    /**
     * Parse an identifier token and extract the name.
     */
    private String parseName() 
	throws SyntaxError, IOException
    {
	expect(Token.IDENT);
	Token token = scanner.getCurrentToken();
	String name = token.stringValue;
	scanner.getNextToken();
	return name;
    }

    /**
     * Parse a string constant
     */
    private StringConstant parseStringConstant()
	throws SyntaxError, IOException
    {
	StringConstant stringConstant = new StringConstant();
	setLineNumberOf(stringConstant);
	Token token = scanner.getCurrentToken();
	stringConstant.value = token.getStringValue();
	scanner.getNextToken();
	return stringConstant;
    }

    /**
     * Numeric-Const ::= Int-Const | Char-Const
     */
    private NumericConstant parseNumericConstant() 
	throws SyntaxError, IOException
    {
	IntegerConstant intConstant = parsePossibleIntConstant();
	if (intConstant != null) return intConstant;
	Token token = scanner.getCurrentToken();
	if (token != Token.CHAR_CONST) {
	    throw SyntaxError.expected("numeric constant", 
				       sourceFileName, token);
	}
	printStatus();
	CharConstant charConstant = new CharConstant();
	setLineNumberOf(charConstant);
	charConstant.value = token.stringValue.charAt(0);
	scanner.getNextToken();
	return charConstant;
    }

    /**
     * Parse an integer constant.
     */
    private IntegerConstant parseIntConstant() 
	throws SyntaxError, IOException
    {
	IntegerConstant result = parsePossibleIntConstant();
	if (result == null)
	    throw SyntaxError.expected ("integer constant", 
					sourceFileName,
					scanner.getCurrentToken());
	return result;
    }

    /**
     * Try to get an integer constant.  Return an integer constant
     * object on success, null on failure.
     */
    private IntegerConstant parsePossibleIntConstant() 
	throws SyntaxError, IOException
    {
	Token token = scanner.getCurrentToken();
	IntegerConstant result = null;
	switch (token) {
	case INT_CONST:
	    result = new IntegerConstant();
	    result.wasHexInSource = token.wasHexInSource;
	    break;
	}
	if (result != null) {
	    printStatus();
	    setLineNumberOf(result);
	    result.setValue(token.getNumberValue());
	    scanner.getNextToken();
	}
	return result;
    }

    /**
     * Type ::= Term [S] | '@'
     */
    private Type parseType()
	throws SyntaxError, IOException
    {
	printStatus("parsing type");
	Type type = new Type();
	setLineNumberOf(type);
	if (parsePossibleToken(Token.AT)) {
	    type.size = 2;
	    type.representsAddress = true;
	} 
	else {
	    type.sizeExpr = parseTerm();
	    type.isSigned = parseIsSigned();
	}
	return type;
    }

    /**
     * Check that the current token is the expected one; if not,
     * report an error
     */
    private void expect(Token token) 
	throws SyntaxError 
    {
	Token currentToken = scanner.getCurrentToken();
	if (token != currentToken)
	    throw SyntaxError.expected(token.expectedString(),
				       sourceFileName,
				       currentToken);
	printStatus();
    }

    /**
     * Check for the expected token, then consume it
     */
    private Token expectAndConsume(Token token)
	throws SyntaxError, IOException {
	expect(token);
	return scanner.getNextToken();
    }

    /**
     * Set the source file and line number of an AST node to the
     * current line recorded in the scanner.
     */
    private void setLineNumberOf(Node node) {
	node.sourceFileName = this.sourceFileName;
	node.lineNumber = scanner.getLineNumber();
    }

    /**
     * Copy source file and line number from one node to another
     */
    private void copyLineNumber(Node from, Node to) {
	to.sourceFileName = from.sourceFileName;
	to.lineNumber = from.lineNumber;
    }

}
