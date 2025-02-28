/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util.exception;


public class NotImplementedException extends UnsupportedOperationException {
    public NotImplementedException(String message) {
        super(message);
    }

    public NotImplementedException() {
        super();
    }

    public void print() {
        System.err.println(this + " " + (getStackTrace()[0].toString()));
        //printStackTrace();
    }


}
