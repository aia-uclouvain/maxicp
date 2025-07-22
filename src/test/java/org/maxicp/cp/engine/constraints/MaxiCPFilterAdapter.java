package org.maxicp.cp.engine.constraints;


import be.uclouvain.solvercheck.core.data.Operator;
import be.uclouvain.solvercheck.core.data.PartialAssignment;
import be.uclouvain.solvercheck.core.task.StatefulFilter;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;


/**
 * This class provides a convenient facility to build a stateful filter from a
 * given constraint factory.
 */
public class MaxiCPFilterAdapter implements StatefulFilter {
    /** The "lambda" used to instantiate the constraint. */
    private final ConstraintFactory factory;

    /** The underlying solvers. */
    private CPSolver solver;
    /** The transformed variables of the solver. */
    private CPIntVar[] vars;
    /**
     *  A flag telling whether or not a conflict was found during
     * search/propagation.
     */
    private boolean conflict;
    /**
     * A flag indicating whether a conflict happened at root level.
     */
    private boolean unsat;

    /**
     * Creates an adapter for the constraint instantiated by the given factory.
     * @param factory a expresion allowing the instanciation of a solver
     *                constraint.
     */
    public MaxiCPFilterAdapter(final ConstraintFactory factory) {
        this.solver = null;
        this.factory  = factory;

        this.vars     = null;
        this.conflict = false;
        this.unsat    = false;
    }

    /** {@inheritDoc} */
    @Override
    public void setup(final PartialAssignment root) {
        unsat    = false;
        conflict = false;

        solver = CPFactory.makeSolver();
        vars   = SolverCheckUtils.partial2Vars(solver, root);

        try {
            factory.get(solver, vars).post();
            solver.fixPoint();
        } catch (InconsistencyException ex) {
            this.unsat    = true;
            this.conflict = true;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void pushState() {
        solver.getStateManager().saveState();
    }

    /** {@inheritDoc} */
    @Override
    public void popState() {
        solver.getStateManager().restoreState();
        conflict = false;
    }

    /** {@inheritDoc} */
    @Override
    public PartialAssignment currentState() {
        if (conflict || unsat) {
            return PartialAssignment.error(vars.length);
        }
        return SolverCheckUtils.vars2Partial(vars);
    }

    /** {@inheritDoc} */
    @Override
    public void branchOn(final int variable,
                         final Operator op,
                         final int value) {

        CPIntVar v = vars[variable];
        try {
            switch (op) {
                case EQ:
                    v.fix(value);
                    break;
                case NE:
                    v.remove(value);
                    break;
                case LE:
                    v.removeAbove(value);
                    break;
                case LT:
                    v.removeAbove(value - 1);
                    break;
                case GE:
                    v.removeBelow(value);
                    break;
                case GT:
                    v.removeBelow(value + 1);
                    break;
                default:
                    throw new UnsupportedOperationException("Not implemented");
            }

            solver.fixPoint();

        } catch (InconsistencyException ex) {
            conflict = true;
        }
    }
}
