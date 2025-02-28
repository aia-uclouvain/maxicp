package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.maxicp.cp.examples.modeling.QAP;
import org.maxicp.modeling.SymbolicBranching;
import org.maxicp.search.Searches;
import org.maxicp.search.SymbolicSearchMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QAPSearchTest {
    @Test
    @Disabled
    void dfsTest() {
        assertEquals(
                QAP.run((baseModel, x) -> baseModel.dfSearch(Searches.firstFail(x)), (baseModel, x) -> baseModel.dfSearch(Searches.firstFail(x))),
                QAP.run((baseModel, x) -> baseModel.concurrentDFSearch(SymbolicBranching.toSymbolicBranching(Searches.firstFail(x), baseModel)),
                        (baseModel, x) -> baseModel.concurrentDFSearch(SymbolicBranching.toSymbolicBranching(Searches.firstFail(x), baseModel)))
        );
    }
}
