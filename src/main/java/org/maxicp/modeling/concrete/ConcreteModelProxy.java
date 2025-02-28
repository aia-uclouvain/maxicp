package org.maxicp.modeling.concrete;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.IntervalExpression;
import org.maxicp.modeling.algebra.sequence.SeqExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.Objective;
import org.maxicp.state.State;
import org.maxicp.state.StateManager;

import java.util.function.Supplier;

/**
 * A base class for some "Layer models" that can locally override some methods
 * but redirect everything else to a bottom layer.
 */
public class ConcreteModelProxy implements ConcreteModel {
    private final ConcreteModel model;
    public ConcreteModelProxy(ConcreteModel model) {
        this.model = model;
    }

    @Override
    public ConcreteIntVar getConcreteVar(IntExpression expr) {
        return model.getConcreteVar(expr);
    }

    @Override
    public ConcreteBoolVar getConcreteVar(BoolExpression expr) {
        return model.getConcreteVar(expr);
    }

    @Override
    public ConcreteSeqVar getConcreteVar(SeqExpression expr) {
        return model.getConcreteVar(expr);
    }

    @Override
    public ConcreteIntervalVar getConcreteVar(IntervalExpression expr) {
        return model.getConcreteVar(expr);
    }

    @Override
    public void add(Constraint c, boolean enforceFixPoint) {
        model.add(c, enforceFixPoint);
    }

    @Override
    public void jumpTo(SymbolicModel m, boolean enforceFixPoint) {
        model.jumpTo(m, enforceFixPoint);
    }

    @Override
    public void jumpToChild(SymbolicModel m, boolean enforceFixPoint) {
        model.jumpToChild(m, enforceFixPoint);
    }

    @Override
    public Objective createObjective(org.maxicp.modeling.symbolic.Objective obj) {
        return model.createObjective(obj);
    }

    @Override
    public SymbolicModel symbolicCopy() {
        return model.symbolicCopy();
    }

    @Override
    public Iterable<Constraint> getConstraints() {
        return model.getConstraints();
    }

    @Override
    public ModelProxy getModelProxy() {
        return model.getModelProxy();
    }

    @Override
    public StateManager getStateManager() {
        return model.getStateManager();
    }

    public static ConcreteModel noFixPoint(ConcreteModel model) {
        return new ConcreteModelProxy(model) {
            @Override
            public void add(Constraint c, boolean ignored) {
                model.add(c, false);
            }

            @Override
            public void jumpTo(SymbolicModel m, boolean ignored) {
                model.jumpTo(m, false);
            }
        };
    }

    public static void noFixPoint(ModelProxy modelProxy, Runnable r) {
        modelProxy.runWithModel(noFixPoint(modelProxy.getConcreteModel()), r);
    }

    public static <T> T noFixPoint(ModelProxy modelProxy, Supplier<T> r) {
        return modelProxy.runWithModel(noFixPoint(modelProxy.getConcreteModel()), r);
    }

    public static SymbolicModel symbolicExecution(ModelProxy modelProxy, Runnable r) {
        SymbolicExecution se = new SymbolicExecution(modelProxy.getConcreteModel());
        StateManager sm = se.getStateManager();
        final int level = sm.getLevel();
        sm.saveState();
        try {
            modelProxy.runWithModel(se, r);
            return se.symbolicCopy();
        }
        finally {
            sm.restoreStateUntil(level);
        }
    }

    private static class SymbolicExecution extends ConcreteModelProxy {
        private final State<SymbolicModel> trueModel;
        public SymbolicExecution(ConcreteModel model) {
            super(model);
            trueModel = model.getStateManager().makeStateRef(model.symbolicCopy());
        }

        @Override
        public void add(Constraint c, boolean ignored) {
            trueModel.setValue(trueModel.value().add(c));
        }

        @Override
        public void jumpTo(SymbolicModel m, boolean ignored) {
            throw new UnsupportedOperationException("jumpTo is not authorized while in a Symbolic Execution");
        }

        @Override
        public void jumpToChild(SymbolicModel m, boolean ignored) {
            throw new UnsupportedOperationException("jumpToChild is not authorized while in a Symbolic Execution");
        }

        @Override
        public SymbolicModel symbolicCopy() {
            return trueModel.value();
        }

        @Override
        public Iterable<Constraint> getConstraints() {
            return trueModel.value().getConstraints();
        }
    }
}
