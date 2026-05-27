package org.maxicp.cp.engine.nogoods;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.maxicp.cp.engine.constraints.EnforceNogood;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

public class Restarter {
    protected CPSolver solver;
    protected DFSearch search;
    protected BiPredicate<RestartSearchStatistics, SearchStatistics> shouldRestart;
    protected Predicate<RestartSearchStatistics> shouldStop;

    public class RestartSearchStatistics extends SearchStatistics {
        public int nRestarts = 0;

        @Override
        public String toString() {
            return super.toString() + "\n\t#restarts: " + nRestarts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            RestartSearchStatistics that = (RestartSearchStatistics) o;
            return nFailures == that.nFailures && nNodes == that.nNodes && nSolutions == that.nSolutions
                    && completed == that.completed && nRestarts == that.nRestarts;
        }

        @Override
        public int hashCode() {
            return Objects.hash(nFailures, nNodes, nSolutions, completed, nRestarts);
        }

        public void increaseRun(SearchStatistics runStats) {
            nFailures += runStats.numberOfFailures();
            nNodes += runStats.numberOfNodes();
            nSolutions += runStats.numberOfSolutions();
            completed = runStats.isCompleted();
            nRestarts++;
        }
    }

    public Restarter(CPSolver solver, DFSearch search) {
        this.solver = solver;
        this.search = search;
        this.shouldRestart = new LubyRestart(100); // by default, use Luby restarts with multiplier 100
        this.shouldStop = stats -> false; // by default, never stop
    }

    public void setRunLimit(BiPredicate<RestartSearchStatistics, SearchStatistics> shouldRestart) {
        this.shouldRestart = shouldRestart;
    }

    public void setRestartLimit(Predicate<RestartSearchStatistics> shouldStop) {
        this.shouldStop = shouldStop;
    }

    /**
     * Returns a BiPredicate that implements the Luby restart strategy with the
     * given base and multiplier.
     * Based on the code from Charles Prud'homme, Arnaud Malapert, Hadrien Cambazard
     * in the Choco-solver, in BSD-3 clause license.
     */
    public static class LubyRestart implements BiPredicate<RestartSearchStatistics, SearchStatistics> {
        protected int scale;
        protected int currentRun;
        protected int externalCounter;
        protected int curVal;
        protected BiPredicate<RestartSearchStatistics, SearchStatistics> shouldStop;

        public LubyRestart(int scale) {
            this(scale, null);
        }

        public LubyRestart(int scale, BiPredicate<RestartSearchStatistics, SearchStatistics> shouldStop) {
            this.scale = scale;
            this.currentRun = 0;
            this.curVal = 1;
            this.externalCounter = 1;
            this.shouldStop = shouldStop;
        }

        protected void computeNextCutOff() {
            currentRun++;
            if ((this.externalCounter & -this.externalCounter) == this.curVal) {
                this.externalCounter += 1;
                this.curVal = 1;
            } else {
                this.curVal <<= 1;
            }
        }

        @Override
        public boolean test(RestartSearchStatistics stats, SearchStatistics lastRunStats) {
            while (stats.nRestarts > this.currentRun)
                computeNextCutOff();
            if (shouldStop != null && shouldStop.test(stats, lastRunStats))
                return true;
            return lastRunStats.numberOfNodes() >= this.scale * this.curVal;
        }
    }

    public RestartSearchStatistics solve() {
        RestartSearchStatistics stats = new RestartSearchStatistics();
        solver.getStateManager().withNewState(() -> {
            NoGoodGenerator maker = new NoGoodGenerator(solver, search);
            EnforceNogood enforcer = new EnforceNogood(solver);
            while (!shouldStop.test(stats)) {
                maker.clear();
                SearchStatistics runStats = search.solve(s -> this.shouldRestart.test(stats, s));
                stats.increaseRun(runStats);
                if (runStats.isCompleted())
                    break;
                enforcer.addNogood(maker.getNoGood());
            }
        });
        return stats;
    }

    public RestartSearchStatistics solveSubjectTo(Runnable subjectTo) {
        return solver.getStateManager().withNewState(() -> {
            subjectTo.run();
            return solve();
        });
    }

    public RestartSearchStatistics optimize(Objective obj) {
        RestartSearchStatistics stats = new RestartSearchStatistics();
        solver.getStateManager().withNewState(() -> {
            NoGoodGenerator maker = new NoGoodGenerator(solver, search);
            EnforceNogood enforcer = new EnforceNogood(solver);
            while (!shouldStop.test(stats)) {
                maker.clear();
                SearchStatistics runStats = search.optimize(obj, s -> this.shouldRestart.test(stats, s));
                stats.increaseRun(runStats);
                if (runStats.isCompleted())
                    break;
                enforcer.addNogood(maker.getNoGood());
            }
        });
        return stats;
    }

    public RestartSearchStatistics optimize(org.maxicp.modeling.symbolic.Objective obj) {
        ConcreteModel model = obj.getModelProxy().getConcreteModel();
        Objective objective = model.createObjective(obj);
        return optimize(objective);
    }

    public RestartSearchStatistics optimizeSubjectTo(Objective objToTighten, Runnable subjectTo) {
        return solver.getStateManager().withNewState(() -> {
            subjectTo.run();
            return optimize(objToTighten);
        });
    }

    public RestartSearchStatistics optimizeSubjectTo(org.maxicp.modeling.symbolic.Objective objToTighten,
            Runnable subjectTo) {
        return solver.getStateManager().withNewState(() -> {
            ConcreteModel model = objToTighten.getModelProxy().getConcreteModel();
            Objective objective = model.createObjective(objToTighten);
            subjectTo.run();
            return optimize(objective);
        });
    }
}
