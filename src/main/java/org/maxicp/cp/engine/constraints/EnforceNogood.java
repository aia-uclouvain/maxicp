package org.maxicp.cp.engine.constraints;

import java.util.ArrayList;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Global constraint to enforce multiple nogoods.
 * 
 * Currently, only "simple" nogoods are supported, that is nogoods that can be
 * represented as a list of decisions of the form "x =/!=/<=/>= v" where x
 * is a variable and v is a value.
 * 
 * The storage here do not currently supports removing nogoods or compressing
 * them.
 */
public class EnforceNogood {
    CPSolver solver;

    private class ReducedNoGood extends AbstractCPConstraint {
        private final CPConstraint[] nogood;

        // There are always two watchers, indicating the id of constraint currently
        // being watched in nogood. A special value (-1) indicates that the
        // ReducedNoGood is currently entailed so nothing is being watched.
        // At any point in time, watchers[0].value() < watchers[1].value() or they both
        // are -1.
        private StateInt[] watchers;

        public ReducedNoGood(CPConstraint[] nogood) {
            super(solver);
            this.nogood = nogood;
            assert nogood.length > 1; // otherwise, we could just post the nogood as it is without needing to watch
                                      // anything, which should be done by the EnforceNogood class
            this.watchers = new StateInt[2];
            this.watchers[0] = solver.getStateManager().makeStateInt(0);
            this.watchers[1] = solver.getStateManager().makeStateInt(1);
        }

        @Override
        public void post() {
            registerWatcher(0);
            registerWatcher(1);
            check(0);
        }

        private void registerWatcher(int whichWatcher) {
            int idx = watchers[whichWatcher].value();
            CPConstraint c = nogood[idx];
            if (c instanceof EqualCst eq) {
                eq.getX().whenFixed(() -> check(idx));
            } else if (c instanceof NotEqualCst neq) {
                neq.getX().whenDomainChange(() -> check(idx));
            } else if (c instanceof LessOrEqualCst le) {
                le.getX().whenBoundChange(() -> check(idx));
            } else if (c instanceof GreaterOrEqualCst ge) {
                ge.getX().whenBoundChange(() -> check(idx));
            } else {
                throw new IllegalStateException("Unexpected constraint type: " + c.getClass());
            }
        }

        private void check(int whichCtr) {
            if (watchers[0].value() != whichCtr && watchers[1].value() != whichCtr) {
                // this is an old watcher being awakened, just ignore the event
                return;
            }

            // Check both watchers
            int w0Status = getWatcherStatus(0);
            int w1Status = getWatcherStatus(1);

            if (w0Status == 0 && w1Status == 0) {
                // both watchers are still unknown, let's wait
                return;
            }

            if (w0Status == -1 || w1Status == -1) {
                // at least one of the two watchers is false, so the nogood is satisfied, we are
                // done, nothing to do anymore
                watchers[0].setValue(-1);
                watchers[1].setValue(-1);
                return;
            }

            if (w0Status == 1 && w1Status == 1) {
                // The two watchers are satisfied. Three possibilities:
                // - if we have >= 2 constraints left to verify, we just have to put two new
                // watchers
                // - if there is exactly 1 constraint left, then we must post its contrapose (as
                // we know we cannot allow the original constraint to be satisfied)
                // - if there are 0 constraints left, then we have a nogood violation, we can
                // just fail
                int nextToWatch = watchers[1].value() + 1;
                if (nextToWatch == nogood.length) {
                    // nogood violation
                    throw new InconsistencyException();
                } else if (nextToWatch == nogood.length - 1) {
                    // we have to post the contrapose of the last constraint
                    CPConstraint lastConstraint = nogood[nextToWatch];
                    contraposeAndPost(lastConstraint);
                    // and we are done, we can set the watchers to -1 to indicate that the nogood is
                    // satisfied and there is no need to watch anything anymore
                    watchers[0].setValue(-1);
                    watchers[1].setValue(-1);
                } else {
                    // we can just put two new watchers
                    watchers[0].setValue(nextToWatch);
                    watchers[1].setValue(nextToWatch + 1);
                    registerWatcher(0);
                    registerWatcher(1);
                    check(nextToWatch);
                }
                return;
            }

            // here, either w0Status or w1Status is 1, and the other one is 0.
            int nextCtr = watchers[1].value() + 1;
            if (w0Status == 1) {
                watchers[0].setValue(watchers[1].value());
                registerWatcher(0);
            }

            if (nextCtr == nogood.length) {
                // we have to post the contrapose of the last constraint
                CPConstraint lastConstraint = nogood[watchers[0].value()];
                contraposeAndPost(lastConstraint);
                // and we are done, we can set the watchers to -1 to indicate that the nogood is
                // satisfied and there is no need to watch anything anymore
                watchers[0].setValue(-1);
                watchers[1].setValue(-1);
            } else {
                watchers[1].setValue(nextCtr);
                registerWatcher(1);
                check(nextCtr);
            }
        }

