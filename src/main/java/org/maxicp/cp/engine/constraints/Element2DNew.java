/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.IntStream;


/**
 *
 * Element Constraint modeling {@code matrix[x][y] = z}
 *
 */
public class Element2DNew extends AbstractCPConstraint {

    private final int[][] matrix;
    private final CPIntVar x, y, z;
    private final int n,m;
    private int [] domx;
    private int [] domy;


    /**
     * Creates an element constraint {@code mat[x][y] = z}
     *
     * @param mat the 2d array representing a matrix to index
     * @param x the first dimension index variable
     * @param y the second dimention index variable
     * @param z the result variable
     */
    public Element2DNew(int[][] mat, CPIntVar x, CPIntVar y, CPIntVar z) {
        super(x.getSolver());
        this.matrix = mat;
        this.x = x;
        this.y = y;
        this.z = z;
        n = matrix.length;
        this.m = matrix[0].length;
        this.domx = new int[n];
        this.domy = new int[m];
    }

    @Override
    public void post() {
        x.removeBelow(0);
        x.removeAbove(n - 1);
        y.removeBelow(0);
        y.removeAbove(m - 1);
        x.propagateOnDomainChange(this);
        y.propagateOnDomainChange(this);
        z.propagateOnBoundChange(this);
        propagate();
    }


    @Override
    public void propagate() {
        int xs = x.fillArray(domx);
        int ys = y.fillArray(domy);
        int zMin = z.min();
        int zMax = z.max();
        
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        
        for (int i = 0; i < xs; i++) {
            int xi = domx[i];
            boolean xHasSupport = false;
            for (int j = 0; j < ys; j++) {
                int yj = domy[j];
                int val = matrix[xi][yj];
                if (val >= zMin && val <= zMax) {
                    xHasSupport = true;
                    if (val < min) min = val;
                    if (val > max) max = val;
                }
            }
            if (!xHasSupport) {
                x.remove(xi);
            }
        }
        
        for (int j = 0; j < ys; j++) {
            int yj = domy[j];
            boolean yHasSupport = false;
            for (int i = 0; i < xs; i++) {
                int xi = domx[i];
                int val = matrix[xi][yj];
                if (val >= zMin && val <= zMax) {
                    yHasSupport = true;
                }
            }
            if (!yHasSupport) {
                y.remove(yj);
            }
        }
        z.removeBelow(min);
        z.removeAbove(max);
    }
}
