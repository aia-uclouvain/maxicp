/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;




/**
 * Soft Global Cardinality Constraint
 * @author Pierre Schaus pschaus@gmail.com
 *
 * Algorithm based on the paper:
 * Schaus, P., Van Hentenryck, P., & Zanarini, A.
 * Revisiting the soft global cardinality constraint. In CPAIOR 2010
 */
public class SoftCardinalityDC extends AbstractCPConstraint{

    enum FlowType { UF, OF };

    private final int NONE = -Integer.MIN_VALUE;

    private boolean posted;

    private CPIntVar [] x;
    private int minval;
    private int maxval;
    private int nbVals;
    private int [] low;
    private int [] up;
    private CPIntVar viol;

    private int sumLow;

    // flow ("uf" refers to underflow and "of" to overflow)

    private int [] flow_uf;     //for each value, the quantity of flow into this value
    private int [] varMatch_uf; //for each variable, the value it is matched to
    private int [] valMatch_uf; //first variable matched to the value
    private int sizeFlow_uf; //total flow
    private int [] next_uf;     //next variable matched
    private int [] prev_uf;     //previous variable matched

    private int [] flow_of;     //for each value, the quantity of flow into this value
    private int [] varMatch_of; //for each variable, the value it is matched to
    private int [] valMatch_of; //first variable matched to the value
    private int sizeFlow_of; //total flow
    private int [] next_of;     //next variable matched
    private int [] prev_of;     //previous variable matched


    private int [] varSeen;  //flags for the dfs if the var nodes have been visited
    private int [] valSeen;  //flags for the dfs if the val nodes have been visited
    private int magic;    //magic used for the flag in _varSeen and _valSeen



    private int dfs;
    private int component;

    private int [] varComponent;
    private int [] varDfs;
    private int [] varHigh;

    private int [] valComponent;
    private int [] valDfs;
    private int [] valHigh;

    private int sinkComponent;
    private int sinkDfs;
    private int sinkHigh;

    private boolean [] isVarAlwaysMatched_uf;
    private boolean [] isVarAlwaysMatched_of;

    private int [] stack;
    private int [] type;
    private int top;


    /**
     * Constraint the values minval+i to appear between low[i] and up[i] times in x but accept some violations to this rule.
     * For the value vi = minval+i, let ci be the number of occurences in x and viol(vi) = max(0,low[i]-ci,ci-up[i]) i.e. viol(vi)
     * is the shortage or excess wrt the prescribed cardinalities. <br>
     * @param x
     * @param minval
     * @param low
     * @param up
     * @param viol = sum(i) viol(i)
     */
    public SoftCardinalityDC(CPIntVar[] x, int minval, int[] low, int[] up, CPIntVar viol) {
        super(x[0].getSolver());
        this.x = x;
        this.minval = minval;
        this.maxval = minval+low.length-1;
        nbVals = maxval-minval+1;
        this.low = low;
        this.up = up;
        this.viol = viol;
        check();
    }

    /**
     * Constraint the values i to appear between low[i] and up[i] times in x but accept some violations to this rule.
     * For the value let ci be the number of occurences of i in x and viol(i) = max(0,low[i]-ci,ci-up[i]) i.e. viol(vi)
     * is the shortage or excess wrt the prescribed cardinalities. <br>
     * @param x
     * @param low
     * @param up
     * @param viol = sum(i) viol(i)
     */
    public SoftCardinalityDC(CPIntVar[] x,  int[] low, int[] up, CPIntVar viol) {
        this(x, 0, low, up, viol);
    }


    private void check() throws RuntimeException{
        if(nbVals != low.length){
            throw new RuntimeException("vals and low must be of the same size");
        }
        if(nbVals != up.length){
            throw new RuntimeException("vals and up must be of the same size");
        }
        for (int i = 0; i < nbVals; i++) {
            if(low[i]<0){
                throw new RuntimeException("low vals must be >= 0");
            }
            if(up[i]<0){
                throw new RuntimeException("up vals must be >= 0");
            }
            if(low[i]>up[i]){
                throw new RuntimeException("low[i] must be <= up[i]");
            }
        }
    }


    @Override
    public void post() {
        posted = true;

        findValueRange();
        allocateFlow();
        findInitialFlow();

        int valViol = getValueViolation();
        viol.removeBelow(valViol);

        allocateSCC();

        propagate();

        for(int k = 0 ; k < x.length; k++){
            if (!x[k].isFixed())
                x[k].propagateOnDomainChange(this);
        }
        if (!viol.isFixed()) viol.propagateOnBoundChange(this);
    }