        /**
         * Returns the status of the watcher, that is:
         * -1 if the watched constraint is false
         * 0 if the watched constraint is still unknown
         * 1 if the watched constraint is satisfied
         * @param whichWatcher
         * @return
         */
        private int getWatcherStatus(int whichWatcher) {
            int idx = watchers[whichWatcher].value();
            assert idx >= 0; // if the watcher is -1, it means that the nogood is already satisfied, so we
                             // should not be trying to get its status anymore
            CPConstraint c = nogood[idx];
            switch (c) {
                case EqualCst eq -> {
                    if (eq.getX().isFixed() && eq.getX().min() == eq.getV()) {
                        return 1;
                    } else if (!eq.getX().contains(eq.getV())) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
                case NotEqualCst neq -> {
                    if (neq.getX().isFixed() && neq.getX().min() == neq.getV()) {
                        return -1;
                    } else if (!neq.getX().contains(neq.getV())) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
                case LessOrEqualCst le -> {
                    if (le.getX().max() <= le.getV())
                        return 1;
                    else if (le.getX().min() > le.getV())
                        return -1;
                    else
                        return 0;
                }
                case GreaterOrEqualCst ge -> {
                    if (ge.getX().min() >= ge.getV())
                        return 1;
                    else if (ge.getX().max() < ge.getV())
                        return -1;
                    else
                        return 0;
                }
                default -> throw new IllegalStateException("Unexpected constraint type: " + c.getClass());
            }
        }
    }

    public EnforceNogood(CPSolver cp) {
        solver = cp;
    }

    public static boolean isNoGoodSimple(CPConstraint[][][] nogood) {
        if (nogood == null || nogood.length == 0) {
            return false;
        }

        for (int nodeIdx = 0; nodeIdx < nogood.length; nodeIdx++) {
            CPConstraint[][] nodeConstraints = nogood[nodeIdx];
            if (nodeConstraints == null) {
                return false;
            }

            if (nodeConstraints.length == 0) {
                return false;
            }

            for (CPConstraint[] decision : nodeConstraints) {
                if (decision != null && decision.length != 1) {
                    return false;
                }

                if (decision == null)
                    continue; // simply a placeholder to indicate that the previous branch is done

                CPConstraint c = decision[0];
                switch (c) {
                    case EqualCst eq -> {
                    }
                    case NotEqualCst neq -> {
                    }
                    case LessOrEqualCst le -> {
                    }
                    case GreaterOrEqualCst ge -> {
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean buildAndPostReducedNoGoods(CPConstraint[][][] nogood) {
        if (!isNoGoodSimple(nogood)) {
            return false;
        }
        // As the nogood is "simple", we know that
        // - each element CPConstraint[][] has at least 1 entry.
        // - in each decision CPConstraint[] there is exactly one constraint, that is of
        // one of the following types:
        // - EqCst
        // - NotEqualCst
        // - LessOrEqualCst
        // - GreaterOrEqualCst

        // we will now write all the reduced nogoods.
        // as defined in "Nogood Recording from Restarts" (Lecoutre et al.),
        // given a list of decisions [d_1, d_2, ..., d_n] on the current branch, for any
        // i such that d_i is negative we have that pos({d_1, ..., d_{i-1}}) + not(d_i),
        // where pos is the subset of positive decisions. In our nomenclature, it means
        // that for any i such that nogood[i].length == 2, we have to post a nogood
        // composed of the nogood[j][0] for j < i such that nogood[j].length == 1 and
        // the negation of nogood[i][1] (that is nogood[i][0]!).

        ArrayList<CPConstraint> currentPosDecisions = new ArrayList<>();

        for (int i = 0; i < nogood.length; i++) {
            CPConstraint[][] decisions = nogood[i];

            if (decisions.length == 1) {
                // there is no negative decision at this node, just add the positive decision to
                // the list of positive decisions for the next nogoods
                CPConstraint positiveDecision = decisions[0][0];
                currentPosDecisions.add(positiveDecision);
            } else {
                // There is a negative decision at this node, so we are at a the end of a
                // nld-subsequence
                for (int decision = 0; decision < decisions.length - 1; decision++) {
                    CPConstraint positiveDecision = decisions[decision][0];

                    // If there is currently no previous positive decision, then we can just post
                    // the nogood as it is
                    if (currentPosDecisions.isEmpty())
                        contraposeAndPost(positiveDecision);
                    else {
                        CPConstraint[] reducedNoGood = new CPConstraint[currentPosDecisions.size() + 1];
                        for (int j = 0; j < currentPosDecisions.size(); j++)
                            reducedNoGood[j] = currentPosDecisions.get(j);
                        reducedNoGood[currentPosDecisions.size()] = positiveDecision;

                        solver.post(new ReducedNoGood(reducedNoGood));
                    }
                }
            }
        }

        return true;

    }

    /**
     * Post the contrapose of a constraint to the solver
     * 
     * @param decision the constraint to be contraposed and posted
     */
    private void contraposeAndPost(CPConstraint decision) {
        switch (decision) {
            case EqualCst eq -> {
                CPConstraint nogood = new NotEqualCst(eq.getX(), eq.getV());
                solver.post(nogood);
            }
            case NotEqualCst neq -> {
                CPConstraint nogood = new EqualCst(neq.getX(), neq.getV());
                solver.post(nogood);
            }
            case LessOrEqualCst le -> {
                // x <= a contrapose is x > a, which can be rewritten as x >= a+1
                CPConstraint nogood = new GreaterOrEqualCst(le.getX(), le.getV() + 1);
                solver.post(nogood);
            }
            case GreaterOrEqualCst ge -> {
                // x >= a contrapose is x < a, which can be rewritten as x <= a-1
                CPConstraint nogood = new LessOrEqualCst(ge.getX(), ge.getV() - 1);
                solver.post(nogood);
            }
            default -> throw new IllegalStateException("Unexpected constraint type: " + decision.getClass());
        }
    }

    /**
     * Adds a nogood. The nogood must be simple, that is it must satisfy the
     * following properties:
     * - each element CPConstraint[][] has at most 2 entries. (aka the tree is
     * binary)
     * - Each node (CPConstraint[]) has exactly one constraint, that is of one of
     * the following types:
     * - EqualCst
     * - NotEqualCst
     * - LessOrEqualCst
     * - GreaterOrEqualCst
     * The nogood is added as a constraint to the solver, so it may be reverted if
     * the search goes above the current node.
     * 
     * @param nogood
     */
    public void addNogood(CPConstraint[][][] nogood) {
        if (!isNoGoodSimple(nogood)) {
            throw new IllegalArgumentException("The nogood is not simple, cannot be added to this constraint");
        }
        buildAndPostReducedNoGoods(nogood);
    }
}
