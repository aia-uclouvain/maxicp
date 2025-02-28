package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.Expression;

import java.util.Collection;
import java.util.List;

public class Cumulative implements Constraint {

    public final SeqVar seqVar;
    public final int[] starts;
    public final int[] ends;
    public final int[] load;
    public final int capacity;

    /**
     * Links some nodes with start and end of activities, with a corresponding load.
     * Given a capacity for the sequence, ensures that the capacity is never exceeded by the visits of activities
     * @param seqVar route where visits of nodes are considered as activities
     * @param starts start of each activity
     * @param ends corresponding end of each activity
     * @param load corresponding load of each activity
     * @param capacity capacity allowed for the sequence
     */
    public Cumulative(SeqVar seqVar, int[] starts, int[] ends, int[] load, int capacity) {
        assert starts.length == ends.length && starts.length == load.length;
        this.seqVar = seqVar;
        this.starts = starts;
        this.ends = ends;
        this.load = load;
        this.capacity = capacity;
    }

    @Override
    public Collection<? extends Expression> scope() {
        return List.of(seqVar);
    }
}