    @Override
    public void propagate() {
        for(int k = 0; k < x.length ; k++) {
            if (varMatch_uf[k] != NONE) {
                if (!x[k].contains(varMatch_uf[k])) {
                    unassign(k,FlowType.UF);
                }
            }
            if (varMatch_of[k] != NONE) {
                if (!x[k].contains(varMatch_of[k])) {
                    unassign(k,FlowType.OF);
                }
            }
        }

        int valViol = getValueViolation();

        //prune lower bound of violation
        viol.removeBelow(valViol);

        //prune variable domains (the constraint is consistent at this point)
        prune(valViol);

        //prune upper bound of violation if all variables are bound
        boolean allBound = true;
        for (int i = 0 ; i < x.length; i++) {
            if (!x[i].isFixed()){
                allBound = false;
                break;
            }
        }
        if (allBound)
            viol.removeAbove(valViol);
    }


    private void findValueRange() {

        int prev_minval = minval;

        for (int i = 0; i < x.length; i++) {
            minval = Math.min(minval,x[i].min());
            maxval = Math.max(maxval,x[i].max());
        }
        int d = prev_minval-minval;

        nbVals = maxval - minval + 1;

        // low
        int [] low_ = new int[nbVals];

        // up
        int [] up_ = new int[nbVals];
        for(int k = 0; k < nbVals; k++){
            up_[k] = x.length;
        }

        sumLow = 0;

        for (int i = 0 ; i < low.length; i++) {
            if (low[i] > 0){
                low_[i+d] = low[i] ;
                sumLow += low[i];
            }
        }

        for (int i = 0 ; i < up.length; i++) {
            if (up[i] < x.length){
                up_[i+d] = up[i] ;
            }
        }
        low = low_;
        up = up_;
    }

    public boolean hasValInBestAssignment(int i) {
        if(!posted || i < 0 || i >= x.length) return false;
        if(varMatch_of[i] == NONE)
            return false;
        else
            return true;
    }

    public int getValInBestAssignment(int i) {
        if (hasValInBestAssignment(i)) {
            return varMatch_of[i];
        }
        else if(i < x.length && i >= 0 && posted){
            return x[i].min();
        }
        return NONE;
    }

    public int getReducedCost(int i,int v){
        if(i >= x.length || i < 0 || !posted || !x[i].contains(v)){
            return Integer.MAX_VALUE;
        }

        findBestUnderFlow();
        findBestOverFlow();

        if(!hasValInBestAssignment(i) || varMatch_of[i]==v){
            return 0;
        }

        int reducedCost = 0;

        findSCC(FlowType.UF);
        computeIsVarAlwaysMatched(FlowType.UF);
        if(varComponent[i] != valComponent[v-minval] && (low[v-minval]>0 || isVarAlwaysMatched_uf[i])){
            reducedCost += 1;
        }

        findSCC(FlowType.OF);
        computeIsVarAlwaysMatched(FlowType.OF);
        if(varComponent[i] != valComponent[v-minval] && (up[v-minval]>0 || isVarAlwaysMatched_of[i])){
            reducedCost += 1;
        }

        return reducedCost;
    }


    private void allocateFlow() {
        // flow
        flow_uf = new int[nbVals];
        flow_of = new int[nbVals];

        // first variable matched
        valMatch_uf = new int [nbVals];
        valMatch_of = new int [nbVals];
        for(int k = 0; k < nbVals; k++){
            valMatch_uf[k] = NONE;  // unmatched
            valMatch_of[k] = NONE;  // unmatched
        }

        // next variable matched
        next_uf = new int[x.length];
        next_of = new int[x.length];
        for(int k = 0; k < x.length; k++){
            next_uf[k] = NONE;  // no next
            next_of[k] = NONE;  // no next
        }

        // previous variable matched
        prev_uf = new int[x.length];
        prev_of = new int[x.length];
        for(int k = 0; k < x.length; k++){
            prev_uf[k] = NONE;  // no prev
            prev_of[k] = NONE;  // no prev
        }

        // variable assignment
        varMatch_uf = new int [x.length];
        varMatch_of = new int [x.length];
        for(int k = 0 ; k < x.length; k++){
            varMatch_uf[k] = NONE; // unmatched
            varMatch_of[k] = NONE; // unmatched
        }

        // flag
        varSeen = new int[x.length];

        // flag
        valSeen = new int[nbVals];

        magic = 0;
    }

