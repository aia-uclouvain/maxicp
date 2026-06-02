package org.maxicp.search.blackbox;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Convenience entry points for running {@link BlackBoxSearch} from the modeling layer.
 *
 * <p>The API exposes two workflows:</p>
 * <ul>
 *   <li>{@link #solve(ModelDispatcher, IntExpression[], int)} for feasibility problems.</li>
 *   <li>{@link #optimize(ModelDispatcher, IntExpression[], Objective, int)} for optimization problems.</li>
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
     */
    public record Result(SearchStatus status, Optional<List<Integer>> solution, Optional<Integer> objectiveValue) {
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
        return model.runCP(() -> {
            SolveConfig cfg = configurer.apply(new SolveConfig());
            BlackBoxSearch search = new BlackBoxSearch(model, decisionVars)
                    .withVerbosity(cfg.verbosity)
                    .withFeasibilityPlan(cfg.restart, cfg.exhaustive,
                            cfg.initialExhaustiveShare, cfg.restartShare, cfg.exhaustiveShare);
            SearchStatus status = search.start(timeLimitInSeconds);
            return new Result(status, search.bestSolution(), search.bestObjectiveValue());
        });
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
        return model.runCP(() -> {
            OptimizeConfig cfg = configurer.apply(new OptimizeConfig());
            BlackBoxSearch search = new BlackBoxSearch(model, decisionVars, objective)
                    .withVerbosity(cfg.verbosity)
                    .withOptimizationPlan(cfg.restart, cfg.lns, cfg.exhaustive,
                            cfg.initialExhaustiveShare, cfg.restartShare, cfg.lnsShare, cfg.exhaustiveShare);
            SearchStatus status = search.start(timeLimitInSeconds);
            return new Result(status, search.bestSolution(), search.bestObjectiveValue());
        });
    }
}

