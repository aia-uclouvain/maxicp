package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.state.StateInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static java.lang.Math.max;
import static org.maxicp.cp.CPFactory.ge;

public class MinMakespan extends AbstractCPConstraint {

    PrecedenceGraph precedenceGraph;
    CPIntVar makespan;
    CPIntervalVar[] onMachine;
    ThetaTree2 tree;
    Integer[] sortEst;
    Integer[] sortLct;
    public MinMakespan(PrecedenceGraph precedenceGraph, CPIntVar makespan, CPIntervalVar... activities){
        super(activities[0].getSolver());
        this.precedenceGraph = precedenceGraph;
        this.makespan = makespan;
        this.onMachine = activities;
        this.tree = new ThetaTree2(activities.length);
        this.sortEst = new Integer[activities.length];
        this.sortLct = new Integer[activities.length];
        for (int i = 0; i < activities.length; i++) {
            sortEst[i] = i;
            sortLct[i] = i;
        }
    }
    @Override
    public int priority() {
        return Constants.PIORITY_SLOW;
    }

    @Override
    public void post() {
        for (CPIntervalVar var : this.onMachine) {
            var.propagateOnChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        Arrays.sort(sortEst, Comparator.comparingInt(x ->onMachine[x].startMin()));
        Arrays.sort(sortLct, Comparator.comparingInt(x ->precedenceGraph.getQ(onMachine[x])));
        int maxValue = 0;
        for(int i = 0; i< this.onMachine.length;i++){
            CPIntervalVar mach = onMachine[i];
            for(int j = 0; j< this.onMachine.length; j++){
                if (sortEst[j]==i){
                    tree.insert(j,mach.startMin(), mach.lengthMin(), precedenceGraph.getQ(onMachine[i]));
                    break;
                }
            }
        }
        maxValue = max(maxValue, tree.getEct());
        for(int i = 0; i< this.onMachine.length;i++){
            for(int j = 0; j< this.onMachine.length; j++){
                if (Objects.equals(sortEst[j], sortLct[i])){
                    tree.remove(j);
                    maxValue = max(maxValue, tree.getEct());
                    break;
                }
            }
        }
        makespan.removeBelow(maxValue);
        tree.reset();
    }

}