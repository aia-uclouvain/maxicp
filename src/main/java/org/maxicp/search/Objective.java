/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.search;

/**
 * Objective object to be used
 * in the {@link DFSearch#optimize(Objective)}
 * for implementing the branch and bound depth first search.
 */
public interface Objective {

    /**
     * Method called each time a solution is found
     * during the search to let the tightening
     * of the primal bound occurs such that
     * the next found solution is better.
     */
    void tighten();

    /**
     * Relax the objective it can be deteriorated
     */
    void relax();

    /**
     * Filters the objective wrt to current bound
     * This method is typically executed at each node of a search tree
     */
    void filter();
}
