package org.maxicp.modeling.xcsp3;

import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.search.blackbox.ModelingBlackBox;
import org.maxicp.search.blackbox.SearchStatus;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.NotImplementedException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Competition-oriented CLI for XCSP3 instances.
 */
public final class XCSP3Cli {

    private XCSP3Cli() {
        // Utility class.
    }

    public static void main(String[] args) {
        long startMillis = System.currentTimeMillis();
        String benchPath = findBenchPath(args);
        if (benchPath == null) {
            System.out.println("s UNKNOWN");
            return;
        }

        final Object outputLock = new Object();
        final AtomicBoolean outputFinished = new AtomicBoolean(false);
        final AtomicReference<String> latestFormattedSolution = new AtomicReference<>();
        final AtomicReference<Integer> lastPrintedObjective = new AtomicReference<>();
        final AtomicReference<Objective> objectiveRef = new AtomicReference<>();

        Thread shutdownHook = new Thread(() -> {
            synchronized (outputLock) {
                if (outputFinished.get()) {
                    return;
                }
                outputFinished.set(true);
                String solution = latestFormattedSolution.get();
                if (solution != null) {
                    System.out.println("s SATISFIABLE");
                    System.out.println("v " + normalizeSolution(solution));
                } else {
                    System.out.println("s UNKNOWN");
                }
                System.out.flush();
            }
        }, "xcsp3-cli-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(benchPath)) {
            objectiveRef.set(instance.objective());

            int requestedLimitSeconds = findTimeLimitSeconds(args);
            int effectiveTimeLimitSeconds = computeEffectiveTimeLimitSeconds(requestedLimitSeconds, startMillis);
            OptionalLong randomSeed = findRandomSeed(args, benchPath);

            ModelingBlackBox.Result outcome = instance.objective() != null
                    ? ModelingBlackBox.optimize(
                            instance.md(),
                            instance.objective(),
                            effectiveTimeLimitSeconds,
                            cfg -> {
                                if (randomSeed.isPresent()) {
                                    configureOptimizeSeed(cfg, randomSeed.getAsLong());
                                }
                                cfg.onIncumbent((sol, objVal) -> {
                                    synchronized (outputLock) {
                                        if (outputFinished.get()) {
                                            return;
                                        }
                                        latestFormattedSolution.set(instance.solutionGenerator().get());
                                        if (objVal != null && !Objects.equals(lastPrintedObjective.get(), objVal)) {
                                            lastPrintedObjective.set(objVal);
                                            System.out.println("o " + objVal);
                                            System.out.flush();
                                        }
                                    }
                                });
                                return cfg;
                            })
                    : ModelingBlackBox.solve(
                            instance.md(),
                            effectiveTimeLimitSeconds,
                            cfg -> {
                                if (randomSeed.isPresent()) {
                                    configureSolveSeed(cfg, randomSeed.getAsLong());
                                }
                                cfg.onIncumbent((sol, objVal) -> {
                                    synchronized (outputLock) {
                                        if (!outputFinished.get()) {
                                            latestFormattedSolution.set(instance.solutionGenerator().get());
                                        }
                                    }
                                });
                                return cfg;
                            });

            synchronized (outputLock) {
                if (!outputFinished.get()) {
                    outputFinished.set(true);
                    if (outcome.objectiveValue().isPresent()) {
                        Integer finalObj = outcome.objectiveValue().get();
                        if (!Objects.equals(lastPrintedObjective.get(), finalObj)) {
                            System.out.println("o " + finalObj);
                            System.out.flush();
                        }
                    }

                    printFinalStatus(outcome.status(), instance.objective(), outcome.solution().isPresent());
                    if (outcome.solution().isPresent()) {
                        String solution = instance.formatSolution(outcome);
                        System.out.println("v " + normalizeSolution(solution));
                    }
                    System.out.flush();
                }
            }
        } catch (InconsistencyException e) {
            synchronized (outputLock) {
                if (!outputFinished.getAndSet(true)) {
                    System.out.println("s UNSATISFIABLE");
                    System.out.flush();
                }
            }
        } catch (NotImplementedException e) {
            synchronized (outputLock) {
                if (!outputFinished.getAndSet(true)) {
                    System.out.println("s UNSUPPORTED");
                    System.out.flush();
                }
            }
        } catch (Throwable t) {
            synchronized (outputLock) {
                if (!outputFinished.getAndSet(true)) {
                    String solution = latestFormattedSolution.get();
                    if (solution != null) {
                        System.out.println("s SATISFIABLE");
                        System.out.println("v " + normalizeSolution(solution));
                    } else {
                        System.out.println("s UNKNOWN");
                        System.out.println("c " + t.getClass().getSimpleName() + ": " + sanitizeForComment(t.getMessage()));
                    }
                    System.out.flush();
                }
            }
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
        }
    }

    private static int computeEffectiveTimeLimitSeconds(int requestedLimitSeconds, long startMillis) {
        if (requestedLimitSeconds == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startMillis);
        long safetyMarginMillis = requestedLimitSeconds >= 5 ? 800L : 0L;
        long remainingMillis = requestedLimitSeconds * 1000L - elapsedMillis - safetyMarginMillis;
        return (int) Math.max(1L, remainingMillis / 1000L);
    }

