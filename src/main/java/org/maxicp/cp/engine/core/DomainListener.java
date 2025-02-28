/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.core;

/**
 * Domain listeners are passed as argument
 * to the {@link IntDomain} modifier methods.
 */
public interface DomainListener {

    /**
     * Called whenever the domain becomes empty.
     */
    void empty();

    /**
     * Called whenever the domain becomes a single value.
     */
    void bind();

    /**
     * Called whenever the domain loses a value.
     */
    void change();

    /**
     * Called whenever the maximum value of the domain is lost.
     */
    void changeMin();

    /**
     * Called whenever the minmum value of the domain is lost.
     */
    void changeMax();
}
