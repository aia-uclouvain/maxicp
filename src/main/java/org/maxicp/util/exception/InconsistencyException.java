/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util.exception;


public class InconsistencyException extends RuntimeException {

    private static final String STACK_TRACE_ENV = "MAXICP_INCONSISTENCY_STACKTRACE";

    private static final boolean STACK_TRACE_ENABLED =
            Boolean.parseBoolean(System.getenv(STACK_TRACE_ENV));
    private static final InconsistencyException INCONSISTENCY_WITHOUT_STACK_TRACE =
            new InconsistencyException(false);

    private InconsistencyException(boolean writableStackTrace) {
        super(null, null, false, writableStackTrace);
    }

    /**
     * Returns the shared exception by default, or a fresh exception with a stack trace when
     * {@code MAXICP_INCONSISTENCY_STACKTRACE=true}.
     */
    public static InconsistencyException INCONSISTENCY() {
        return STACK_TRACE_ENABLED
                ? new InconsistencyException(true)
                : INCONSISTENCY_WITHOUT_STACK_TRACE;
    }

    private static final long serialVersionUID = 1240061199250453776L;

    public String toString() {
        return "inconsistency";
    }
}
