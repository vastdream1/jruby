package org.jruby.ir.instructions;

import org.jruby.RubyKernel;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.IRException;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import java.util.Map;

// Right now, this is primarily used by the JRuby implementation.
// Ruby exceptions go through RubyKernel.raise (or RubyThread.raise).
public class ThrowExceptionInstr extends Instr implements FixedArityInstr {
    private Operand exceptionArg;

    public ThrowExceptionInstr(Operand exception) {
        super(Operation.THROW);
        this.exceptionArg = exception;
    }

    public Operand getExceptionArg() {
        return exceptionArg;
    }

    @Override
    public Operand[] getOperands() {
        return new Operand[]{ exceptionArg };
    }

    @Override
    public void simplifyOperands(Map<Operand, Operand> valueMap, boolean force) {
        exceptionArg = exceptionArg.getSimplifiedOperand(valueMap, force);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + exceptionArg + ")";
    }

    @Override
    public Instr clone(CloneInfo ii) {
        return new ThrowExceptionInstr(exceptionArg.cloneForInlining(ii));
    }

    @Override
    public Object interpret(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        if (exceptionArg instanceof IRException) {
            throw ((IRException) exceptionArg).getException(context.runtime);
        }

        Object excObj = exceptionArg.retrieve(context, self, currScope, currDynScope, temp);

        if (excObj instanceof IRubyObject) {
            RubyKernel.raise(context, context.runtime.getKernel(), new IRubyObject[] {(IRubyObject)excObj}, Block.NULL_BLOCK);
        } else if (excObj instanceof Throwable) { // java exception -- avoid having to add 'throws' clause everywhere!
            Helpers.throwException((Throwable)excObj);
        }

        // should never get here
        throw new RuntimeException("Control shouldn't have reached here in ThrowExceptionInstr.  excObj is: " + excObj);
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.ThrowExceptionInstr(this);
    }
}
