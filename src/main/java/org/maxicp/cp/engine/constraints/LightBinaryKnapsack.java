/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;

import java.util.Arrays;

public class LightBinaryKnapsack extends AbstractCPConstraint {

    private final CPBoolVar[] items;
    private final int[] weights;
    private final CPIntVar load;
    private final int nItems;
    private final int[] unassigned;
    private final StateInt nUnassignedRev;
    private final StateInt requiredLoadRev;
    private final StateInt possibleLoadRev;
    private int nUnassigned;
    private int requiredLoad;
    private int possibleLoad;

    public LightBinaryKnapsack(CPBoolVar[] items, int[] weights, CPIntVar load) {
        super(items[0].getSolver());

        this.items = items;
        this.weights = weights;
        this.load = load;

        this.nItems = items.length;
        this.unassigned = new int[nItems];
        for (int i = 0; i < nItems; i++) {
            unassigned[i] = i;
        }

        this.nUnassignedRev = getSolver().getStateManager().makeStateInt(nItems);
        this.requiredLoadRev = getSolver().getStateManager().makeStateInt(0);
        this.possibleLoadRev = getSolver().getStateManager().makeStateInt(0);
    }

    @Override
    public void post() {
        init();
        for (CPBoolVar item : items) {
            item.propagateOnDomainChange(this);
        }
        load.propagateOnDomainChange(this);
    }

    private void init() {
        // Reset structures
        nUnassigned = nItems;
        requiredLoad = 0;
        possibleLoad = 0;

        // Compute loads
        for (int i = 0; i < nItems; i++) {
            int itemId = unassigned[i];
            CPBoolVar item = items[itemId];
            if (!item.isFixed()) {
                possibleLoad += weights[i];
            } else {
                // Remove from set
                nUnassigned--;
                unassigned[i] = unassigned[nUnassigned];
                unassigned[nUnassigned] = itemId;

                // Update loads
                if (item.isTrue()) {
                    int weight = weights[itemId];
                    requiredLoad += weight;
                    possibleLoad += weight;
                }
            }
        }
        filterItems();
        nUnassignedRev.setValue(nUnassigned);
        requiredLoadRev.setValue(requiredLoad);
        possibleLoadRev.setValue(possibleLoad);
    }
    @Override
    public void propagate() {
        // Cache
        nUnassigned = nUnassignedRev.value();
        requiredLoad = requiredLoadRev.value();
        possibleLoad = possibleLoadRev.value();

        // Filtering
        updateAssignedItems();
        filterItems();

        nUnassignedRev.setValue(nUnassigned);
        requiredLoadRev.setValue(requiredLoad);
        possibleLoadRev.setValue(possibleLoad);

    }

    private void updateAssignedItems() {
        for (int i = nUnassigned - 1; i >= 0; i--) {
            int itemId = unassigned[i];
            CPBoolVar item = items[itemId];
            if (item.isFixed()) {
                // Remove from set
                nUnassigned--;
                unassigned[i] = unassigned[nUnassigned];
                unassigned[nUnassigned] = itemId;

                // Update loads
                if (item.isTrue()) {
                    requiredLoad += weights[itemId];
                } else {
                    possibleLoad -= weights[itemId];
                }
            }
        }
    }

    private void filterItems() {
        boolean fixed = false;
        while (!fixed) { // Inner fixed point
            fixed = true;
            load.removeAbove(possibleLoad);
            load.removeBelow(requiredLoad);
            int maxWeight = load.max() - requiredLoad;
            int minWeight = possibleLoad - load.min();

            for (int i = nUnassigned - 1; i >= 0; i--) {
                int itemId = unassigned[i];
                CPBoolVar item = items[itemId];
                int weight = weights[itemId];

                if (weight > maxWeight) {
                    item.fix(false);
                    // Remove from set
                    nUnassigned--;
                    unassigned[i] = unassigned[nUnassigned];
                    unassigned[nUnassigned] = itemId;

                    // Update loads
                    possibleLoad -= weight;
                    fixed = false;

                } else if (minWeight < weight) {
                    item.fix(true);

                    // Remove from set
                    nUnassigned--;
                    unassigned[i] = unassigned[nUnassigned];
                    unassigned[nUnassigned] = itemId;

                    // Update loads
                    requiredLoad += weight;
                    fixed = false;
                }
            }
        }
    }
}
