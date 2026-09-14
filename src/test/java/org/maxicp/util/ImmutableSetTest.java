/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 */

package org.maxicp.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ImmutableSetTest {

    @Test
    public void anArrayWithRepeatedElementsCollapsesInsteadOfThrowing() {
        ImmutableSet<String> s = ImmutableSet.of("a", "b", "a");
        assertEquals(2, s.size());
        assertTrue(s.contains("a"));
        assertTrue(s.contains("b"));
    }

    @Test
    public void anArrayWithoutRepetitionKeepsEveryElement() {
        assertEquals(3, ImmutableSet.of("a", "b", "c").size());
    }

    @Test
    public void aCollectionWithRepeatedElementsAlsoCollapses() {
        assertEquals(2, ImmutableSet.of(List.of("a", "b", "a")).size());
    }

    @Test
    public void anEmptyArrayGivesAnEmptySet() {
        assertTrue(ImmutableSet.of(new String[0]).isEmpty());
    }

    @Test
    public void equalsMatchesAnEquivalentSet() {
        assertEquals(Set.of("a", "b"), ImmutableSet.of("a", "b", "a"));
    }

    @Test
    public void theSetIsImmutable() {
        ImmutableSet<String> s = ImmutableSet.of("a", "b");
        assertThrows(UnsupportedOperationException.class, () -> s.add("c"));
    }
}
