package org.maxicp.cp.examples.raw.distance;

import org.apache.commons.cli.*;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.DistanceNew;
import org.maxicp.cp.engine.constraints.seqvar.distance.*;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public abstract class Benchmark {

    public Benchmark(String[] args) {
        try {
            this.args = String.join(" ", args);
            CommandLine cli = cli(args);
            instancePath = cli.getOptionValue("f");
            maxRunTimeMS = (long) (Double.parseDouble(cli.getOptionValue("t", "60.0")) * 1000.0);
            variant = Variant.valueOf(cli.getOptionValue("m").toUpperCase());
            verbosity = Integer.parseInt(cli.getOptionValue("v", "1"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.join(" ", args) + " | crashed");
            throw new RuntimeException(e);
        }
    }

    private static Options options() {
        Options options = new Options();
        options.addRequiredOption("f", "filename", true, "instance file");
        options.addOption("t", "timeout", true, "timeout");
        options.addOption("m", "variant", true, "variant for the distance constraint");
        Option verbosity = Option.builder("v").longOpt("verbose").hasArg().desc("verbosity").type(Integer.class).build();
        options.addOption(verbosity);
        return options;
    }

    private static CommandLine cli(final String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse(options(), args);
    }

    /**
     * Verbosity level:
     * 0: only print a one line summary once the problem has been solved
     * 1: 0 + print each time a solution is found
     */
    protected String args;
    protected long initialTimeMS;
    protected long maxRunTimeMS;
    protected int verbosity;
    protected String instancePath; // instance to solve
    protected List<CompactSolution> solutions = new ArrayList<CompactSolution>();  // All solutions found during the resolution
    protected Variant variant; // variant to use for the distance constraint
    protected SearchStatistics statistics; // statistics of the last call to the search

    /**
     * A solution found during the resolution
     */
    public record CompactSolution(double timeSeconds, int nNodes, int nFailures, double objective) {

        @Override
        public String toString() {
            return String.format("(t=%.3f; nodes=%d; fails=%d; obj=%.3f)", timeSeconds, nNodes, nFailures, objective);
        }
    };

    /**
     * Creates the search procedure that will be used to solve the problem
     */
    protected abstract DFSearch makeDFSearch();

    /**
     * Gives the objective to optimize
     */
    protected abstract Objective makeObjective();

    /**
     * Gives the value of the objective when all variables are fixed
     */
    protected abstract double getObjectiveValue();

    protected abstract void makeModel(String instancePath);

    public void addDistanceConstraint(CPSeqVar seqVar, int[][] distance, CPIntVar totLength) {
        switch (variant) {
            case ORIGINAL -> seqVar.getSolver().post(new DistanceOriginal(seqVar, distance, totLength));
            case MIN_INPUT_SUM -> seqVar.getSolver().post(new DistanceMinInputSum(seqVar, distance, totLength));
            case MEAN_INPUT_AND_OUTPUT_SUM -> seqVar.getSolver().post(new DistanceMinInputAndOutputSum(seqVar, distance, totLength));
            case MIN_DETOUR -> seqVar.getSolver().post(new DistanceMinDetourSum(seqVar, distance, totLength));
            case MST -> seqVar.getSolver().post(new DistanceMST(seqVar, distance, totLength));
            case MATCHING_SUCCESSOR -> seqVar.getSolver().post(new DistanceMatchingSuccessor(seqVar, distance, totLength));
            case MST_DETOUR -> seqVar.getSolver().post(new DistanceMSTDetour(seqVar, distance, totLength));
            case SCHEDULING -> seqVar.getSolver().post(new DistanceScheduling(seqVar, distance, totLength));
            case MATCHING_SUCCESSOR_LAGRANGIAN -> throw new RuntimeException("not yet implemented");
        }
    }

    public void addSearchListeners(DFSearch search) {
        search.onSolution(s -> {
            double time = elapsedSeconds();
            int nNodes = s.numberOfNodes();
            int nFailures = s.numberOfFailures();
            double objective = getObjectiveValue();
            solutions.add(new CompactSolution(time, nNodes, nFailures, objective));
        });
        if (verbosity >= 1) {
            search.onSolution(() -> System.out.println(solutions.getLast()));
        }
    }

    public boolean isTimeout() {
        return System.currentTimeMillis() - initialTimeMS > maxRunTimeMS;
    }

    public double elapsedSeconds() {
        return (double) (System.currentTimeMillis() - initialTimeMS) / 1000.0;
    }

    public void solve() {
        try {
            initialTimeMS = System.currentTimeMillis();
            // initialize the model
            makeModel(instancePath);
            // make the search and add listeners
            DFSearch search = makeDFSearch();
            Objective objective = makeObjective();
            addSearchListeners(search);
            // solve the problem
            statistics = search.optimize(objective, s -> isTimeout());
            System.out.println(this);
        } catch (Exception e) {
            System.out.println(String.join(" ", args) + " | crashed");
            e.printStackTrace();
        }
    }

    private static void bellmanFord(int numNodes, int edgeCount, int[][] edges, int src, long[] dist) throws Exception {
        // Initially distance from source to all other vertices
        // is not known(Infinite).
        int INF = Integer.MAX_VALUE;
        Arrays.fill(dist, INF);
        dist[src] = 0;
        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodes; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (dist[u] != INF && dist[u] + wt < dist[v]) {
                    // V_th relaxation => negative cycle
                    if (i == numNodes - 1) {
                        throw new Exception();
                    }
                    // Update shortest distance to node v
                    dist[v] = dist[u] + wt;
                }
            }
        }
    }

    public static void makeTriangularInequality(int[][] distance) {
        int n = distance.length;
        int[][] edges = new int[n * n][3];
        int edgeCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                edges[edgeCount][0] = i;
                edges[edgeCount][1] = j;
                edges[edgeCount][2] = distance[i][j];
                edgeCount++;
            }
        }
        for (int i = 0; i < n; i++) {
            long[] dist = new long[n];
            try {
                bellmanFord(n, edgeCount, edges, i, dist);
                for (int j = 0; j < n; j++) {
                    distance[i][j] = (int) dist[j];
                }
            } catch (Exception e) {
                System.out.println("negative cycle");
            }
        }
    }

    private String searchStatsString() {
        return statistics == null ? " | | | " : String.format("%d | %d | %d | %b", statistics.numberOfNodes(), statistics.numberOfFailures(), statistics.numberOfSolutions(), statistics.isCompleted());
    }

    private String bestSolutionString() {
        if (solutions == null || solutions.isEmpty()) {
            return "";
        } else {
            return String.format("%.3f", solutions.getLast().objective());
        }
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s | %.3f | %.3f | %s | %s | %s",
                this.getClass().getSimpleName(),
                instancePath,
                variant,
                bestSolutionString(),
                (double) maxRunTimeMS / 1000.0,
                elapsedSeconds(),
                searchStatsString(),
                solutions.stream().map(CompactSolution::toString).collect(Collectors.joining(" ", "[", "]")),
                args);
    }
}
