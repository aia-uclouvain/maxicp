/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.Collection;
import java.util.List;

/**
 * Modeling-level distance constraint on a sequence variable ({@link SeqVar}), maintaining the total travel
 * distance between consecutive visited nodes in the sequence.
 */
public class Distance implements Constraint {

    public final SeqVar seqVar;
    public final int[][] distanceMatrix;
    public final IntExpression distance;

    /**
     * Creates a constraint maintaining that {@code distance} equals the sum of transition distances
     * between consecutive nodes visited in {@code seqVar}.
     *
     * @param seqVar         the sequence variable
     * @param distanceMatrix the distance matrix between nodes (must satisfy the triangle inequality)
     * @param distance       the expression representing the total travel distance
     */
    public Distance(SeqVar seqVar, int[][] distanceMatrix, IntExpression distance) {
        this.seqVar = seqVar;
        this.distanceMatrix = distanceMatrix;
        this.distance = distance;
        DistanceMatrix.checkTriangularInequality(distanceMatrix);
    }

    @Override
    public Collection<? extends Expression> scope() {
        return List.of(seqVar, distance);
    }
}