    //assigns value v to variable k and update structures: sizeFlow, flow, varMatch, prev, next, valMatch
    private void assign(int k,int v, FlowType ft){
        int [] flow;     //for each value, the quantity of flow into this value
        int [] varMatch; //for each variable, the value it is matched to
        int [] next;     //next variable matched
        int [] prev;     //previous variable matched
        int [] valMatch; //first variable matched to the value

        if(ft == FlowType.UF){
            flow = flow_uf;
            varMatch = varMatch_uf;
            next = next_uf;
            prev = prev_uf;
            valMatch = valMatch_uf;
            sizeFlow_uf++;
        }else{ //OF
            flow = flow_of;
            varMatch = varMatch_of;
            next = next_of;
            prev = prev_of;
            valMatch = valMatch_of;
            sizeFlow_of++;
        }

        unassign(k,ft);

        // k is now first on the list of v
        varMatch[k] = v;
        flow[v-minval]++;
        int nk = valMatch[v-minval];
        next[k] = nk;
        prev[k] = NONE;
        if (nk != NONE)
            prev[nk] = k;
        valMatch[v-minval] = k;
    }

    //unassings variable k and updates appropriately the structures: sizeFlow, flow, varMatch, prev, next, valMatch
    private void unassign(int k, FlowType ft){
        int [] flow;     //for each value, the quantity of flow into this value
        int [] varMatch; //for each variable, the value it is matched to
        int [] next;     //next variable matched
        int [] prev;     //previous variable matched
        int [] valMatch; //first variable matched to the value

        if(ft == FlowType.UF){
            flow = flow_uf;
            varMatch = varMatch_uf;
            next = next_uf;
            prev = prev_uf;
            valMatch = valMatch_uf;
        }else{ // OF
            flow = flow_of;
            varMatch = varMatch_of;
            next = next_of;
            prev = prev_of;
            valMatch = valMatch_of;
        }

        if (varMatch[k] != NONE) { // this guy is assigned; must be removed
            if(ft == FlowType.UF) sizeFlow_uf--;
            else sizeFlow_of--;

            int w = varMatch[k];
            flow[w-minval]--;
            if (valMatch[w-minval] == k) { // first in the list
                int nk = next[k];
                valMatch[w-minval] = nk;
                if (nk != NONE)
                    prev[nk] = NONE; // nk is now first
            }
            else { // not first
                int pk = prev[k];
                int nk = next[k];
                next[pk] = nk;
                if (nk != NONE)
                    prev[nk] = pk;
            }
            varMatch[k] = NONE;
        }
    }

    //finds a initial flow for both the underflow and overflow
    private void findInitialFlow() {
        sizeFlow_uf = 0;
        sizeFlow_of = 0;
        for(int k = 0; k < x.length; k++) {
            int mx = x[k].min();
            int Mx = x[k].max();
            for(int i = mx; i <= Mx; i++){
                if (flow_uf[i-minval] < low[i-minval])
                    if (x[k].contains(i)) {
                        assign(k,i,FlowType.UF);
                        break;
                    }
            }
            for(int i = mx; i <= Mx; i++){
                if (flow_of[i-minval] < up[i-minval])
                    if (x[k].contains(i)) {
                        assign(k,i,FlowType.OF);
                        break;
                    }
            }
        }
    }

    private int getValueViolation(){
        int buf = findBestUnderFlow();
        int bof = findBestOverFlow();
        return buf+bof;
    }

    //computes and returns the best under flow
    private int findBestUnderFlow(){
        for(int k = 0; k < x.length && sizeFlow_uf < x.length; k++) {
            if (varMatch_uf[k] == NONE) {
                magic++;
                findAugmentingPath(k,FlowType.UF);
            }
        }
        return sumLow - sizeFlow_uf;
    }

    //computes and returns the best over flow
    private int findBestOverFlow(){
        //in order to have the best overflow AND underflow, I start from the
        //best under flow to computeLowerBound the best overflow (very important for the methods hasValInBestAssignment/getValInBestAssignment)

        for(int i = minval; i <= maxval; i++){
            flow_of[i-minval] = flow_uf[i-minval];
            valMatch_of[i-minval] = valMatch_uf[i-minval];
        }
        for(int k = 0; k < x.length ; k++) {
            varMatch_of[k] = varMatch_uf[k];
            next_of[k] = next_uf[k];
            prev_of[k] = prev_uf[k];
        }
        sizeFlow_of = sizeFlow_uf;

        for(int k = 0; k < x.length && sizeFlow_of < x.length; k++) {
            if (varMatch_of[k] == NONE) {
                magic++;
                findAugmentingPath(k,FlowType.OF);
            }
        }
        return x.length - sizeFlow_of;
    }

