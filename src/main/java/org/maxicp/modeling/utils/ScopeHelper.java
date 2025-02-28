package org.maxicp.modeling.utils;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.Var;
import org.maxicp.modeling.algebra.Expression;

import java.util.HashSet;

public class ScopeHelper {
    private static class VarLister {
        HashSet<Var> list = new HashSet<>();
        private Expression recurListVar(Expression e) {
            if(e instanceof Var)
                list.add((Var) e);
            e.mapSubexpressions(this::recurListVar);
            return e;
        }
    }
    public static HashSet<Var> listVars(Model model) {
        VarLister v = new VarLister();
        for(Constraint c: model.getConstraints())
            for(Expression e: c.scope())
                v.recurListVar(e);
        return v.list;
    }
}
