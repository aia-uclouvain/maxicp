/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;

public class AllDifferentFWC extends AtLeastNValueFWC {

    public AllDifferentFWC(CPIntVar... x) {
        super(x, CPFactory.makeIntVar(x[0].getSolver(), x.length, x.length));
    }

}