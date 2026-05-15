package org.maxicp.modeling.xcsp3;

import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.NotImplementedException;

import java.util.Locale;

/**
 * Competition-oriented CLI for XCSP3 instances.
 */
public final class XCSP3Cli {

    private XCSP3Cli() {
        // Utility class.
    }

    public static void main(String[] args) {
        String benchPath = findBenchPath(args);
        if (benchPath == null) {
            System.out.println("s UNKNOWN");
            return;
        }

        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(benchPath)) {
            IntExpression[] vars = instance.decisionVars();
            SolveOutcome outcome = instance.md().runCP((cp) -> {
                DFSearch search = cp.dfSearch(new FDSModeling(vars));
                SolveOutcome current = new SolveOutcome(instance.objective());

                search.onSolution(() -> {
                    current.bestSolution = instance.solutionGenerator().get();
                    if (instance.objective() != null) {
                        int currentObj = evaluateObjective(instance.objective());
                        if (current.isBetterObjective(currentObj)) {
                            current.bestObjective = currentObj;
                            System.out.println("o " + currentObj);
                            System.out.flush();
                        }
                    }
                });

                SearchStatistics stats = instance.objective() != null
                        ? search.optimize(instance.objective())
                        : search.solve();
                current.stats = stats;
                return current;
            });

            printFinalStatus(outcome);
            if (outcome.bestSolution != null) {
                System.out.println("v " + normalizeSolution(outcome.bestSolution));
            }
        } catch (NotImplementedException e) {
            System.out.println("s UNSUPPORTED");
        } catch (Throwable t) {
            System.out.println("s UNKNOWN");
            System.out.println("c " + t.getClass().getSimpleName() + ": " + sanitizeForComment(t.getMessage()));
        }
    }

    private static String findBenchPath(String[] args) {
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.startsWith("--mem-limit=")
                    || lower.startsWith("--time-limit=")
                    || lower.startsWith("--tmpdir=")
                    || lower.startsWith("--seed=")) {
                continue;
            }
            if (!arg.startsWith("-")) {
                return arg;
            }
        }
        return null;
    }

    private static void printFinalStatus(SolveOutcome outcome) {
        SearchStatistics stats = outcome.stats;
        boolean hasSolution = outcome.bestSolution != null;

        if (outcome.objective == null) {
            if (hasSolution) {
                System.out.println("s SATISFIABLE");
            } else if (stats != null && stats.isCompleted()) {
                System.out.println("s UNSATISFIABLE");
            } else {
                System.out.println("s UNKNOWN");
            }
            return;
        }

        if (hasSolution) {
            if (stats != null && stats.isCompleted()) {
                System.out.println("s OPTIMUM FOUND");
            } else {
                System.out.println("s SATISFIABLE");
            }
        } else if (stats != null && stats.isCompleted()) {
            System.out.println("s UNSATISFIABLE");
        } else {
            System.out.println("s UNKNOWN");
        }
    }

    private static int evaluateObjective(Objective objective) {
        try {
            if (objective instanceof Minimization min) {
                return min.expr().evaluate();
            }
            if (objective instanceof Maximization max) {
                return max.expr().evaluate();
            }
            throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
        } catch (VariableNotFixedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String normalizeSolution(String solution) {
        return solution.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String sanitizeForComment(String text) {
        if (text == null || text.isBlank()) {
            return "unexpected error";
        }
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class SolveOutcome {
        private final Objective objective;
        private SearchStatistics stats;
        private String bestSolution;
        private Integer bestObjective;

        private SolveOutcome(Objective objective) {
            this.objective = objective;
        }

        private boolean isBetterObjective(int candidate) {
            if (bestObjective == null) {
                return true;
            }
            if (objective instanceof Minimization) {
                return candidate < bestObjective;
            }
            if (objective instanceof Maximization) {
                return candidate > bestObjective;
            }
            return false;
        }
    }
}

