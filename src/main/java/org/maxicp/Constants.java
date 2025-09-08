/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp;

/**
 * Constants used in the MaxiCP library
 */
public class Constants {

    /**
     * Default Scheduling maximum time horizon
     */
    public final static int HORIZON = 100000000;

    /**
     * High priority for a constraint in the propagation queue
     * This is the default priority, to be used for light propagators
     */
    public final static int PIORITY_FAST = 0;

    /**
     * Medium priority for a constraint in the propagation queue
     * To be used for medium complexity propagators
     */
    public final static int PIORITY_MEDIUM = 1;

    /**
     * Low priority for a constraint in the propagation queue
     * To be used for heavy (slow) propagators
     */
    public final static int PIORITY_SLOW = 2;


    public enum CumulativeAlgo {
        BELDICEANU_CARLSSON,
        SCHAUS_THOMAS_KAMEUGNE,
        SCHAUS_THOMAS_KAMEUGNE_BASELINE,
    }
}
