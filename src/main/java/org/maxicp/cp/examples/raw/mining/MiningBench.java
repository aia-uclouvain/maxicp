/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.mining;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.ClosedFimNv;
import org.maxicp.cp.engine.constraints.patternvar.ClosedItemset;
import org.maxicp.cp.engine.constraints.patternvar.ClosedNv;
import org.maxicp.cp.engine.constraints.patternvar.CoverSize;
import org.maxicp.cp.engine.constraints.patternvar.MinSupportItemset;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Benchmark driver comparing frequent-itemset enumeration at two modeling levels
 * (Gate 2 of the PatternVar research plan):
 * <ul>
 *   <li>{@code nvgc}: Boolean item variables + CoverSize global constraint + support bound
 *       (the native-variable, constraint-owned-state model);</li>
 *   <li>{@code pv-supvar}: pattern variable + its support view + support bound
 *       (the same filtering, expressed through the variable kernel);</li>
 *   <li>{@code pv}: pattern variable + the dedicated MinSupportItemset constraint
 *       (no support lower-bound maintenance);</li>
 *   <li>{@code pv-minext}: as pv, with the min-extension-support branching heuristic.</li>
 * </ul>
 * All models use the same reversible sparse-bitset algorithms; nvgc, pv-supvar and pv
 * use the identical static min-item search order so that node counts coincide.
 * <p>
 * Usage: {@code MiningBench <file> <theta> <nvgc|pv|pv-supvar|pv-minext> <nRuns>}
 * — prints one CSV line per run: {@code model,dataset,theta,run,timeMs,nodes,fails,sols}
 * (run 0 is a warm-up).
 */
public class MiningBench {

    static Supplier<Runnable[]> staticBoolBranching(CPSolver cp, CPBoolVar[] xs) {
        return () -> {
            CPBoolVar x = null;
            for (CPBoolVar candidate : xs) {
                if (!candidate.isFixed()) {
                    x = candidate;
                    break;
                }
            }
            if (x == null)
                return EMPTY;
            CPBoolVar selected = x;
            return branch(
                    () -> {
                        selected.fix(true);
                        cp.fixPoint();
                    },
                    () -> {
                        selected.fix(false);
                        cp.fixPoint();
                    });
        };
    }

    static SearchStatistics run(TransactionDb db, int theta, String model) {
        CPSolver cp = makeSolver();
        DFSearch search;
        switch (model) {
            case "nvgc" -> {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = makeIntVar(cp, 0, db.nTrans());
                cp.post(new CoverSize(items, support, db));
                cp.post(ge(support, theta));
                search = CPFactory.makeDfs(cp, staticBoolBranching(cp, items));
            }
            case "pv-supvar" -> {
                CPItemsetVar pattern = makeItemsetVar(cp, db);
                cp.post(ge(pattern.support(), theta));
                search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
            }
            case "pv" -> {
                CPItemsetVar pattern = makeItemsetVar(cp, db);
                cp.post(new MinSupportItemset(pattern, theta));
                search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
            }
            case "pv-minext" -> {
                CPItemsetVar pattern = makeItemsetVar(cp, db);
                cp.post(new MinSupportItemset(pattern, theta));
                search = CPFactory.makeDfs(cp, PatternSearches.minExtensionSupport(pattern));
            }
            // closed frequent mining, three architectures (Gate 4 ablation):
            // shared kernel (pv-closed), fused monolithic propagator (nvgc-closed-fused),
            // and two independent propagators duplicating the cover (nvgc-closed-dup)
            case "pv-closed" -> {
                CPItemsetVar pattern = makeItemsetVar(cp, db);
                cp.post(new MinSupportItemset(pattern, theta));
                cp.post(new ClosedItemset(pattern));
                search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
            }
            case "nvgc-closed-fused" -> {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                cp.post(new ClosedFimNv(items, theta, db));
                search = CPFactory.makeDfs(cp, staticBoolBranching(cp, items));
            }
            case "nvgc-closed-dup" -> {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = makeIntVar(cp, 0, db.nTrans());
                cp.post(new CoverSize(items, support, db));
                cp.post(ge(support, theta));
                cp.post(new ClosedNv(items, db));
                search = CPFactory.makeDfs(cp, staticBoolBranching(cp, items));
            }
            default -> throw new IllegalArgumentException("unknown model " + model);
        }
        return search.solve();
    }

    public static void main(String[] args) {
        String file = args[0];
        int theta = Integer.parseInt(args[1]);
        String model = args[2];
        int nRuns = args.length > 3 ? Integer.parseInt(args[3]) : 3;

        TransactionDb db = MiningIO.readTransactionDb(file);
        String dataset = file.substring(file.lastIndexOf('/') + 1).replace(".txt", "");

        for (int run = 0; run <= nRuns; run++) { // run 0 is a warm-up
            long start = System.currentTimeMillis();
            SearchStatistics stats = run(db, theta, model);
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("%s,%s,%d,%d,%d,%d,%d,%d%n",
                    model, dataset, theta, run, elapsed,
                    stats.numberOfNodes(), stats.numberOfFailures(), stats.numberOfSolutions());
        }
    }
}
