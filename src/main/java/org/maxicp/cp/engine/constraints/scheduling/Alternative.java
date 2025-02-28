/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Alternative Constraint with cardinality:
 * Enforces that if the interval a is present, then c intervals from the array alt must be present and synchronized
 * with a. If a is not present, then all the intervals of alt must be absent.
 *
 * @author Charles Thomas
 */
public class Alternative extends AbstractCPConstraint {
    private final CPIntervalVar a;
    private final CPIntervalVar[] alts;
    private final CPIntVar c;

    private final int nAlts;

    public Alternative(CPIntervalVar a, CPIntervalVar[] alts, CPIntVar c) {
        super(a.getSolver());
        this.a = a;
        this.alts = alts;
        this.c = c;
        nAlts = alts.length;
    }

    @Override
    public void post() {
        a.propagateOnChange(this);
        c.propagateOnBoundChange(this);
        for(CPIntervalVar alt : alts) alt.propagateOnChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if(a.isAbsent()){
            //If a is absent: setting alts absent and deactivating constraint
            for(CPIntervalVar alt : alts) {
                alt.setAbsent();
            }
            setActive(false);
        } else {
            int minStartMinAlts = Constants.HORIZON;
            int maxStartMaxAlts = 0;
            int minEndMinAlts = Constants.HORIZON;
            int maxEndMaxAlts = 0;
            int minLengthMinAlts = Constants.HORIZON;
            int maxLengthMaxAlts = 0;
            int nPresent = 0;
            int nAbsent = 0;

            for(CPIntervalVar alt : alts) {
                if(alt.isAbsent()){
                    nAbsent ++;
                    //If there are not enough alternative intervals present for the min cardinality: setting a as absent
                    if(nAlts - nAbsent < c.min()){
                        a.setAbsent();
                        break;
                    } else c.removeAbove(nAlts - nAbsent);
                } else {
                    if(alt.isPresent()){
                        nPresent++;
                        c.removeBelow(nPresent);

                        //If the alternative is present, then the start min and end max of a can be constrained:
                        a.setStartMin(alt.startMin());
                        a.setStartMax(alt.startMax());
                        a.setEndMin(alt.endMin());
                        a.setEndMax(alt.endMax());
                        a.setLengthMin(alt.lengthMin());
                        a.setLengthMax(alt.lengthMax());
                    }

                    //Updating and checking start min:
                    alt.setStartMin(a.startMin());
                    minStartMinAlts = Math.min(alt.startMin(), minStartMinAlts);
                    //Updating and checking start max:
                    alt.setStartMax(a.startMax());
                    maxStartMaxAlts = Math.max(alt.startMax(), maxStartMaxAlts);
                    //Updating and checking end min:
                    alt.setEndMin(a.endMin());
                    minEndMinAlts = Math.min(alt.endMin(), minEndMinAlts);
                    //Updating and checking end max:
                    alt.setEndMax(a.endMax());
                    maxEndMaxAlts = Math.max(alt.endMax(), maxEndMaxAlts);
                    //Updating and checking length min:
                    alt.setLengthMin(a.lengthMin());
                    minLengthMinAlts = Math.min(alt.lengthMin(), minLengthMinAlts);
                    //Updating and checking length max:
                    alt.setLengthMax(a.lengthMax());
                    maxLengthMaxAlts = Math.max(alt.lengthMax(), maxLengthMaxAlts);
                }
            }

            //If no alternative is present: updating starts and ends based on max and min values:
            if(nPresent == 0){
                a.setStartMin(minStartMinAlts);
                a.setStartMax(maxStartMaxAlts);
                a.setEndMin(minEndMinAlts);
                a.setEndMax(maxEndMaxAlts);
                a.setLengthMin(minLengthMinAlts);
                a.setLengthMax(maxLengthMaxAlts);
            }

            //If at least one alternative activity is present, setting a present:
            if(nPresent > 0) a.setPresent();

            if(a.isPresent()){
                //If the number of undecided and present alternative activity is equal to the min cardinality:
                //Setting undecided acts as present:
                if(nAlts - nAbsent == c.min()){
                    for(CPIntervalVar alt : alts) if(alt.isOptional()) alt.setPresent();
                }

                //If the number of present alternative activities is equal to the max cardinality:
                //Setting undecided acts as absent:
                if(nPresent == c.max()){
                    for(CPIntervalVar alt : alts) if(alt.isOptional()) alt.setAbsent();
                }
            }
        }
    }
}