    private static int findTimeLimitSeconds(String[] args) {
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("--time-limit=")) {
                continue;
            }
            String raw = arg.substring(arg.indexOf('=') + 1).trim();
            if (raw.isEmpty()) {
                continue;
            }
            try {
                // Competition wrappers typically pass integer seconds; tolerate decimals too.
                int parsed = (int) Math.ceil(Double.parseDouble(raw));
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed time-limit and fallback below.
            }
        }
        return Integer.MAX_VALUE;
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

    private static OptionalLong findRandomSeed(String[] args, String benchPath) {
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("--seed=")) {
                continue;
            }
            String raw = arg.substring(arg.indexOf('=') + 1).trim();
            OptionalLong seed = parseSeed(raw);
            if (seed.isPresent()) {
                return seed;
            }
        }

        boolean seenBenchPath = false;
        for (String arg : args) {
            if (arg.startsWith("-")) {
                continue;
            }
            if (!seenBenchPath && arg.equals(benchPath)) {
                seenBenchPath = true;
                continue;
            }
            OptionalLong seed = parseSeed(arg.trim());
            if (seed.isPresent()) {
                return seed;
            }
        }
        return OptionalLong.empty();
    }

    private static OptionalLong parseSeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private static ModelingBlackBox.SolveConfig configureSolveSeed(ModelingBlackBox.SolveConfig cfg, long seed) {
        BlackBoxSearch.RestartPhaseOptions restartDefaults = BlackBoxSearch.RestartPhaseOptions.defaults();
        BlackBoxSearch.ExhaustivePhaseOptions exhaustiveDefaults = BlackBoxSearch.ExhaustivePhaseOptions.defaults();
        return cfg
                .restart(new BlackBoxSearch.RestartPhaseOptions(
                        restartDefaults.baseFailureLimit(),
                        restartDefaults.randomSwapProbability(),
                        seed))
                .exhaustive(new BlackBoxSearch.ExhaustivePhaseOptions(
                        exhaustiveDefaults.randomSwapProbability(),
                        seed + 1));
    }

    private static ModelingBlackBox.OptimizeConfig configureOptimizeSeed(ModelingBlackBox.OptimizeConfig cfg, long seed) {
        BlackBoxSearch.RestartPhaseOptions restartDefaults = BlackBoxSearch.RestartPhaseOptions.defaults();
        BlackBoxSearch.LnsPhaseOptions lnsDefaults = BlackBoxSearch.LnsPhaseOptions.defaults();
        BlackBoxSearch.ExhaustivePhaseOptions exhaustiveDefaults = BlackBoxSearch.ExhaustivePhaseOptions.defaults();
        return cfg
                .restart(new BlackBoxSearch.RestartPhaseOptions(
                        restartDefaults.baseFailureLimit(),
                        restartDefaults.randomSwapProbability(),
                        seed))
                .lns(new BlackBoxSearch.LnsPhaseOptions(
                        lnsDefaults.failureLimitPerRestart(),
                        lnsDefaults.freezeRatePercent(),
                        lnsDefaults.randomSwapProbability(),
                        seed + 1,
                        lnsDefaults.fragmentSelectionStrategy()))
                .exhaustive(new BlackBoxSearch.ExhaustivePhaseOptions(
                        exhaustiveDefaults.randomSwapProbability(),
                        seed + 2));
    }

    private static void printFinalStatus(SearchStatus status, Objective objective, boolean hasSolution) {
        if (objective == null) {
            switch (status) {
                case SAT, IMPROVED -> System.out.println("s SATISFIABLE");
                case UNSAT -> System.out.println("s UNSATISFIABLE");
                default -> System.out.println(hasSolution ? "s SATISFIABLE" : "s UNKNOWN");
            }
            return;
        }

        switch (status) {
            case PROVEN_OPTIMAL -> System.out.println("s OPTIMUM FOUND");
            case IMPROVED, SAT, NOT_IMPROVED -> System.out.println(hasSolution ? "s SATISFIABLE" : "s UNKNOWN");
            case UNSAT -> System.out.println("s UNSATISFIABLE");
            case UNKNOWN -> System.out.println(hasSolution ? "s SATISFIABLE" : "s UNKNOWN");
        }
    }


    private static String buildSolution(String[] varIds, java.util.List<Integer> values) {
        if (varIds.length != values.size()) {
            throw new IllegalArgumentException("decision variable ids and values must have same size");
        }
        StringBuilder b = new StringBuilder();
        b.append("<instantiation>\n\t<list>\n\t\t");
        b.append(String.join(" ", varIds));
        b.append("\n\t</list>\n\t<values>\n\t\t");
        b.append(values.stream().map(String::valueOf).reduce((a, c) -> a + " " + c).orElse(""));
        b.append("\n\t</values>\n</instantiation>");
        return b.toString();
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

}

