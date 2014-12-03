package org.jruby.ir;

import org.jcodings.util.IntHash;
import org.jruby.internal.runtime.methods.IRMethodArgs;
import org.jruby.ir.instructions.Instr;
import org.jruby.ir.instructions.ReceiveArgBase;
import org.jruby.ir.instructions.ReceiveKeywordArgInstr;
import org.jruby.ir.instructions.ReceiveKeywordRestArgInstr;
import org.jruby.ir.instructions.ReceiveRestArgInstr;
import org.jruby.ir.operands.LocalVariable;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.operands.Symbol;
import org.jruby.ir.operands.Hash;
import org.jruby.ir.operands.Splat;
import org.jruby.util.KeyValuePair;
import org.jruby.parser.StaticScope;
import org.jruby.util.collections.IntHashMap;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IRMethod extends IRScope {
    public final boolean isInstanceMethod;

    // Note that if operands from the method are modified,
    // callArgs would have to be updated as well
    //
    // Call parameters
    private List<Operand> callArgs;
    private List<KeyValuePair<Operand, Operand>> keywordArgs;

    // Argument description of the form [:req, "a"], [:opt, "b"] ..
    private List<String[]> argDesc;

    // Signatures to the jitted versions of this method
    private IntHashMap<MethodType> signatures = NULL_SIGNATURES;

    // Max arity we support; this is only an artificial number to keep the signature store compact.
    private static final IntHashMap<MethodType> NULL_SIGNATURES = new IntHashMap<>(0);

    // Method name in the jitted version of this method
    private String jittedName;

    public IRMethod(IRManager manager, IRScope lexicalParent, String name,
            boolean isInstanceMethod, int lineNumber, StaticScope staticScope) {
        super(manager, lexicalParent, name, lexicalParent.getFileName(), lineNumber, staticScope);

        this.isInstanceMethod = isInstanceMethod;
        this.callArgs = new ArrayList<>();
        this.keywordArgs = new ArrayList<>();
        this.argDesc = new ArrayList<>();

        if (!getManager().isDryRun() && staticScope != null) {
            staticScope.setIRScope(this);
            staticScope.setScopeType(this.getScopeType());
        }
    }

    @Override
    public IRScopeType getScopeType() {
        return isInstanceMethod ? IRScopeType.INSTANCE_METHOD : IRScopeType.CLASS_METHOD;
    }

    @Override
    public void addInstr(Instr i) {
        // Accumulate call arguments
        if (i instanceof ReceiveKeywordRestArgInstr) {
            // Always add the keyword rest arg to the beginning
            keywordArgs.add(0, new KeyValuePair<Operand, Operand>(Symbol.KW_REST_ARG_DUMMY, ((ReceiveArgBase) i).getResult()));
        } else if (i instanceof ReceiveKeywordArgInstr) {
            ReceiveKeywordArgInstr rkai = (ReceiveKeywordArgInstr)i;
            keywordArgs.add(new KeyValuePair<Operand, Operand>(new Symbol(rkai.argName), rkai.getResult()));
        } else if (i instanceof ReceiveRestArgInstr) {
            callArgs.add(new Splat(((ReceiveRestArgInstr)i).getResult(), true));
        } else if (i instanceof ReceiveArgBase) {
            callArgs.add(((ReceiveArgBase) i).getResult());
        }

        super.addInstr(i);
    }

    public void addArgDesc(IRMethodArgs.ArgType type, String argName) {
        argDesc.add(new String[]{type.name(), argName});
    }

    public List<String[]> getArgDesc() {
        return argDesc;
    }

    public Operand[] getCallArgs() {
        if (receivesKeywordArgs()) {
            int i = 0;
            Operand[] args = new Operand[callArgs.size() + 1];
            for (Operand arg: callArgs) {
                args[i++] = arg;
            }
            args[i] = new Hash(keywordArgs, true);
            return args;
        } else {
            return callArgs.toArray(new Operand[callArgs.size()]);
        }
    }

    @Override
    protected LocalVariable findExistingLocalVariable(String name, int scopeDepth) {
        assert scopeDepth == 0: "Local variable depth in IRMethod should always be zero (" + name + " had depth of " + scopeDepth + ")";
        return localVars.get(name);
    }

    @Override
    public LocalVariable getLocalVariable(String name, int scopeDepth) {
        LocalVariable lvar = findExistingLocalVariable(name, scopeDepth);
        if (lvar == null) lvar = getNewLocalVariable(name, scopeDepth);
        return lvar;
    }

    public void addNativeSignature(int arity, MethodType signature) {
        ensureSignatures();
        signatures.put(arity, signature);
    }

    public IntHashMap<MethodType> getNativeSignatures() {
        return signatures;
    }

    private void ensureSignatures() {
        if (signatures == NULL_SIGNATURES) signatures = new IntHashMap<>(5, 0.25f);
    }

    public String getJittedName() {
        return jittedName;
    }

    public void setJittedName(String jittedName) {
        this.jittedName = jittedName;
    }
}
