/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.state.StateManager;

import javax.lang.model.type.NullType;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An abstract search method that can be used concurrently: it is stoppable and can be work-stealed.
 *
 * An additional constraint w.r.t. AbstractSearchMethod is that an AbstractConcurrentSearchMethod
 * MUST NOT be running more than one solving process at a time.
 */
public abstract class AbstractConcurrentSearchMethod extends AbstractSearchMethod<SymbolicModel> {
    protected SearchStatistics statistics;
    protected Runnable onNodeVisit;
    private final BlockingQueue<Message> queue;

    private sealed interface Message {
        void cancel(Throwable ex);
    };

    private record StopMessage(CompletableFuture<NullType> future) implements Message {
        @Override
        public void cancel(Throwable ex) {
            future.completeExceptionally(ex);
        }
    };

    private record StealMessage(CompletableFuture<SymbolicModel> future) implements Message {
        @Override
        public void cancel(Throwable ex) {
            future.completeExceptionally(ex);
        }
    };

    public AbstractConcurrentSearchMethod(StateManager sm, Supplier<SymbolicModel[]> branching) {
        super(sm, branching);
        statistics = null;
        onNodeVisit = null;
        queue = new LinkedBlockingQueue<>();
    }

    /**
     * @return true if there are no more nodes to visit
     */
    protected abstract boolean done();

    /**
     * Called when a new search is started
     */
    protected abstract void initSolve();

    /**
     * process the next step of the search procedure.
     * done() must be false to call this function.
     */
    protected abstract void processNextStep();

    /**
     * Called when the current search is finished
     */
    protected abstract void finishSolve();

    /**
     * Remove a model from the "list of open models" and returns it.
     * This function will be called on the same thread as startSolve if it's running, and is not needed to be thread-safe.
     *
     * The function is allowed to return null if and only if there is no Model left to visit.
     */
    protected abstract SymbolicModel extractModel();

    /**
     * Steal a (yet non-visited) model from this search method. Thread-safe.
     *
     * The function is allowed to return null if and only if there is no Model left to visit.
     */
    public Future<SymbolicModel> steal() {
        CompletableFuture<SymbolicModel> future = new CompletableFuture<>();
        queue.add(new StealMessage(future));
        return future;
    }

    /**
     * Stop a solving process (thread-safely)
     * @return a Fu
     */
    public Future<NullType> stop() {
        CompletableFuture<NullType> future = new CompletableFuture<>();
        queue.add(new StopMessage(future));
        return future;
    }

    private void processQueue() {
        while (!queue.isEmpty()) {
            Message message = queue.poll();
            switch (message) {
                case StealMessage stealMessage -> stealMessage.future.complete(extractModel());
                case StopMessage stopMessage -> {
                    while (!queue.isEmpty()) {
                        Message bis = queue.poll();
                        bis.cancel(new StopSearchException());
                    }
                    stopMessage.future.complete(null);
                    throw new StopSearchException();
                }
            }
        }
    }

    @Override
    protected void startSolve(SearchStatistics statistics, Predicate<SearchStatistics> limit, Runnable onNodeVisit) {
        this.statistics = statistics;
        this.onNodeVisit = onNodeVisit;
        sm.withNewState(() -> {
            initSolve();
            try {
                while(!done()) {
                    if (limit.test(statistics)) throw new StopSearchException();
                    processQueue();
                    processNextStep();
                }
            }
            finally {
                finishSolve();
            }
        });
    }
}
