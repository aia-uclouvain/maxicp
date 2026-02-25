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

public class Distance implements Constraint {

    public final SeqVar seqVar;
    public final int[][] distanceMatrix;
    public final IntExpression distance;

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
