package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Convenience entry points for running {@link BlackBoxSearch} from the modeling layer.
 *
 * <p>The API exposes two workflows:</p>
 * <ul>
 *   <li>{@link #solve(ModelDispatcher, int)} and {@link #solve(ModelDispatcher, IntExpression[], int)} for feasibility problems.</li>
 *   <li>{@link #optimize(ModelDispatcher, Objective, int)} and {@link #optimize(ModelDispatcher, IntExpression[], Objective, int)} for optimization problems.</li>
 * </ul>
 *
 * <p>Both workflows accept optional configurers to tune phase options, phase time shares and verbosity.
 * The underlying model is executed through {@code model.runCP(...)} and returns a compact {@link Result}.</p>
 */
public final class ModelingBlackBox {

    /**
     * Final outcome returned by {@link #solve} and {@link #optimize}.
     *
     * @param status overall search status
     * @param solution incumbent values for decision variables when one exists
     * @param objectiveValue incumbent objective value for optimization models
     * @param varValues mapping of model integer variables to their incumbent values
     */
    public record Result(
            SearchStatus status,
            Optional<List<Integer>> solution,
            Optional<Integer> objectiveValue,
            Map<IntExpression, Integer> varValues) {

        public Result {
            varValues = varValues == null ? Map.of() : Map.copyOf(varValues);
        }

        public Result(SearchStatus status, Optional<List<Integer>> solution, Optional<Integer> objectiveValue) {
            this(status, solution, objectiveValue, Map.of());
        }

        /**
         * Returns the value of {@code expr} in the best incumbent solution, if available.
         */
        public Optional<Integer> value(IntExpression expr) {
            Integer v = varValues.get(expr);
            if (v != null) {
                return Optional.of(v);
            }
            if (expr != null && expr.isFixed()) {
                return Optional.of(expr.min());
            }
            return Optional.empty();
        }

        /**
         * Returns the values of {@code vars} in the best incumbent solution, if available.
         */
        public Optional<List<Integer>> values(IntExpression... vars) {
            if (varValues.isEmpty() && solution.isEmpty()) {
                return Optional.empty();
            }
            List<Integer> res = new ArrayList<>(vars.length);
            for (IntExpression v : vars) {
                Integer val = varValues.get(v);
                if (val != null) {
                    res.add(val);
                } else if (v != null && v.isFixed()) {
                    res.add(v.min());
                } else {
                    return Optional.empty();
                }
            }
            return Optional.of(Collections.unmodifiableList(res));
        }
    }

    private ModelingBlackBox() {
    }

    /**
     * Configuration object used by feasibility solves.
     */
    public static final class SolveConfig {
        private BlackBoxSearch.RestartPhaseOptions restart = BlackBoxSearch.RestartPhaseOptions.defaults();
        private BlackBoxSearch.ExhaustivePhaseOptions exhaustive = BlackBoxSearch.ExhaustivePhaseOptions.defaults();
        private double initialExhaustiveShare = 0.0;
        private double restartShare = 0.40;
        private double exhaustiveShare = 0.60;
        private BlackBoxSearch.Verbosity verbosity = BlackBoxSearch.Verbosity.QUIET;
        private BiConsumer<List<Integer>, Integer> incumbentListener;
        private Runnable solutionListener;

        /**
         * Sets restart-phase options.
         */
        public SolveConfig restart(BlackBoxSearch.RestartPhaseOptions options) {
            this.restart = options;
            return this;
        }

        /**
         * Sets exhaustive-phase options.
         */
        public SolveConfig exhaustive(BlackBoxSearch.ExhaustivePhaseOptions options) {
            this.exhaustive = options;
            return this;
        }

        /**
         * Sets time shares for restart and exhaustive phases.
         */
        public SolveConfig shares(double restartShare, double exhaustiveShare) {
            this.restartShare = restartShare;
            this.exhaustiveShare = exhaustiveShare;
            return this;
        }

        /**
         * Sets the optional initial exhaustive phase time share.
         */
        public SolveConfig initialExhaustiveShare(double initialExhaustiveShare) {
            this.initialExhaustiveShare = initialExhaustiveShare;
            return this;
        }

        /**
         * Sets logging verbosity for the underlying {@link BlackBoxSearch}.
         */
        public SolveConfig verbosity(BlackBoxSearch.Verbosity verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        /**
         * Registers a listener invoked whenever a new incumbent solution is found.
         */
        public SolveConfig onIncumbent(BiConsumer<List<Integer>, Integer> listener) {
            this.incumbentListener = listener;
            return this;
        }

        /**
         * Registers a callback invoked while the solver is positioned at a new incumbent solution state.
         */
        public SolveConfig onSolution(Runnable listener) {
            this.solutionListener = listener;
            return this;
        }
    }

    /**
     * Configuration object used by optimization solves.
     */
    public static final class OptimizeConfig {
        private BlackBoxSearch.RestartPhaseOptions restart = BlackBoxSearch.RestartPhaseOptions.defaults();
        private BlackBoxSearch.LnsPhaseOptions lns = BlackBoxSearch.LnsPhaseOptions.defaults();
        private BlackBoxSearch.ExhaustivePhaseOptions exhaustive = BlackBoxSearch.ExhaustivePhaseOptions.defaults();
        private double initialExhaustiveShare = 0.25;
        private double restartShare = 0.20;
        private double lnsShare = 1.0 / 3.0;
        private double exhaustiveShare = 1.0 - 0.25 - 0.20 - 1.0 / 3.0;
        private BlackBoxSearch.Verbosity verbosity = BlackBoxSearch.Verbosity.QUIET;
        private BiConsumer<List<Integer>, Integer> incumbentListener;
        private Runnable solutionListener;

        /**
         * Sets restart-phase options.
         */
        public OptimizeConfig restart(BlackBoxSearch.RestartPhaseOptions options) {
            this.restart = options;
            return this;
        }

        /**
         * Sets LNS phase options.
         */
        public OptimizeConfig lns(BlackBoxSearch.LnsPhaseOptions options) {
            this.lns = options;
            return this;
        }

        /**
         * Sets exhaustive-phase options.
         */
        public OptimizeConfig exhaustive(BlackBoxSearch.ExhaustivePhaseOptions options) {
            this.exhaustive = options;
            return this;
        }

        /**
         * Sets time shares for restart, LNS, and exhaustive phases.
         */
        public OptimizeConfig shares(double restartShare, double lnsShare, double exhaustiveShare) {
            this.restartShare = restartShare;
            this.lnsShare = lnsShare;
            this.exhaustiveShare = exhaustiveShare;
            return this;
        }

        /**
         * Sets the optional initial exhaustive phase time share.
         */
        public OptimizeConfig initialExhaustiveShare(double initialExhaustiveShare) {
            this.initialExhaustiveShare = initialExhaustiveShare;
            return this;
        }

        /**
         * Sets logging verbosity for the underlying {@link BlackBoxSearch}.
         */
        public OptimizeConfig verbosity(BlackBoxSearch.Verbosity verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        /**
         * Registers a listener invoked whenever a new incumbent solution is found.
         */
        public OptimizeConfig onIncumbent(BiConsumer<List<Integer>, Integer> listener) {
            this.incumbentListener = listener;
            return this;
        }

        /**
         * Registers a callback invoked while the solver is positioned at a new incumbent solution state.
         */
        public OptimizeConfig onSolution(Runnable listener) {
            this.solutionListener = listener;
            return this;
        }
    }

    /**
     * Solves a feasibility model with automatic decision variable discovery and default black-box settings.
     */
    public static Result solve(ModelDispatcher model, int timeLimitInSeconds) {
        return solve(model, (IntExpression[]) null, timeLimitInSeconds, cfg -> cfg);
    }

    /**
     * Solves a feasibility model with automatic decision variable discovery and caller-provided configuration.
     */
    public static Result solve(ModelDispatcher model, int timeLimitInSeconds, UnaryOperator<SolveConfig> configurer) {
        return solve(model, (IntExpression[]) null, timeLimitInSeconds, configurer);
    }

    /**
     * Solves a feasibility model with default black-box settings.
     */
    public static Result solve(ModelDispatcher model, IntExpression[] decisionVars, int timeLimitInSeconds) {
        return solve(model, decisionVars, timeLimitInSeconds, cfg -> cfg);
    }

    /**
     * Solves a feasibility model with caller-provided configuration.
     */
    public static Result solve(ModelDispatcher model, IntExpression[] decisionVars, int timeLimitInSeconds,
                               UnaryOperator<SolveConfig> configurer) {
        try {
            return model.runCP(() -> {
                SolveConfig cfg = configurer.apply(new SolveConfig());
                BlackBoxSearch search = (decisionVars == null ? new BlackBoxSearch(model) : new BlackBoxSearch(model, decisionVars))
                        .withVerbosity(cfg.verbosity)
                        .onIncumbent(cfg.incumbentListener);
                if (cfg.solutionListener != null) {
                    search.onSolution(cfg.solutionListener);
                }
                if (search.intervals().isEmpty()) {
                    search.withFeasibilityPlan(cfg.restart, cfg.exhaustive,
                            cfg.initialExhaustiveShare, cfg.restartShare, cfg.exhaustiveShare);
                } else {
                    search.withDefaultPhasePlan();
                }
                SearchStatus status = search.start(timeLimitInSeconds);
                return new Result(status, search.bestSolution(), search.bestObjectiveValue(), search.bestVarValuesMap());
            });
        } catch (InconsistencyException e) {
            return new Result(SearchStatus.UNSAT, Optional.empty(), Optional.empty(), Map.of());
        }
    }

    /**
     * Solves an optimization model with automatic decision variable discovery and default black-box settings.
     */
    public static Result optimize(ModelDispatcher model, Objective objective, int timeLimitInSeconds) {
        return optimize(model, null, objective, timeLimitInSeconds, cfg -> cfg);
    }

    /**
     * Solves an optimization model with automatic decision variable discovery and caller-provided configuration.
     */
    public static Result optimize(ModelDispatcher model, Objective objective, int timeLimitInSeconds,
                                  UnaryOperator<OptimizeConfig> configurer) {
        return optimize(model, null, objective, timeLimitInSeconds, configurer);
    }

    /**
     * Solves an optimization model with default black-box settings.
     */
    public static Result optimize(ModelDispatcher model, IntExpression[] decisionVars, Objective objective, int timeLimitInSeconds) {
        return optimize(model, decisionVars, objective, timeLimitInSeconds, cfg -> cfg);
    }

    /**
     * Solves an optimization model with caller-provided configuration.
     */
    public static Result optimize(ModelDispatcher model, IntExpression[] decisionVars, Objective objective, int timeLimitInSeconds,
                                  UnaryOperator<OptimizeConfig> configurer) {
        try {
            return model.runCP(() -> {
                OptimizeConfig cfg = configurer.apply(new OptimizeConfig());
                BlackBoxSearch search = (decisionVars == null
                        ? new BlackBoxSearch(model, objective)
                        : new BlackBoxSearch(model, decisionVars, objective))
                        .withVerbosity(cfg.verbosity)
                        .onIncumbent(cfg.incumbentListener);
                if (cfg.solutionListener != null) {
                    search.onSolution(cfg.solutionListener);
                }
                if (search.intervals().isEmpty()) {
                    search.withOptimizationPlan(cfg.restart, cfg.lns, cfg.exhaustive,
                            cfg.initialExhaustiveShare, cfg.restartShare, cfg.lnsShare, cfg.exhaustiveShare);
                } else {
                    search.withDefaultPhasePlan();
                }
                SearchStatus status = search.start(timeLimitInSeconds);
                return new Result(status, search.bestSolution(), search.bestObjectiveValue(), search.bestVarValuesMap());
            });
        } catch (InconsistencyException e) {
            return new Result(SearchStatus.UNSAT, Optional.empty(), Optional.empty(), Map.of());
        }
    }
}

