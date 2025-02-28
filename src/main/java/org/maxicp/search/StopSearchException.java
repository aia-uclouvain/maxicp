/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

/**
 * Exception that is thrown to stop
 * the execution of {@link DFSearch#solve()}, {@link DFSearch#optimize(Objective)}
 */
public class StopSearchException extends RuntimeException {
    private static final long serialVersionUID = 2079205745523222197L;
}