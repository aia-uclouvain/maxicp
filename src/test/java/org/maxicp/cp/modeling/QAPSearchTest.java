package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.maxicp.cp.examples.modeling.QAP;
import org.maxicp.modeling.SymbolicBranching;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QAPSearchTest {
    @Test
    @Disabled
    void dfsTest() {
        assertEquals(
                QAP.run((baseModel, x) -> baseModel.dfSearch(Searches.firstFailBinary(x)), (baseModel, x) -> baseModel.dfSearch(Searches.firstFailBinary(x))),
                QAP.run((baseModel, x) -> baseModel.concurrentDFSearch(SymbolicBranching.toSymbolicBranching(Searches.firstFailBinary(x), baseModel)),
                        (baseModel, x) -> baseModel.concurrentDFSearch(SymbolicBranching.toSymbolicBranching(Searches.firstFailBinary(x), baseModel)))
        );
    }
}
