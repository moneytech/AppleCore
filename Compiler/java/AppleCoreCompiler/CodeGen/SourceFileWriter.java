/**
 * Generic pass for traversing the AST and writing out assembly code.
 *
 * Code generation uses the following registers in zero-page memory:
 *
 * - Stack pointer, SP (2 bytes): Points to first free byte after
 *   stack top
 * - Frame pointer FP (2 bytes): Points to start of current frame in
 *   program stack.
 * - Index pointer IP (2 bytes): Points to memory location being
 *   indexed.
 *
 * The code generator depends on support code provided elsewhere.  The
 * support code uses a few additional zero-page registers.
 */
package AppleCoreCompiler.CodeGen;

import AppleCoreCompiler.AST.*;
import AppleCoreCompiler.AST.Node.*;
import AppleCoreCompiler.Errors.*;
import AppleCoreCompiler.AST.Node.RegisterExpression.Register;
import AppleCoreCompiler.AVM.*;
import AppleCoreCompiler.AVM.Instruction.*;

import java.io.*;
import java.util.*;
import java.math.*;

public class SourceFileWriter
    extends ASTScanner 
    implements Pass
{

    public enum Mode {
	NATIVE,AVM;
    }

    /* Initialization stuff */
    public final NativeCodeEmitter emitter;
    public final Mode mode;

    public SourceFileWriter(NativeCodeEmitter emitter,
			    Mode mode) {
	this.emitter = emitter;
	this.mode = mode;
    }

    public void runOn(SourceFile sourceFile) 
	throws ACCError
    {
	scan(sourceFile);
    }

    /* State variables for tree traversal */

    /**
     * Whether to print verbose comments
     */
    public boolean printVerboseComments = false;

    /**
     * The function being processed
     */
    protected FunctionDecl currentFunction;

    /**
     * Counter for branch labels
     */
    protected int branchLabelCount = 1;

    /**
     * Counter for constants
     */
    protected int constCount = 1;

    /**
     * Whether a variable seen is being evaluated for its address or
     * its value.
     */
    protected boolean needAddress;

    /**
     * Are we in debug mode?
     */
    public boolean debug = false;

    protected final Map<IntegerConstant,String> constMap =
	new HashMap<IntegerConstant,String>();
    
    protected String getLabelFor(IntegerConstant ic) {
	String s = constMap.get(ic);
	if (s != null) return s;
	s = mangle("CONST."+constCount++);
	constMap.put(ic,s);
	return s;
    }

    /* Visitor methods */

    /* Leaf nodes */

    public void visitIntegerConstant(IntegerConstant node) {
	emitVerboseComment(node);
	IntegerConstant intConst = (IntegerConstant) node;
	int size = intConst.getSize();
	if (size < 5) {
	    for (int i = 0; i < size; ++i) {
		emitter.emitImmediateInstruction("LDA",intConst.valueAtIndex(i));
		emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    }
	}
	else {
	    String label = getLabelFor(intConst);
	    emitter.emitImmediateInstruction("LDA",label,false);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    emitter.emitImmediateInstruction("LDA",label,true);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    emitter.emitImmediateInstruction("LDA",size);
	    emitter.emitAbsoluteInstruction("JSR","ACC.EVAL");
	}
    }

    public void visitCharConstant(CharConstant node) {
	emitVerboseComment(node);
	CharConstant charConst = (CharConstant) node;
	emitter.emitImmediateInstruction("LDA",charConst.value);
	emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");	    
    }

    public void visitIdentifier(Identifier node) 
	throws ACCError 
    {
	Node def = node.def;
	if (def instanceof VarDecl) {
	    VarDecl varDecl = (VarDecl) def;
	    if (needAddress) {
		// Push variable's address on stack
		pushVarAddr(varDecl);
	    }
	    else {
		if (varDecl.isLocalVariable && varDecl.size == 1) {
		    emitVerboseComment("value of " + node);
		    // Fast case:  reading a local variable
		    emitter.emitImmediateInstruction("LDY",varDecl.getOffset());
		    emitter.emitIndirectYInstruction("LDA","ACC.FP");
		    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
		}
		else {
		    // Push variable's address on stack
		    pushVarAddr(varDecl);
		    // Use the address to get the value.
		    emitVerboseComment("value at address");
		    emitter.emitImmediateInstruction("LDA",node.size);
		    emitter.emitAbsoluteInstruction("JSR","ACC.EVAL");
		}
	    }
	}
	else if (def instanceof ConstDecl) {
	    emitVerboseComment(node);
	    ConstDecl cd = (ConstDecl) def;
	    scan(cd.expr);
	}
	else if (def instanceof DataDecl) {
	    emitVerboseComment(node);
	    DataDecl dataDecl = (DataDecl) def;
	    emitter.emitImmediateInstruction("LDA",
					     emitter.makeLabel(dataDecl.label),
					     false);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    emitter.emitImmediateInstruction("LDA",
					     emitter.makeLabel(dataDecl.label),
					     true);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	}
	else if (def instanceof FunctionDecl) {
	    emitVerboseComment(node);
	    FunctionDecl functionDecl = (FunctionDecl) def;
	    emitter.emitImmediateInstruction("LDA",
					     emitter.makeLabel(functionDecl.name),
					     false);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    emitter.emitImmediateInstruction("LDA",
					     emitter.makeLabel(functionDecl.name),
					     true);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	}
    }

    /* Non-leaf nodes */

    public void visitSourceFile(SourceFile node) 
	throws ACCError
    {
	emitter.emitPreamble(node);
	emitter.emitSeparatorComment();
	emitter.emitComment("Assembly file generated by");
	emitter.emitComment("the AppleCore Compiler, v1.0");
	emitter.emitSeparatorComment();
	if (!node.includeMode) {
	    emitter.emitAbsoluteInstruction(".IN","ACC.PROLOGUE");
	    FunctionDecl firstFunction = null;
	    for (Declaration decl : node.decls) {
		if (decl instanceof FunctionDecl) {
		    FunctionDecl functionDecl = (FunctionDecl) decl;
		    if (firstFunction == null && !functionDecl.isExternal) {
			firstFunction = (FunctionDecl) decl;
		    }
		}
	    }
	    if (firstFunction != null) {
		emitVerboseComment("initial carriage return");
		emitter.emitAbsoluteInstruction("JSR","$FD8E");
		emitVerboseComment("put DOS in deferred mode");
		emitter.emitAbsoluteInstruction("LDA","$AAB6");
		emitter.emitInstruction("PHA");
		emitter.emitImmediateInstruction("LDY",0);
		emitter.emitAbsoluteInstruction("STY","$AAB6");
		emitter.emitInstruction("DEY");
		emitter.emitAbsoluteInstruction("STY","$D9");
		emitVerboseComment("do main function");
		emitter.emitAbsoluteInstruction("JSR",
						emitter.makeLabel(firstFunction.name));
		emitVerboseComment("put DOS back in direct mode");
		emitter.emitInstruction("PLA");
		emitter.emitAbsoluteInstruction("STA","$AAB6");
		emitter.emitImmediateInstruction("LDY",0);
		emitter.emitAbsoluteInstruction("STY","$D9");
		emitVerboseComment("exit to BASIC");
		emitter.emitAbsoluteInstruction("JMP","$3D3");
	    }
	    emitter.emitSeparatorComment();
	}
	emitter.emitComment("START OF FILE " + node.name);
	emitter.emitSeparatorComment();
	
	super.visitSourceFile(node);

	if (constMap.size() > 0) {
	    emitter.emitSeparatorComment();
	    emitter.emitComment("LARGE CONSTANTS");
	    emitter.emitSeparatorComment();
	    for (Map.Entry<IntegerConstant,String> entry :
		     constMap.entrySet()) {
		emitter.emitLabel(entry.getValue());
		emitter.emitAsData(entry.getKey());
	    }
	}
	emitter.emitSeparatorComment();
	emitter.emitComment("END OF FILE " + node.name);
	emitter.emitSeparatorComment();
	if (!node.includeMode) {
	    emitter.emitEpilogue();
	}
    }

    public void visitConstDecl(ConstDecl node) {
	// Do nothing
    }

    public void visitFunctionDecl(FunctionDecl node) 
	throws ACCError
    {
	if (!node.isExternal) {
	    emitter.emitSeparatorComment();
	    emitter.emitComment("function " + node.name);
	    emitter.emitSeparatorComment();
	    
	    printStatus("entering " + node);
	    
	    if (mode == Mode.NATIVE) {
		// TODO: Generate native code by scanning the AVM
		// instructions, instead of scanning the AST again.
		generateNativeCodeFor(node);
	    }
	    else {
		generateAVMCodeFor(node);
	    }
	}
    }

    private void generateNativeCodeFor(FunctionDecl node) 
	throws ACCError
    {
	currentFunction = node;
	branchLabelCount=1;
	
	computeStackSlotsFor(node);
	
	emitter.emitLabel(node.name);
	emitter.emitLine();
	
	emitVerboseComment("skip slot for AVM ret addr");
	emitter.emitImmediateInstruction("LDA",2);
	emitter.emitAbsoluteInstruction("JSR","ACC.SP.UP.A");

	emitVerboseComment("push return address");
	emitter.emitInstruction("PLA");
	emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	emitter.emitInstruction("PLA");
	emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	
	emitVerboseComment("push old FP");
	emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.FP");
	
	emitVerboseComment("set new FP");
	emitter.emitAbsoluteInstruction("JSR","ACC.SET.FP.TO.SP");
	
	if (node.frameSize > 0) {
	    emitVerboseComment("bump stack to top of frame");
	    emitter.emitImmediateInstruction("LDA",node.frameSize);
	    emitter.emitAbsoluteInstruction("JSR","ACC.SP.UP.A");
	}
	
	scan(node.varDecls);
	scan(node.statements);
	
	if (!node.endsInReturnStatement()) {
	    emitVerboseComment("restore old frame and return");
	    emitter.emitAbsoluteInstruction("JMP","ACC.FN.RETURN.VOID");
	}
    }

    private void generateAVMCodeFor(FunctionDecl node) 
	throws ACCError
    {
	for (Instruction inst : node.instructions) {
	    if (!(inst instanceof LabelInstruction) &&
		!(inst instanceof CommentInstruction)) {
		emitter.printStream.print("\t");
	    }
	    emitter.printStream.println(emitter.makeLabel(inst.toString()));
	}
    }

    public void visitIncludeDecl(IncludeDecl node) {
	emitter.emitIncludeDecl(node);
    }

    public void visitDataDecl(DataDecl node) 
	throws ACCError
    {
	if (node.label != null)
	    emitter.emitLabel(node.label);
	if (node.stringConstant != null) {
	    emitter.emitStringConstant(node.stringConstant);
	    if (node.isTerminatedString) {
		emitter.emitStringTerminator();
	    }
	}
	else {
	    // Previous passes must ensure cast will succeed.
	    if (!(node.expr instanceof NumericConstant)) {
		throw new ACCInternalError("non-constant data for ", node);
	    }
	    NumericConstant nc = 
		(NumericConstant) node.expr;
	    emitter.emitAsData(nc);
	}
    }

    public void visitVarDecl(VarDecl node) 
	throws ACCError
    {
	if (node.isLocalVariable) {
	    if (node.init != null) {
		// We have an initializer expression
		// Evaluate intializer expr
		needAddress = false;
		scan(node.init);
		// Adjust the size
		adjustSize(node.size,node.init.size,node.init.isSigned);
		// Do the assignment.
		emitVerboseComment("initialize " + node);
		emitter.emitImmediateInstruction("LDA",node.getOffset());
		emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.SLOT");
		assign(node.size);
	    }
	}
	else {
	    emitter.emitLabel(node.name);
	    if (node.init == null) {
		emitter.emitBlockStorage(node.size);
	    }
	    else {
		// Previous passes must ensure cast will succeed.
		if (!(node.init instanceof NumericConstant)) {
		    throw new ACCInternalError("non-constant initializer expr for ", node);
		}
		NumericConstant constant = 
		    (NumericConstant) node.init;
		emitter.emitAsData(constant, node.size);
	    }
	}
    }

    public void visitIfStatement(IfStatement node) 
	throws ACCError
    {
	// Evaluate the test condition
	needAddress = false;
	scan(node.test);
	// Do the branch
	emitVerboseComment("if condition test");
	String label = getLabel();
	emitter.emitImmediateInstruction("LDA",node.test.size);
	emitter.emitAbsoluteInstruction("JSR","ACC.BOOLEAN");
	emitter.emitAbsoluteInstruction("BNE",emitter.makeLabel(mangle("true"+label)));
	emitter.emitAbsoluteInstruction("JMP",emitter.makeLabel(mangle("false"+label)));
	// True part
	emitter.emitLabel(mangle("true"+label));
	emitter.emit("\n");
	scan(node.thenPart);
	if (node.elsePart != null)
	    emitter.emitAbsoluteInstruction("JMP",emitter.makeLabel("endif" + label));
	// False part
	emitter.emitLabel(mangle("false"+label));
	emitter.emit("\n");
	if (node.elsePart != null) {
	    scan(node.elsePart);
	    emitter.emitLabel("endif"+label);
	    emitter.emit("\n");
	}
    }

    private String mangle(String s) {
	return currentFunction.name+"."+s;
    }

    public void visitWhileStatement(WhileStatement node) 
	throws ACCError
    {
	// Evaluate the test condition
	String label = getLabel();
	needAddress = false;
	emitter.emitLabel(mangle("test" + label));
	emitter.emit("\n");
	scan(node.test);
	emitVerboseComment("while loop test");
	// Do the branch
	emitter.emitImmediateInstruction("LDA",node.test.size);
	emitter.emitAbsoluteInstruction("JSR","ACC.BOOLEAN");
	emitter.emitAbsoluteInstruction("BNE",emitter.makeLabel(mangle("true" + label)));
	emitter.emitAbsoluteInstruction("JMP",emitter.makeLabel(mangle("false" + label)));
	// True part
	emitter.emitLabel(mangle("true" + label));
	emitter.emit("\n");
	scan(node.body);
	emitter.emitAbsoluteInstruction("JMP",emitter.makeLabel(mangle("TEST"+label)));
	// False part
	emitter.emitLabel(mangle("false" + label));
	emitter.emit("\n");
    }
    
    public void visitExpressionStatement(ExpressionStatement node) 
	throws ACCError
    {
	// Nothing special to do 
	super.visitExpressionStatement(node);
    }

    public void visitReturnStatement(ReturnStatement node)
	throws ACCError 
    {
	if (node.expr != null) {
	    // Evaluate expression
	    needAddress = false;
	    scan(node.expr);
	    adjustSize(currentFunction.size,node.expr.size,
		       node.expr.isSigned);
	    // Assign result and restore frame
	    emitVerboseComment("assign result, restore frame, and exit");
	    emitter.emitImmediateInstruction("LDA",currentFunction.size);
	    emitter.emitAbsoluteInstruction("JMP","ACC.FN.RETURN");
	}
	else {
	    emitVerboseComment("restore old frame and return");
	    emitter.emitAbsoluteInstruction("JMP","ACC.FN.RETURN.VOID");
	}
    }

    public void visitBlockStatement(BlockStatement node) 
	throws ACCError
    {
	// Nothing special to do
	super.visitBlockStatement(node);
    }

    public void visitIndexedExpression(IndexedExpression node)
	throws ACCError
    {
	// Record whether our result should be an address.
	boolean parentNeedsAddress = needAddress;
	// Evaluate indexed expr.
	needAddress = false;
	scan(node.indexed);
	// Pad to 2 bytes if necessary
	adjustSize(2,node.indexed.size,false);
	if (!node.index.isZero()) {
	    // Evaluate index expr.
	    needAddress = false;
	    scan(node.index);
	    // Pad to 2 bytes if necessary
	    adjustSize(2,node.index.size,node.index.isSigned);
	    // Pull LHS address and RHS index, add them, and put
	    // result on the stack.
	    emitVerboseComment("index");
	    emitter.emitImmediateInstruction("LDA",2);
	    emitter.emitAbsoluteInstruction("JSR","ACC.BINOP.ADD");
	}
	// If parent wanted a value, compute it now.
	if (!parentNeedsAddress) {
	    emitVerboseComment("value at index");
	    emitter.emitImmediateInstruction("LDA",node.size);
	    emitter.emitAbsoluteInstruction("JSR","ACC.EVAL");
	}
    }

    public void visitCallExpression(CallExpression node) 
	throws ACCError
    {
	boolean needIndirectCall = true;
	if (node.fn instanceof Identifier) {
	    Identifier id = (Identifier) node.fn;
	    Node def = id.def;
	    if (def instanceof FunctionDecl) {
		emitCallToFunctionDecl((FunctionDecl) def,
				       node.args);
		needIndirectCall = false;
	    }
	    else if (def instanceof ConstDecl ||
		     def instanceof DataDecl) {
		emitCallToConstant(emitter.makeLabel(id.name));
		needIndirectCall = false;
	    }
	}
	else if (node.fn instanceof NumericConstant) {
	    NumericConstant nc = (NumericConstant) node.fn;
	    emitCallToConstant(nc.valueAsHexString());
	    needIndirectCall = false;
	}
	// If all else failed, use indirect call
	if (needIndirectCall) {
	    emitIndirectCall(node.fn);
	}
    }

    /**
     * Call to declared function: push args, restore regs, call, and
     * save regs.
     */
    private void emitCallToFunctionDecl(FunctionDecl functionDecl,
					List<Expression> args) 
	throws ACCError
    {
	// Fill in the arguments, if any
	Iterator<VarDecl> I = functionDecl.params.iterator();
	if (args.size() > 0) {
	    emitVerboseComment("fill slots for new frame");
	    // Save bump size for undo
	    int bumpSize = AVMTranslatorPass.SAVED_INFO_SIZE;
	    // Save place for callee to save info
	    emitter.emitImmediateInstruction("LDA",bumpSize);
	    emitter.emitAbsoluteInstruction("JSR","ACC.SP.UP.A");
	    for (Expression arg : args) {
		VarDecl param = I.next();
		emitVerboseComment("bind arg to " + param);
		// Evaluate the argument
		needAddress = false;
		scan(arg);
		// Adjust sizes to match.
		adjustSize(param.size,arg.size,arg.isSigned);
		bumpSize += param.size;
	    }
	    emitVerboseComment("set SP for new frame");
	    // Bump SP back down to new FP
	    emitter.emitImmediateInstruction("LDA",bumpSize);
	    emitter.emitAbsoluteInstruction("JSR","ACC.SP.DOWN.A");
	}
	restoreRegisters();
	emitVerboseComment("function call");
	emitter.emitAbsoluteInstruction("JSR",
					emitter.makeLabel(functionDecl.name));
	saveRegisters();
    }
    
    /**
     * Calling a constant address: restore regs, call, and save regs.
     */
    private void emitCallToConstant(String addr) {
	restoreRegisters();
	emitVerboseComment("function call");
	emitter.emitAbsoluteInstruction("JSR",addr);
	saveRegisters();
    }

    /**
     * Indirect function call: evaluate expression, restore regs,
     * call, and save regs.
     */
    private void emitIndirectCall(Expression node) 
	throws ACCError
    {
	needAddress = false;
	scan(node);
	adjustSize(2,node.size,node.isSigned);
	emitter.emitAbsoluteInstruction("JSR","ACC.POP.IP");
	restoreRegisters();
	emitVerboseComment("function call");
	emitter.emitAbsoluteInstruction("JSR","ACC.INDIRECT.CALL");
	saveRegisters();
    }

    public void visitRegisterExpression(RegisterExpression node) 
	throws ACCError
    {
	if (needAddress) {
	    emitVerboseComment("address of slot for " + node.register);
	    emitter.emitImmediateInstruction("LDA",node.register.getOffset());
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.SLOT");
	} else {
	    emitVerboseComment("value in slot for " + node.register);
	    emitter.emitImmediateInstruction("LDY",node.register.getOffset());
	    emitter.emitIndirectYInstruction("LDA","ACC.FP");
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	}
    }

    public void visitSetExpression(SetExpression node) 
	throws ACCError
    {
	// Evaluate RHS as value
	needAddress = false;
	scan(node.rhs);
	adjustSize(node.lhs.getSize(),node.rhs.getSize(),
		   node.rhs.isSigned);
	// Evaluate LHS as address.
	needAddress = true;
	scan(node.lhs);
	// Store RHS to (LHS)
	assign(node.lhs.getSize());
    }

    public void visitBinopExpression(BinopExpression node) 
	throws ACCError
    {
	int size = Math.max(node.left.size,node.right.size);
 	// Evaluate left
	needAddress = false;
	scan(node.left);
	adjustSize(size,node.left.size,node.left.isSigned);
 	// Evaluate right
	needAddress = false;
	scan(node.right);
	if (node.operator.compareTo(BinopExpression.Operator.SHR) > 0) {
	    adjustSize(size,node.right.size,node.right.isSigned);
	}
	emitVerboseComment(node);
	if (node.hasSignedness()) {
	    // Load signedness
	    if (node.left.isSigned || node.right.isSigned) {
		emitVerboseComment("signed");
		emitter.emitImmediateInstruction("LDX",1);
	    }
	    else {
		emitVerboseComment("unsigned");
		emitter.emitImmediateInstruction("LDX",0);
	    }
	}
	// Load size
	emitter.emitImmediateInstruction("LDA",size);
	// Do the operation
	emitter.emitAbsoluteInstruction("JSR","ACC.BINOP."+
				node.operator.name);
    }

    public void visitUnopExpression(UnopExpression node) 
	throws ACCError
    {
	switch(node.operator) {
	case DEREF:
	    // Evaluate expr as address.
	    emitVerboseComment(node);
	    needAddress = true;
	    scan(node.expr);
	    break;
	case NOT:
	case NEG:
	    // Evaluate expr as value.
	    needAddress = false;
	    scan(node.expr);
	    emitVerboseComment(node);
	    // Do the operation.
	    emitter.emitImmediateInstruction("LDA",node.expr.size);
	    emitter.emitAbsoluteInstruction("JSR","ACC.UNOP."+node.operator.name);
	    break;
	case INCR:
	case DECR:
	    // Evaluate expr as address.
	    needAddress = true;
	    scan(node.expr);
	    emitVerboseComment(node);
	    // Do the operation.
	    emitter.emitImmediateInstruction("LDA",node.expr.size);
	    emitter.emitAbsoluteInstruction("JSR","ACC.UNOP."+node.operator.name);
	    break;
	default:
	    throw new ACCInternalError("unhandled unary operator",node);
	}
    }

    public void visitParensExpression(ParensExpression node) 
	throws ACCError
    {
	// Nothing special to do
	super.visitParensExpression(node);
    }

    /* Helper methods */

    /**
     * Emit a verbose comment
     */
    public void emitVerboseComment(Object comment) {
	if (printVerboseComments) {
	    emitter.emitComment(comment);
	}
    }

    /**
     * Print out debugging info
     */
    public void printStatus(String s) {
	if (debug) {
	    System.err.println(s);
	}
    }

    /**
     * Get a fresh branch label
     */
    protected String getLabel() {
	return "." + branchLabelCount++;
    }

    /**
     * Record the local variables in the current frame and compute
     * their stack slots.
     */
    private void computeStackSlotsFor(FunctionDecl node) 
	throws ACCError
    {
	printStatus("stack slots:");
	int offset = 0;
	printStatus(" return address: " + offset);
	// Params
	for (VarDecl varDecl : node.params) {
	    printStatus(" " + varDecl + ",offset=" + offset);
	    varDecl.setOffset(offset);
	    offset += varDecl.getSize();
	}
	int firstLocalVarOffset = offset;
	// Local vars
	for (VarDecl varDecl : node.varDecls) {
	    printStatus(" " + varDecl + ",offset=" + offset);
	    varDecl.setOffset(offset);
	    offset += varDecl.getSize();
	}
	// Saved regs
	savedRegs.runOn(node);
	for (RegisterExpression.Register reg : node.savedRegs) {
	    printStatus(" " + reg + ",offset=" + offset);
	    reg.setOffset(offset);
	    offset += reg.getSize();
	}

	// Compute and store the frame size.
	node.frameSize = offset;
	printStatus("frame size="+node.frameSize);
    }

    /**
     * If a register expression for register R appears in the function
     * body, then we need a stack slot for R.  Find all those
     * registers now.
     */
    private SavedRegs savedRegs = new SavedRegs();
    private class SavedRegs 
	extends ASTScanner
	implements FunctionPass 
    {
	FunctionDecl fn;
	public void runOn(FunctionDecl node)
	    throws ACCError
	{
	    fn = node;
	    scan(node);
	}

	public void visitRegisterExpression(RegisterExpression node) {
	    fn.savedRegs.add(node.register);
	}
    }

    /**
     * Push a size on the stack
     */
    protected void pushSize(int size) {
	emitVerboseComment("push expr size");
	emitter.emitImmediateInstruction("LDA",size);
	emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
    }

    /**
     * Make a value on the stack bigger or smaller if necessary to fit
     * needed size.
     */
    protected void adjustSize(int targetSize, int stackSize,
			      boolean signed) {
	if (targetSize < stackSize) {
	    emitter.emitImmediateInstruction("LDA",stackSize-targetSize);
	    emitter.emitAbsoluteInstruction("JSR","ACC.SP.DOWN.A");
	}
	if (targetSize > stackSize) {
	    if (signed) {
		emitVerboseComment("sign extend");
		emitter.emitImmediateInstruction("LDX",1);
	    }
	    else {
		emitVerboseComment("zero extend");
		emitter.emitImmediateInstruction("LDX",0);
	    }
	    emitter.emitImmediateInstruction("LDA",targetSize-stackSize);
	    emitter.emitAbsoluteInstruction("JSR","ACC.EXTEND");
	}
    }

    /**
     * Emit code to do an assignment through IP. Pull 'pullSize' bytes
     * off the stack and assign the low-order 'copySize' bytes through
     * IP.
     *
     * @param copySize  How many bytes to copy
     * @param pullSize  How many bytes to pull
     */
    protected void assign(int targetSize) {
	// Pull IP from stack, then copy from stack to (IP).
	emitVerboseComment("assign");
	emitter.emitImmediateInstruction("LDA",targetSize);
	emitter.emitAbsoluteInstruction("JSR","ACC.ASSIGN");
    }

    /**
     * Emit code to push the address of a variable on the stack.
     */
    protected void pushVarAddr(VarDecl node) {
	emitVerboseComment("address of " + node);
	if (node.isLocalVariable) {
	    emitter.emitImmediateInstruction("LDA",node.getOffset());
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.SLOT");
	}
	else {
	    emitter.emitImmediateInstruction("LDA",node.name,false);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	    emitter.emitImmediateInstruction("LDA",node.name,true);
	    emitter.emitAbsoluteInstruction("JSR","ACC.PUSH.A");
	}
    }

    /**
     * Emit code to restore all registers to the values saved in their
     * spill slots on the program stack.
     */
    protected void restoreRegisters() {
	if (currentFunction.savedRegs.size() > 0) {
	    emitVerboseComment("restore registers");
	    for (RegisterExpression.Register reg : 
		     currentFunction.savedRegs) {
		emitter.emitImmediateInstruction("LDY",reg.getOffset());
		emitter.emitIndirectYInstruction("LDA","ACC.FP");
		emitter.emitAbsoluteInstruction("STA",reg.saveAddr);
	    }
	    emitter.emitAbsoluteInstruction("JSR","$FF3F");
	}
    }

    /**
     * Emit code to save the regsiters.
     */
    protected void saveRegisters() {
	if (currentFunction.savedRegs.size() > 0) {
	    emitVerboseComment("save registers");
	    emitter.emitAbsoluteInstruction("JSR","$FF4A");
	    for (RegisterExpression.Register reg :
		     currentFunction.savedRegs) {
		emitter.emitAbsoluteInstruction("LDA",reg.saveAddr);
		emitter.emitImmediateInstruction("LDY",reg.getOffset());
		emitter.emitIndirectYInstruction("STA","ACC.FP");
	    }
	}
    }

}
