/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import java.util.function.Supplier;

/**
 * The StateManager exposes
 * all the mechanisms and data-structures
 * needed to implement a depth-first-search
 * with reversible states.
 */
public interface StateManager {

    /**
     * Stores the current state
     * such that it can be recovered using restoreState()
     * Increase the level by 1
     */
    void saveState();


    /**
     * Restores state as it was at getLevel()-1
     * Decrease the level by 1
     */
    void restoreState();

    /**
     * Restores the state up the the given level.
     *
     * @param level the level, a non negative number between 0 and {@link #getLevel()}
     */
    void restoreStateUntil(int level);

    /**
     * Add a listener that is notified each time the {@link #restoreState()}
     * is called.
     *
     * @param listener the listener to be notified
     */
    void onRestore(Runnable listener);

    /**
     * Returns the current level.
     * It is increased at each {@link #saveState()}
     * and decreased at each {@link #restoreState()}.
     * It is initially equal to -1.
     * @return the level
     */
    int getLevel();

    /**
     * Creates a Stateful reference (restorable)
     *
     * @param initValue the initial setValue
     * @return a State object wrapping the initValue
     */
    <T> State<T> makeStateRef(T initValue);

    /**
     * Creates a Stateful integer (restorable)
     *
     * @param initValue the initial setValue
     * @return a StateInt object wrapping the initValue
     */
    StateInt makeStateInt(int initValue);

    /**
     * Creates a Stateful long (restorable)
     *
     * @param initValue the initial setValue
     * @return a StateLong object wrapping the initValue
     */
    StateLong makeStateLong(long initValue);

    /**
     * Creates a Stateful map (restorable)
     *
     * @return a reference to the map.
     */
    <K, V> StateMap<K,V> makeStateMap();

    /**
     * Higher-order function that preserves the state prior to calling body and restores it after.
     *
     * @param body the first-order function to execute.
     */
    default void withNewState(Runnable body) {
        final int level = getLevel();
        saveState();
        try {
            body.run();
        }
        finally {
            restoreStateUntil(level);
        }
    }

    default <T> T withNewState(Supplier<T> body) {
        final int level = getLevel();
        saveState();
        try {
            return body.get();
        }
        finally {
            restoreStateUntil(level);
        }
    }
}