    //finds augmenting path from variable i which is currently not matched (true if found a path, false otherwise)
    private boolean findAugmentingPath(int k, FlowType ft){
        int []  varMatch; //for each variable, the value it is matched to
        if(ft == FlowType.UF) 	varMatch = varMatch_uf;
        else 					varMatch = varMatch_of;

        if (varSeen[k] != magic) {
            varSeen[k] = magic;
            int mx = x[k].min();
            int Mx = x[k].max();
            for(int v = mx; v <= Mx; v++) {
                if (varMatch[k] != v) {
                    if (x[k].contains(v)) {
                        if (findAugmentingPathValue(v,ft)) {
                            assign(k,v,ft);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean findAugmentingPathValue(int v, FlowType ft){

        int [] flow;     //for each value, the quantity of flow into this value
        int [] next;     //next variable matched
        int [] valMatch; //first variable matched to the value
        int [] capa;     //capacity (low for ft==UF, up for ft==OF)

        if(ft == FlowType.UF){
            flow = flow_uf;
            next = next_uf;
            valMatch = valMatch_uf;
            capa = low;
        }else{ //OF
            flow = flow_of;
            next = next_of;
            valMatch = valMatch_of;
            capa = up;
        }

        if (valSeen[v-minval] != magic) {
            valSeen[v-minval] = magic;
            if (flow[v-minval] < capa[v-minval])
                return true;
            else if (flow[v-minval] > 0) {
                int i = valMatch[v-minval];
                while (i != NONE) {
                    if (findAugmentingPath(i,ft))
                        return true;
                    i = next[i];
                }
            }
        }
        return false;
    }

    private void computeIsVarAlwaysMatched(FlowType ft){
        boolean [] isVarAlwaysMatched;
        int [] varMatch;

        if(ft == FlowType.UF){
            isVarAlwaysMatched = isVarAlwaysMatched_uf;
            varMatch = varMatch_uf;
        }else{
            isVarAlwaysMatched = isVarAlwaysMatched_of;
            varMatch = varMatch_of;
        }


        int [] nbVarInComponent = new int[component+1];
        for(int k = 0; k < x.length; k++){
            if(varMatch[k] == NONE ){
                nbVarInComponent[varComponent[k]]++;
            }
        }
        for(int k = 0; k < x.length; k++){
            isVarAlwaysMatched[k] = false;
            if(varMatch[k] != NONE && nbVarInComponent[varComponent[k]] == 0){
                isVarAlwaysMatched[k] = true;
            }
        }
    }

    private void allocateSCC(){
        varComponent = new int[x.length];
        varDfs = new int[x.length];
        varHigh = new int [x.length];

        valComponent = new int [nbVals];
        valDfs = new int[nbVals];
        valHigh = new int[nbVals];

        stack        = new int [x.length+nbVals+1];
        type         = new int [x.length+nbVals+1];

        isVarAlwaysMatched_uf  = new boolean[x.length];
        isVarAlwaysMatched_of  = new boolean[x.length];

    }

    private void initSCC(){
        for(int k = 0 ; k < x.length; k++) {
            varComponent[k] = 0;
            varDfs[k] = 0;
            varHigh[k] = 0;
        }
        for(int k = 0 ; k < nbVals; k++) {
            valComponent[k] = 0;
            valDfs[k] = 0;
            valHigh[k] = 0;
        }

        sinkComponent = 0;
        sinkDfs = 0;
        sinkHigh = 0;

        top = 0;
        dfs = x.length + nbVals + 1;
        component = 0;
    }

    private void findSCC(FlowType ft){
        initSCC();
        for(int k = 0; k < x.length; k++) {
            if (varDfs[k] == 0)
                findSCCvar(k,ft);
        }
    }

    private void findSCCvar(int k, FlowType ft){
        int [] varMatch = (ft == FlowType.UF) ? varMatch_uf : varMatch_of; //first variable matched to the value

        varDfs[k] = dfs--;
        varHigh[k] = varDfs[k];
        stack[top] = k;
        type[top] = 0;
        top++;
        assert(top <= x.length + nbVals + 1);


        int mx = x[k].min();
        int Mx = x[k].max();
        for(int w = mx; w <= Mx; w++) {
            //go to every values of the domain of x that is not matched by x
            if (varMatch[k] != w) {
                if (x[k].contains(w)) {
                    if (valDfs[w-minval] == 0) {
                        findSCCval(w,ft);
                        if (valHigh[w-minval] > varHigh[k])
                            varHigh[k] = valHigh[w-minval];
                    }
                    else if ( (valDfs[w-minval] > varDfs[k]) && (valComponent[w-minval] == 0)) {
                        if (valDfs[w-minval] > varHigh[k])
                            varHigh[k] = valDfs[w-minval];
                    }
                }
            }
        }

        //if x is matched go also to every other variables that are not matched (path from x->source->x')

        if(varMatch[k] != NONE){
            for(int i = 0; i < x.length; i++) {
                if(varMatch[i] == NONE){
                    if (varDfs[i] == 0) {
                        findSCCvar(i,ft);
                        if (varHigh[i] > varHigh[k])
                            varHigh[k] = varHigh[i];
                    }
                    else if ( (varDfs[i] > varDfs[k]) && (varComponent[i] == 0)) {
                        if (varDfs[i] > varHigh[k])
                            varHigh[k] = varDfs[i];
                    }
                }
            }
        }

        if (varHigh[k] == varDfs[k]) {
            component++;
            do {
                assert(top > 0);
                int i = stack[--top];
                int t = type[top];
                if (t == 0)
                    varComponent[i] = component;
                else if (t == 1)
                    valComponent[i-minval] = component;
                else
                    sinkComponent = component;
                if (t == 0 && i == k)
                    break;
            } while (true);
        }


    }

    private void findSCCval(int v, FlowType ft){
        int []  valMatch = (ft == FlowType.UF) ? valMatch_uf : valMatch_of; //first variable matched to the value
        int []  capa     = (ft == FlowType.UF) ? low : up; //first variable matched to the value
        int []  next     = (ft == FlowType.UF) ? next_uf : next_of; //first variable matched to the value
        int []  flow     = (ft == FlowType.UF) ? flow_uf : flow_of; //first variable matched to the value

        valDfs[v-minval] = dfs--;
        valHigh[v-minval] = valDfs[v-minval];
        stack[top] = v;
        type[top] = 1;
        top++;
        assert(top <= x.length + nbVals + 1);

        // first go to the variables assigned to this value
        int k = valMatch[v-minval];
        while (k != NONE) {
            if (varDfs[k] == 0) {
                findSCCvar(k,ft);
                if (varHigh[k] > valHigh[v-minval])
                    valHigh[v-minval] = varHigh[k];
            }
            else if ( (varDfs[k] > valDfs[v-minval]) && (varComponent[k] == 0)) {
                if (varDfs[k] > valHigh[v-minval])
                    valHigh[v-minval] = varDfs[k];
            }
            k = next[k];
        }

        // next try to see if you can go to the sink

        if (flow[v-minval] < capa[v-minval]) {
            // go to the sink
            if (sinkDfs == 0) {
                findSCCsink(ft);
                if (sinkHigh > valHigh[v-minval])
                    valHigh[v-minval] = sinkHigh;
            }
            else if ( (sinkDfs > valDfs[v-minval]) && (sinkComponent == 0)) {
                if (sinkDfs > valHigh[v-minval])
                    valHigh[v-minval] = sinkDfs;
            }
        }

        if (valHigh[v-minval] == valDfs[v-minval]) {
            component++;
            do {
                assert(top > 0);
                int i = stack[--top];
                int t = type[top];
                if (t == 0)
                    varComponent[i] = component;
                else if (t == 1)
                    valComponent[i-minval] = component;
                else
                    sinkComponent = component;
                if (t == 1 && i == v)
                    break;
            } while (true);
        }

    }

    private void findSCCsink(FlowType ft){
        int []  varMatch = (ft == FlowType.UF) ? varMatch_uf : varMatch_of; //first variable matched to the value
        int []  flow     = (ft == FlowType.UF) ? flow_uf : flow_of; //first variable matched to the value

        sinkDfs  = dfs--;
        sinkHigh = sinkDfs;
        stack[top] = NONE;
        type[top] = 2;
        top++;
        assert(top <= x.length + nbVals + 1);

        //from the sink, I can go to the values if the flow in the value is larger than the demand in these value

        for(int i = 0; i < x.length; i++) {
            int w = varMatch[i];
            if(varMatch[i] != NONE){
                if (flow[w-minval] > 0) { //there is no minimum capacity
                    if (valDfs[w-minval] == 0) {
                        findSCCval(w,ft);
                        if (valHigh[w-minval] > sinkHigh)
                            sinkHigh = valHigh[w-minval];
                    }
                    else if ( (valDfs[w-minval] > sinkDfs) && (valComponent[w-minval] == 0)) {
                        if (valDfs[w-minval] > sinkHigh)
                            sinkHigh = valDfs[w-minval];
                    }
                }
            }
        }

        //from the sink we can also go the variables that are not matched

        for(int i = 0; i < x.length; i++) {
            if(varMatch[i] == NONE){
                if (varDfs[i] == 0) {
                    findSCCvar(i,ft);
                    if (varHigh[i] > sinkHigh)
                        sinkHigh = varHigh[i];
                }
                else if ( (varDfs[i] > sinkDfs) && (varComponent[i] == 0)) {
                    if (varDfs[i] > sinkHigh)
                        sinkHigh = varDfs[i];
                }
            }
        }

        if (sinkHigh == sinkDfs) {
            component++;
            do {
                assert(top > 0);
                int i = stack[--top];
                int t = type[top];
                if (t == 0)
                    varComponent[i] = component;
                else if (t == 1)
                    valComponent[i-minval] = component;
                else
                    sinkComponent = component;
                if (t == 2)
                    break;
            } while (true);
        }

    }

    private void prune(int valViol){
        if(valViol < viol.max()-1){
            return; //the constraint is GAC
        }

        //we computeLowerBound the SCC in Gu and Go and also if a variable is matched in every maximum matching in Gu and Go

        //source of inefficiency to create the table (memory)?
        int  [] varComponent_uf = new int[x.length];
        int  [] valComponent_uf = new int[nbVals];

        findSCC(FlowType.UF);
        computeIsVarAlwaysMatched(FlowType.UF);
        for(int v = 0; v<nbVals; v++) {
            valComponent_uf[v] = valComponent[v];
        }
        for(int k = 0; k < x.length; k++) {
            varComponent_uf[k] = varComponent[k];
        }

        findSCC(FlowType.OF);
        computeIsVarAlwaysMatched(FlowType.OF);

        if(valViol == viol.max()-1) {
            for(int k = 0; k < x.length; k++) {
                if( varMatch_of[k] == NONE) continue;//all values of this variable are consistent
                int mx = x[k].min();
                int Mx = x[k].max();
                for(int w = mx; w <= Mx; w++) {
                    if(x[k].contains(w)){
                        if (varMatch_uf[k] != w && varMatch_of[k] != w) {
                            if ((varComponent_uf[k] != valComponent_uf[w-minval] && (low[w-minval]>0 || isVarAlwaysMatched_uf[k])) && (varComponent[k] != valComponent[w-minval] && (up[w-minval]>0 || isVarAlwaysMatched_of[k])) ) {
                                x[k].remove(w);
                            }
                        }
                    }
                }
            }
        }
        else if(valViol == viol.max()){
            //under-flow filtering
            for(int k = 0; k < x.length; k++) {
                if( varMatch_of[k] == NONE) continue;//all values of this variable are consistent
                int mx = x[k].min();
                int Mx = x[k].max();
                for(int w = mx; w <= Mx; w++) {
                    if(x[k].contains(w)){
                        if (varMatch_uf[k] != w && varMatch_of[k] != w) {
                            if (varComponent_uf[k] != valComponent_uf[w-minval] && (low[w-minval]>0 || isVarAlwaysMatched_uf[k])) {
                                x[k].remove(w);
                            }
                        }
                    }
                }
            }
            //over-flow filtering
            //under-flow filtering
            for(int k = 0; k < x.length; k++) {
                if( varMatch_of[k] == NONE) continue;//all values of this variable are consistent
                int mx = x[k].min();
                int Mx = x[k].max();
                for(int w = mx; w <= Mx; w++) {
                    if(x[k].contains(w)){
                        if (varMatch_of[k] != w) {
                            if (varComponent[k] != valComponent[w-minval] && (up[w-minval]>0 || isVarAlwaysMatched_of[k])) {
                                x[k].remove(w);
                            }
                        }
                    }
                }
            }
        }
    }

}

