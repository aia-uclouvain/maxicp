package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Stack;

/**
 *
 * Ensures that only one Hamiltonian circuit appears in the provided successor variables.
 * The nodes not belonging to the main circuit have themselves as successor.
 * @author pschaus
 */
public class SubCircuit extends AbstractCPConstraint {

    int n;
    private final CPIntVar[] x;

    private final StateInt[] dest;
    private final StateInt[] orig;
    private final StateInt nSubCircuits;

    /**
     * Creates a SubCircuit Constraint.
     * Nodes with x[i] == i are considered outside the active circuit.
     *
     * @param x the variables representing the successor array
     */
    public SubCircuit(CPIntVar[] x) {
        super(x[0].getSolver());
        assert (x.length > 0);
        this.x = x;
        this.n = x.length;

        dest = new StateInt[x.length];
        orig = new StateInt[x.length];

        for (int i = 0; i < x.length; i++) {
            dest[i] = getSolver().getStateManager().makeStateInt(i);
            orig[i] = getSolver().getStateManager().makeStateInt(i);
        }
        nSubCircuits = getSolver().getStateManager().makeStateInt(0);
    }

    @Override
    public void post() {
        for (CPIntVar var: x) {
            var.removeBelow(0);
            var.removeAbove(x.length - 1);
        }

        getSolver().post(new AllDifferentDC(x));

        for (int i = 0; i < x.length; i++) {
            if (!x[i].isFixed()) {
                int idx = i;
                x[idx].whenFixed(() -> fixed(idx));
            } else {
                fixed(i);
            }
        }
    }

    public void fixed(int u) {
        int v = x[u].min();

        // If the node points to itself, it's not part of the active subcircuit.
        if (u == v) {
            return;
        }

        // s ->* u -> v ->* d

        int s = orig[u].value();
        int d = dest[v].value();

        // Update the extremities of the path
        orig[d].setValue(s);
        dest[s].setValue(d);

        // Update the number of active sub-circuits
        if (d != v) {
            nSubCircuits.setValue(nSubCircuits.value() - 1);
        } else if (s == u) {
            nSubCircuits.setValue(nSubCircuits.value() + 1);
        }

        // We can only have one main sub-circuit, so if we would close this one now,
        // it means that there would be more than one sub-circuit, which is not allowed.
        if (nSubCircuits.value() > 1) {
            x[d].remove(s);
        }

        // Check if the circuit has closed, if so,
        // we force all remaining unassigned nodes to point to themselves.
        if (s == v) {
            close();
        }
    }

    /**
     * Closes the sub-circuit by forcing all remaining unassigned nodes
     * to point to themselves (self-loops).
     */
    private void close() {
        for (int i = 0; i < n; i++) {
            if (!x[i].isFixed()) {
                x[i].fix(i); // In MaxiCP, fix() will throw InconsistencyException if 'i' is removed
            }
        }
    }
}

