/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.util.exception.InconsistencyException;

import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BestFirstSearch<T extends Comparable<T>> extends RunnableSearchMethod {
    private Supplier<T> nodeEvaluator;
    private ModelProxy modelProxy;
    private PriorityQueue<PQEntry<T>> pq = new PriorityQueue<>();

    record PQEntry<T extends Comparable<T>>(T order, SymbolicModel m) implements Comparable<PQEntry<T>> {
        @Override
        public int compareTo(PQEntry<T> o) {
            return this.order.compareTo(o.order);
        }
    }

    // TODO best first search with limited number of opened nodes
    public BestFirstSearch(ModelProxy modelProxy, Supplier<Runnable[]> branching, Supplier<T> nodeEvaluator) {
        super(modelProxy.getConcreteModel().getStateManager(), branching);
        this.modelProxy = modelProxy;
        this.nodeEvaluator = nodeEvaluator;
    }

    @Override
    protected void startSolve(SearchStatistics statistics, Predicate<SearchStatistics> limit, Runnable onNodeVisit, DFSListener listener) {
        pq.clear();
        pq.add(new PQEntry<>(nodeEvaluator.get(), modelProxy.getModel().symbolicCopy()));

        while (!pq.isEmpty()) {
            if (limit.test(statistics))
                throw new StopSearchException();

            SymbolicModel m = pq.poll().m;
            statistics.incrNodes();

            sm.saveState();
            try {
                // jumpTo will perform restore operations until it finds the common ancestor
                modelProxy.getConcreteModel().jumpTo(m);
            } catch (InconsistencyException e) {
                sm.restoreState();
                continue;
            }

            Runnable[] alts = branching.get();

            if (alts.length == 0) {
                statistics.incrSolutions();
                notifySolution();
            } else {
                for (Runnable b : alts) {
                    sm.saveState();
                    try {
                        onNodeVisit.run();
                        b.run();
                        pq.add(new PQEntry<>(nodeEvaluator.get(), modelProxy.getModel().symbolicCopy()));
                    } catch (InconsistencyException e) {
                        statistics.incrFailures();
                        notifyFailure();
                    }
                    sm.restoreState();
                }
            }
        }
    }

    public SymbolicModel[] getUnexploredModels() {
        return pq.stream().map(x -> x.m).toArray(SymbolicModel[]::new);
    }
}
