/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.algebra.Expression;

public interface IntervalExpression extends Expression {

    int startMin();

    int startMax();

    int endMin();

    int endMax();

    int lengthMin();

    int lengthMax();

    boolean isPresent();

    boolean isAbsent();

    boolean isOptional();

    /**
     * Gives the current domain of the interval var as a human-readable String.
     * If the var is fixed, a single number is returned.
     * Otherwise, if the domain can be represented as an interval, it is represented by "{min..max}".
     * Otherwise, all values are enumerated in brackets, without any guarantee on the value ordering (i.e. "{v2, v0, v1}")
     *
     * @return representation of the current domain, in human-readable format.
     */
    default String show() {
        StringBuilder builder = new StringBuilder();
        if (isPresent()) {
            builder.append("present ");
        } else if (isAbsent()) {
            builder.append("absent ");
        } else {
            builder.append("optional ");
        }
        if (startMin() == startMax()) {
            builder.append("start = " + startMin() + " ");
        } else {
            builder.append("start = [" + startMin() + "," + startMax() + "] ");
        }
        if (lengthMin() == lengthMax()) {
            builder.append("length = " + lengthMin() + " ");
        } else {
            builder.append("length = [" + lengthMin() + "," + lengthMax() + "] ");
        }
        if (endMin() == endMax()) {
            builder.append("end = " + endMin());
        } else {
            builder.append("end = [" + endMin() + "," + endMax() + "]");
        }
        return builder.toString();
    }

}
