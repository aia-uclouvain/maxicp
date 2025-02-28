/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util.exception;


public class IntOverFlowException extends RuntimeException {

    public IntOverFlowException(String message) {
        super(message);
    }

    public String toString() {
        return "possible overflow on integer "+ super.toString();
    }


}
