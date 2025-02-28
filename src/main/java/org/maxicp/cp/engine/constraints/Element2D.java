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
public class Element2D extends AbstractCPConstraint {

    private final int[][] matrix;
    private final CPIntVar x, y, z;
    private int n, m;
    private final StateInt[] nRowsSup;
    private final StateInt[] nColsSup;

    private final StateInt low;
    private final StateInt up;
    private final ArrayList<Triple> xyz;

    private static final class Triple implements Comparable<Triple> {
        private final int x, y, z;

        private Triple(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compareTo(Triple t) {
            return z - t.z;
        }
    }

    /**
     * Creates an element constraint {@code mat[x][y] = z}
     *
     * @param mat the 2d array representing a matrix to index
     * @param x the first dimension index variable
     * @param y the second dimention index variable
     * @param z the result variable
     */
    public Element2D(int[][] mat, CPIntVar x, CPIntVar y, CPIntVar z) {
        super(x.getSolver());
        this.matrix = mat;
        this.x = x;
        this.y = y;
        this.z = z;
        n = matrix.length;
        this.m = matrix[0].length;
        this.xyz = new ArrayList<Triple>();
        for (int i = 0; i < matrix.length; i++)
            for (int j = 0; j < matrix[i].length; j++)
                xyz.add(new Triple(i, j, matrix[i][j]));
        Collections.sort(xyz);
        StateManager sm = getSolver().getStateManager();
        low = sm.makeStateInt(0);
        up = sm.makeStateInt(xyz.size() - 1);
        nColsSup = IntStream.range(0, n).mapToObj(i -> sm.makeStateInt(this.m)).toArray(StateInt[]::new);
        nRowsSup = IntStream.range(0, this.m).mapToObj(i -> sm.makeStateInt(n)).toArray(StateInt[]::new);
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

    private void updateSupports(int lostPos) {
        if (nColsSup[xyz.get(lostPos).x].decrement() == 0)
            x.remove(xyz.get(lostPos).x);
        if (nRowsSup[xyz.get(lostPos).y].decrement() == 0)
            y.remove(xyz.get(lostPos).y);
    }

    @Override
    public void propagate() {
        int l = low.value(), u = up.value();
        int zMin = z.min(), zMax = z.max();

        while (xyz.get(l).z < zMin || !x.contains(xyz.get(l).x) || !y.contains(xyz.get(l).y)) {
            updateSupports(l++);
            if (l > u) throw new InconsistencyException();
        }
        while (xyz.get(u).z > zMax || !x.contains(xyz.get(u).x) || !y.contains(xyz.get(u).y)) {
            updateSupports(u--);
            if (l > u) throw new InconsistencyException();
        }
        z.removeBelow(xyz.get(l).z);
        z.removeAbove(xyz.get(u).z);
        low.setValue(l);
        up.setValue(u);
    }
}
