/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */
package org.maxicp.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.BinPacking;
import org.maxicp.modeling.constraints.scheduling.Alternative;
import org.maxicp.modeling.constraints.seqvar.Distance;
import org.maxicp.modeling.symbolic.IntervalStatus;
import org.maxicp.modeling.symbolic.IsNodeRequired;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.modeling.symbolic.SharedMaximization;
import org.maxicp.modeling.symbolic.SharedMinimization;

import java.util.*;

/**
 * Walks the full constraint / expression graph of a {@link ModelDispatcher}
 * and collects candidate decision variables ({@link IntExpression} and {@link IntervalVar}).
 *
 * <p>Results are deduplicated by identity, preserving first-seen order.</p>
 *
 * @see DecisionVarsProvider
 */
public class DecisionVarCollector {

    private DecisionVarCollector() {}

    /**
     * Collects all candidate integer/boolean decision variables inferred from the model's
     * constraint graph.
     *
     * @param model the model to inspect
     * @return ordered, identity-deduplicated, unmodifiable list of decision variables
     */
    public static List<IntExpression> collect(ModelDispatcher model) {
        IdentityHashMap<IntExpression, Boolean> derived = collectDerivedVars(model);
        IdentityHashMap<IntExpression, Boolean> seen = new IdentityHashMap<>();
        List<IntExpression> result = new ArrayList<>();

        // Pass 1: Harvest explicitly declared decision variables from DecisionVarsProvider nodes
        for (Constraint c : model.getConstraints()) {
            if (c instanceof DecisionVarsProvider dvp) {
                for (IntExpression v : dvp.decisionVariables()) {
                    if (isCandidateDecisionVar(v, derived) && seen.putIfAbsent(v, Boolean.TRUE) == null) {
                        result.add(v);
                    }
                }
            }
            walkDecisionVarProviders(c.scope(), derived, seen, result);
        }

        // Pass 2: Walk non-DecisionVarsProvider constraints for leaf IntVars
        for (Constraint c : model.getConstraints()) {
            if (!(c instanceof DecisionVarsProvider)) {
                walkLeafIntVars(c.scope(), derived, seen, result);
            }
        }

        // Pass 3: If no constraints provided decision variables and the model has no interval or sequence variables,
        // fallback to registered model IntVars
        if (result.isEmpty() && model.getRegisteredIntervalVars().isEmpty() && model.getRegisteredSeqVars().isEmpty()) {
            for (IntVar v : model.getRegisteredIntVars()) {
                if (isCandidateDecisionVar(v, derived) && v.min() < v.max() && seen.putIfAbsent(v, Boolean.TRUE) == null) {
                    result.add(v);
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Collects all integer/boolean variables relevant for black-box search on the model,
     * starting with primary decision variables from {@link #collect(ModelDispatcher)},
     * followed by any leaf {@link IntVar} in the objective and any remaining registered
     * {@link IntVar} on the {@link ModelDispatcher}.
     *
     * @param model     the model to inspect
     * @param objective optional objective
     * @return ordered, identity-deduplicated, unmodifiable list of integer variables
     */
    public static List<IntExpression> collectAllIntVars(ModelDispatcher model, Objective objective) {
        IdentityHashMap<IntExpression, Boolean> derived = collectDerivedVars(model);
        IdentityHashMap<IntExpression, Boolean> seen = new IdentityHashMap<>();
        List<IntExpression> result = new ArrayList<>();

        for (IntExpression v : collect(model)) {
            if (seen.putIfAbsent(v, Boolean.TRUE) == null) {
                result.add(v);
            }
        }

        boolean hasIntervals = !collectIntervals(model).isEmpty();
        boolean hasSequences = !collectSequences(model).isEmpty();
        if (!hasIntervals && !hasSequences && !model.isRawSolver()) {
            IntExpression objExpr = extractObjectiveExpr(objective);
            if (objExpr != null) {
                walkLeafIntVars(List.of(objExpr), derived, seen, result);
            }
        }

        for (IntVar v : model.getRegisteredIntVars()) {
            if (!(v instanceof IntervalStatus) && !(v instanceof IsNodeRequired) && seen.putIfAbsent(v, Boolean.TRUE) == null) {
                result.add(v);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Collects all {@link SeqVar} instances from the model's constraint graph
     * and registered sequence variables.
     *
     * @param model the model to inspect
     * @return ordered, identity-deduplicated, unmodifiable list of sequence variables
     */
    public static List<SeqVar> collectSequences(ModelDispatcher model) {
        IdentityHashMap<SeqVar, Boolean> seen = new IdentityHashMap<>();
        List<SeqVar> result = new ArrayList<>();

        for (SeqVar sv : model.getRegisteredSeqVars()) {
            if (sv != null && seen.putIfAbsent(sv, Boolean.TRUE) == null) {
                result.add(sv);
            }
        }

        IdentityHashMap<Expression, Boolean> visited = new IdentityHashMap<>();
        for (Constraint c : model.getConstraints()) {
            Deque<Expression> stack = new ArrayDeque<>(c.scope());
            while (!stack.isEmpty()) {
                Expression node = stack.pop();
                if (node == null || visited.putIfAbsent(node, Boolean.TRUE) != null) {
                    continue;
                }
                if (node instanceof SeqVar sv) {
                    if (seen.putIfAbsent(sv, Boolean.TRUE) == null) {
                        result.add(sv);
                    }
                } else if (node instanceof IsNodeRequired inr && inr.seqVar != null) {
                    if (seen.putIfAbsent(inr.seqVar, Boolean.TRUE) == null) {
                        result.add(inr.seqVar);
                    }
                }
                Collection<? extends Expression> children = node.subexpressions();
                if (children != null) {
                    for (Expression child : children) {
                        if (child != null) {
                            stack.push(child);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Collects all {@link IntervalVar} instances from the model's constraint graph
     * and registered interval variables.
     *
     * @param model the model to inspect
     * @return ordered, identity-deduplicated, unmodifiable list of interval variables
     */
    public static List<IntervalVar> collectIntervals(ModelDispatcher model) {
        IdentityHashMap<IntervalVar, Boolean> seen = new IdentityHashMap<>();
        List<IntervalVar> result = new ArrayList<>();

        for (IntervalVar iv : model.getRegisteredIntervalVars()) {
            if (!isDummyFixedStepInterval(iv) && seen.putIfAbsent(iv, Boolean.TRUE) == null) {
                result.add(iv);
            }
        }

        IdentityHashMap<Expression, Boolean> visited = new IdentityHashMap<>();
        for (Constraint c : model.getConstraints()) {
            Deque<Expression> stack = new ArrayDeque<>(c.scope());
            while (!stack.isEmpty()) {
                Expression node = stack.pop();
                if (node == null || visited.putIfAbsent(node, Boolean.TRUE) != null) {
                    continue;
                }
                if (node instanceof IntervalVar iv) {
                    if (!isDummyFixedStepInterval(iv) && seen.putIfAbsent(iv, Boolean.TRUE) == null) {
                        result.add(iv);
                    }
                } else if (node instanceof IntervalStatus is && is.intervalVar != null) {
                    if (!isDummyFixedStepInterval(is.intervalVar) && seen.putIfAbsent(is.intervalVar, Boolean.TRUE) == null) {
                        result.add(is.intervalVar);
                    }
                }
                Collection<? extends Expression> children = node.subexpressions();
                if (children != null) {
                    for (Expression child : children) {
                        if (child != null) {
                            stack.push(child);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static boolean isDummyFixedStepInterval(IntervalVar iv) {
        return iv.isPresent() && iv.isFixed() && iv.lengthMin() >= org.maxicp.Constants.HORIZON;
    }

    private static IdentityHashMap<IntExpression, Boolean> collectDerivedVars(ModelDispatcher model) {
        IdentityHashMap<IntExpression, Boolean> derived = new IdentityHashMap<>();
        IdentityHashMap<IntVar, Boolean> registered = new IdentityHashMap<>();
        for (IntVar v : model.getRegisteredIntVars()) {
            registered.put(v, Boolean.TRUE);
        }
        for (Constraint c : model.getConstraints()) {
            if (c instanceof BinPacking bp && bp.loads() != null) {
                for (IntExpression load : bp.loads()) {
                    if (load != null) {
                        derived.put(load, Boolean.TRUE);
                    }
                }
            } else if (c instanceof Alternative alt && alt.n() instanceof IntVar nv) {
                if (!registered.containsKey(nv)) {
                    derived.put(nv, Boolean.TRUE);
                }
            } else if (c instanceof Distance distCtr && distCtr.distance != null) {
                derived.put(distCtr.distance, Boolean.TRUE);
            }
        }
        return derived;
    }

    private static boolean isCandidateDecisionVar(IntExpression v, IdentityHashMap<IntExpression, Boolean> derived) {
        if (v == null || v instanceof Constant || v instanceof IntervalStatus || v instanceof IsNodeRequired) {
            return false;
        }
        return !derived.containsKey(v);
    }

    /** Iterative DFS over expression trees; harvests DecisionVarsProvider nodes. */
    private static void walkDecisionVarProviders(
            Collection<? extends Expression> roots,
            IdentityHashMap<IntExpression, Boolean> derived,
            IdentityHashMap<IntExpression, Boolean> seen,
            List<IntExpression> result) {
        if (roots == null || roots.isEmpty()) return;
        Deque<Expression> stack = new ArrayDeque<>(roots);
        IdentityHashMap<Expression, Boolean> visited = new IdentityHashMap<>();
        while (!stack.isEmpty()) {
            Expression node = stack.pop();
            if (node == null || visited.putIfAbsent(node, Boolean.TRUE) != null) continue;
            if (node instanceof DecisionVarsProvider dvp) {
                for (IntExpression v : dvp.decisionVariables()) {
                    if (isCandidateDecisionVar(v, derived) && seen.putIfAbsent(v, Boolean.TRUE) == null) {
                        result.add(v);
                    }
                }
            }
            Collection<? extends Expression> children = node.subexpressions();
            if (children != null) {
                for (Expression child : children) {
                    if (child != null) stack.push(child);
                }
            }
        }
    }

    /** Iterative DFS over expression trees; harvests non-fixed leaf IntVar nodes. */
    private static void walkLeafIntVars(
            Collection<? extends Expression> roots,
            IdentityHashMap<IntExpression, Boolean> derived,
            IdentityHashMap<IntExpression, Boolean> seen,
            List<IntExpression> result) {
        if (roots == null || roots.isEmpty()) return;
        Deque<Expression> stack = new ArrayDeque<>(roots);
        IdentityHashMap<Expression, Boolean> visited = new IdentityHashMap<>();
        while (!stack.isEmpty()) {
            Expression node = stack.pop();
            if (node == null || visited.putIfAbsent(node, Boolean.TRUE) != null) continue;
            if (node instanceof IntVar iv && isCandidateDecisionVar(iv, derived) && iv.min() < iv.max()) {
                if (seen.putIfAbsent(iv, Boolean.TRUE) == null) {
                    result.add(iv);
                }
            }
            Collection<? extends Expression> children = node.subexpressions();
            if (children != null) {
                for (Expression child : children) {
                    if (child != null) stack.push(child);
                }
            }
        }
    }

    private static IntExpression extractObjectiveExpr(Objective objective) {
        if (objective == null) return null;
        return switch (objective) {
            case Minimization min -> min.expr();
            case SharedMinimization min -> min.expr;
            case Maximization max -> max.expr();
            case SharedMaximization max -> max.expr;
            default -> null;
        };
    }
}
