package org.maxicp.cp.examples.raw.distance;

/**
 * A variant for the lower bound estimation
 */
public enum Variant {
    ORIGINAL, // lb = partial tour
    MIN_INPUT_SUM, // lb = sum of incoming edges
    MEAN_INPUT_AND_OUTPUT_SUM, // lb = sum of (min(incoming edge) + min(outgoing edge)) / 2
    MIN_DETOUR, // lb = sum of min detours (assume future detours not yet available for the API)
    MST, // lb = minimum spanning tree on the edges
    MATCHING_SUCCESSOR, // lb = minimum matching of the successors
    MATCHING_SUCCESSOR_LAGRANGIAN, // lb = minimum matching of the successors. Attempt to create as few strongly connected components as possible through lagrangian relaxation
}
