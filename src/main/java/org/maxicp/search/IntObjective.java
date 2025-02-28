/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

public interface IntObjective extends Objective {

    /**
     * Activates or deactivates the filtering of the objective
     * @param activate
     */
    public void setFilter(boolean activate);

    /**
     * Sets the new value of the bound as
     * the one of the current solution + (maximization) or - (minimization) delta
     *
     * @param delta a positive integer
     */
    public void setDelta(int delta);


    /**
     * Sets the new value of the bound
     *
     * @param newBound
     */
    void setBound(int newBound);

    /**
     * Returns the current bound
     * @return the current bound
     */
    int getBound();




}
