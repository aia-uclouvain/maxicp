/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util.exception;


public class InconsistencyException extends RuntimeException {

    public static final InconsistencyException INCONSISTENCY = new InconsistencyException();

    private static final long serialVersionUID = 1240061199250453776L;

    public String toString() {
        return "inconsistency";
    }


    /**
     * Forbid the JVM to produce a stack trace each time an InconsistencyException is thrown.
     *
     * Uncomment if you need stack traces. Be careful to comment it after debugging.
     */

    /*
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }*/
}
