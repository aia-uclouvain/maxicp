package org.maxicp.modeling;

import org.maxicp.modeling.concrete.ConcreteModelProxy;
import org.maxicp.modeling.symbolic.SymbolicModel;

import java.util.function.Supplier;

public class SymbolicBranching {
    public static Supplier<SymbolicModel[]> toSymbolicBranching(Supplier<Runnable[]> branching, ModelProxy mp) {
        return () -> {
            Runnable[] branches = branching.get();
            SymbolicModel[] symbolicModels = new SymbolicModel[branches.length];
            for (int i = 0; i < branches.length; i++) {
                symbolicModels[i] = ConcreteModelProxy.symbolicExecution(mp, branches[i]);
            }
            return symbolicModels;
        };
    }

    public static Supplier<Runnable[]> toRunnableBranching(Supplier<SymbolicModel[]> branching, ModelProxy mp) {
        return () -> {
            SymbolicModel[] symbolicModels = branching.get();
            Runnable[] branches = new Runnable[symbolicModels.length];
            for (int i = 0; i < symbolicModels.length; i++) {
                SymbolicModel m = symbolicModels[i];
                branches[i] = () -> {mp.getConcreteModel().jumpTo(m);};
            }
            return branches;
        };
    }
}
