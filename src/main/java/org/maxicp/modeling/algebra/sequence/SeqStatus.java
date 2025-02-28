/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.algebra.sequence;

/**
 * Status of the nodes
 */
public enum SeqStatus {
    REQUIRED,
    MEMBER,
    POSSIBLE,
    EXCLUDED,
    NOT_EXCLUDED,           // equivalent to REQUIRED U POSSIBLE

    // useful for branching
    INSERTABLE,             // equivalent to (REQUIRED \ MEMBER) U POSSIBLE
    INSERTABLE_REQUIRED,    // equivalent to (REQUIRED \ MEMBER)

    // ordering within the sequence
    MEMBER_ORDERED,         // equivalent to MEMBER, sorted by order in the sequence
}
