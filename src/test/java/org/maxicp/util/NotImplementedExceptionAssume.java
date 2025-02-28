/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util;

import org.junit.jupiter.api.Assumptions;
import org.maxicp.util.exception.NotImplementedException;

public class NotImplementedExceptionAssume {
    public static void fail(NotImplementedException e) {
        Assumptions.abort(e.getMessage());
    }
}
