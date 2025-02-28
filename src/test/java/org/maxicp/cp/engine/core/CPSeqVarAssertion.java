/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;


import org.opentest4j.AssertionFailedError;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class CPSeqVarAssertion {

    /**
     * Gives the expected predecessors of a node, given its insertions
     * @param seqVar sequence of nodes
     * @param node node for which the predecessors must be computed
     * @param insertions all insertions for the nodes
     * @param members nodes visited in the sequence, in order
     * @param memberSet nodes that are member
     * @param requiredSet nodes that are required
     * @param possibleSet nodes that are possible
     * @param excludedSet nodes that are excluded
     * @return array representing all predecessors of the sequence
     */
    private static int[] buildExpectedPreds(CPSeqVar seqVar, int node, int[][] insertions, int[] members, Set<Integer> memberSet, Set<Integer> requiredSet, Set<Integer> possibleSet, Set<Integer> excludedSet) {
        if (memberSet.contains(node)) {
            // find the predecessor
            int predecessor = -1;
            for (int i = 0 ; i < members.length ; i++) {
                if (members[i] == node) {
                    if (i > 0) {
                        predecessor = members[i-1];
                    }
                    break;
                }
            }
            if (predecessor == -1) {
                assertEquals(seqVar.start(), node);
                return new int[0];
            }  else {
                List<Integer> preds = new ArrayList<>();
                for (int i = 0; i < insertions.length; i++) {
                    for (int candidate : insertions[i]) {
                        if (candidate == predecessor) {
                            preds.add(i);
                        }
                    }
                }
                preds.add(predecessor);
                return preds.stream().mapToInt(i -> i).toArray();
            }
        } else if (excludedSet.contains(node)) {
            return new int[0];
        } else {
            // insertable node
            int nPreds = possibleSet.size() + requiredSet.size() - memberSet.size() + insertions[node].length - 1;
            int[] preds = new int[nPreds];
            int i = 0 ;
            // links to all possible nodes
            for (int pred: possibleSet) {
                if (pred != node)
                    preds[i++] = pred;
            }
            // links to all required nodes that are not member
            for (int pred: requiredSet) {
                if (pred != node && !memberSet.contains(pred))
                    preds[i++] = pred;
            }
            // link depending on the insertions
            for (int pred: insertions[node]) {
                preds[i++] = pred;
            }
            assert i == nPreds;
            return preds;
        }
    }

    /**
     * Gives the expected predecessors of a node, given its insertions
     * @param seqVar sequence of nodes
     * @param node node for which the predecessors must be computed
     * @param insertions all insertions for the nodes
     * @param members nodes visited in the sequence, in order
     * @param memberSet nodes that are member
     * @param requiredSet nodes that are required
     * @param possibleSet nodes that are possible
     * @param excludedSet nodes that are excluded
     * @return array representing all predecessors of the sequence
     */
    private static int[] buildExpectedSuccs(CPSeqVar seqVar, int node, int[][] insertions, int[] members, Set<Integer> memberSet, Set<Integer> requiredSet, Set<Integer> possibleSet, Set<Integer> excludedSet) {
        if (memberSet.contains(node)) {
            // find the predecessor
            int successor = -1;
            for (int i = 0 ; i < members.length ; i++) {
                if (members[i] == node) {
                    if (i < members.length - 1) {
                        successor = members[i+1];
                    }
                    break;
                }
            }
            if (successor == -1) {
                assertEquals(seqVar.end(), node);
                return new int[0];
            }  else {
                List<Integer> succs = new ArrayList<>();
                for (int i = 0; i < insertions.length; i++) {
                    for (int candidate : insertions[i]) {
                        if (candidate == node) {
                            succs.add(i);
                        }
                    }
                }
                succs.add(successor);
                return succs.stream().mapToInt(i -> i).toArray();
            }
        } else if (excludedSet.contains(node)) {
            return new int[0];
        } else {
            // insertable node
            int nSuccs = possibleSet.size() + requiredSet.size() - memberSet.size() + insertions[node].length - 1;
            int[] succs = new int[nSuccs];
            int i = 0 ;
            // links to all possible nodes
            for (int succ: possibleSet) {
                if (succ != node)
                    succs[i++] = succ;
            }
            // links to all required nodes that are not member
            for (int succ: requiredSet) {
                if (!memberSet.contains(succ) && succ != node)
                    succs[i++] = succ;
            }
            // link depending on the insertions
            Map<Integer, Integer> successors = new HashMap<>();
            for (int j = 0 ; j < members.length-1 ; j++) {
                successors.put(members[j], members[j+1]);
            }
            for (int pred: insertions[node]) {
                succs[i++] = successors.get(pred);
            }
            assert i == nSuccs;
            return succs;
        }
    }


    /**
     * Assert values related to the predecessors of a node
     *
     * @param seqVar      the sequence variable under test
     * @param memberSet   supposed member nodes of the sequence
     * @param requiredSet supposed required nodes of the sequence
     * @param possibleSet supposed possible nodes of the sequence
     * @param node        node that must be tested
     * @param preds  supposed insertions of the node
     * @param buffer      array used by fill operations for the test. Must be large enough for the fill,
     *                    its initial content is irrelevant and will be overwritten
     */
    private static void assertPred(CPSeqVar seqVar, Set<Integer> memberSet, Set<Integer> requiredSet, Set<Integer> possibleSet, Set<Integer> excludedSet, int node, int[] preds, int[] insertions, int[] buffer) {
        // TODO check values for member nodes and excluded nodes
        int nInserts = insertions.length;;
        Set<Integer> insertionSet;
        int nPredInsertable;
        int nPred;
        int nPredPossible;
        int nPredRequired;
        int nPredMember;
        int nPredInsertableRequired;
        if (memberSet.contains(node) || excludedSet.contains(node)) {
            insertionSet = new HashSet();
            if (memberSet.contains(node) && node != seqVar.start()) {
                nPredInsertable = (int) Arrays.stream(preds).filter(p -> possibleSet.contains(p) || (!memberSet.contains(p) && requiredSet.contains(p))).count();
                // if the node is a member node different from the start, it has one member predecessor (the node preceding it)
                nPredPossible = (int) Arrays.stream(preds).filter(possibleSet::contains).count();
                nPredRequired = (int) Arrays.stream(preds).filter(requiredSet::contains).count();
                nPred = preds.length;
                nPredMember = 1;
                nPredInsertableRequired = (int) Arrays.stream(preds).filter(p -> !memberSet.contains(p) && requiredSet.contains(p)).count();
            } else {
                assertEquals(0, preds.length);
                nPredInsertable = 0;
                nPredPossible = 0;
                nPredRequired = 0;
                nPred = 0;
                nPredMember = 0;
                nPredInsertableRequired = 0;
            }
        } else {
            insertionSet = Arrays.stream(insertions).boxed().collect(Collectors.toSet());
            nPredPossible = possibleSet.size() - (possibleSet.contains(node) ? 1 : 0);
            nPredRequired = requiredSet.size() - memberSet.size() + insertionSet.size() - (requiredSet.contains(node) ? 1 : 0);
            nPredInsertable = possibleSet.size() + (requiredSet.size() - memberSet.size()) - 1; // not counting itself
            nPred = preds.length;
            nPredMember = nInserts;
            nPredInsertableRequired = (int) Arrays.stream(preds).filter(p -> !memberSet.contains(p) && requiredSet.contains(p)).count();
        }

        int[] actualSeq;
        int[] actualNode;
        int[] expectedPredecessors;

        // test nPred methods
        assertEquals(nPred, seqVar.nPred(node), "Wrong number of predecessors for node " + node);
        assertEquals(nPred, seqVar.getNodeVar(node).nPred(), "Wrong number of predecessors for node " + node);

        // test nInsert methods
        assertEquals(nInserts, seqVar.nInsert(node), "Wrong number of insertions for node " + node);
        assertEquals(nInserts, seqVar.getNodeVar(node).nInsert(), "Wrong number of insertions for node " + node);

        // test fillPred over all the predecessors
        assertEquals(nPred, seqVar.fillPred(node, buffer, NOT_EXCLUDED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPred);
        Arrays.sort(actualSeq, 0, nPred);

        assertEquals(nPred, seqVar.getNodeVar(node).fillPred(buffer, NOT_EXCLUDED));
        actualNode = Arrays.copyOfRange(buffer, 0, nPred);
        Arrays.sort(actualNode, 0, nPred);
        Arrays.sort(preds, 0, nPred);

        assertArrayEquals(preds, actualSeq);
        assertArrayEquals(preds, actualNode);

        for (int i = 0; i < nPred; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x) || possibleSet.contains(x));
            assertFalse(excludedSet.contains(x));
        }

        // test fillPred over all the possible predecessors
        assertEquals(nPredPossible, seqVar.fillPred(node, buffer, POSSIBLE));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredPossible);
        Arrays.sort(actualSeq, 0, nPredPossible);
        assertEquals(nPredPossible, seqVar.getNodeVar(node).fillPred(buffer, POSSIBLE));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredPossible);
        Arrays.sort(actualNode, 0, nPredPossible);
        expectedPredecessors = Arrays.stream(preds)
                .filter(possibleSet::contains).toArray();
        Arrays.sort(expectedPredecessors, 0, nPredPossible);
        assertArrayEquals(expectedPredecessors, actualSeq);
        assertArrayEquals(expectedPredecessors, actualNode);
        for (int i = 0; i < nPredPossible; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(possibleSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
        }

        // test fillPred over all the required predecessors
        assertEquals(nPredRequired, seqVar.fillPred(node, buffer, REQUIRED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredRequired);
        Arrays.sort(actualSeq, 0, nPredRequired);
        assertEquals(nPredRequired, seqVar.getNodeVar(node).fillPred(buffer, REQUIRED));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredRequired);
        Arrays.sort(actualNode, 0, nPredRequired);
        expectedPredecessors = Arrays.stream(preds)
                .filter(requiredSet::contains) // either not member, or member but is an insertion
                .toArray();
        Arrays.sort(expectedPredecessors, 0, nPredRequired);
        assertArrayEquals(expectedPredecessors, actualSeq);
        assertArrayEquals(expectedPredecessors, actualNode);
        for (int i = 0; i < nPredRequired; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
        }

        //Testing fillPred over all the member predecessors
        assertEquals(nPredMember, seqVar.fillPred(node, buffer, MEMBER));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredMember);
        Arrays.sort(actualSeq, 0, nPredMember);
        assertEquals(nPredMember, seqVar.getNodeVar(node).fillPred(buffer, MEMBER));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredMember);
        Arrays.sort(actualNode, 0, nPredMember);
        expectedPredecessors = Arrays.stream(preds)
                .filter(memberSet::contains)
                .toArray();
        Arrays.sort(expectedPredecessors, 0, nPredMember);
        assertArrayEquals(expectedPredecessors, actualSeq);
        assertArrayEquals(expectedPredecessors, actualNode);
        for (int i = 0; i < nPredMember; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, MEMBER));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(memberSet.contains(x));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
            if (!memberSet.contains(node)) {
                int after = seqVar.memberAfter(x);
                assertTrue(seqVar.hasEdge(node, after));
                assertTrue(seqVar.hasInsert(x, node));
            }
        }

        // test fillPred over all the member predecessors ordered by appearance in the sequence
        assertEquals(nPredMember, seqVar.fillPred(node, buffer, MEMBER_ORDERED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredMember);
        assertEquals(nPredMember, seqVar.getNodeVar(node).fillPred(buffer, MEMBER_ORDERED));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredMember);
        expectedPredecessors = Arrays.stream(preds)
                .filter(memberSet::contains)
                .toArray();
        int[] expectedOrdered = new int[nPredMember];
        int current = seqVar.end();
        int j = nPredMember - 1;
        if (memberSet.contains(node) && node != seqVar.start()) {
            assertEquals(1, nPredMember);
            expectedOrdered[0] = seqVar.memberBefore(node);
        } else {
            while (j >= 0) {
                if (insertionSet.contains(current)) {
                    expectedOrdered[j] = current;
                    j--;
                }
                current = seqVar.memberBefore(current);
            }
        }
        assertArrayEquals(expectedOrdered, actualSeq);
        assertArrayEquals(expectedOrdered, actualNode);
        Arrays.sort(expectedPredecessors, 0, nInserts);
        Arrays.sort(expectedOrdered, 0, nInserts);
        assertArrayEquals(expectedPredecessors, expectedOrdered);
        for (int i = 0; i < nInserts; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, MEMBER));
            assertTrue(seqVar.isNode(x, MEMBER_ORDERED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(memberSet.contains(x));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
            if (!memberSet.contains(node)) {
                int after = seqVar.memberAfter(x);
                assertTrue(seqVar.hasEdge(node, after));
                assertTrue(seqVar.hasInsert(x, node));
            }
        }

        // test fillPred over all the non-member predecessors
        assertEquals(nPredInsertable, seqVar.fillPred(node, buffer, INSERTABLE));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredInsertable);
        Arrays.sort(actualSeq, 0, nPredInsertable);
        assertEquals(nPredInsertable, seqVar.getNodeVar(node).fillPred(buffer, INSERTABLE));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredInsertable);
        Arrays.sort(actualNode, 0, nPredInsertable);
        expectedPredecessors = Arrays.stream(preds)
                .filter(x -> possibleSet.contains(x) || (requiredSet.contains(x) && !memberSet.contains(x)))
                .toArray();
        Arrays.sort(expectedPredecessors, 0, nPredInsertable);
        assertArrayEquals(expectedPredecessors, actualSeq);
        assertArrayEquals(expectedPredecessors, actualNode);
        for (int i = 0; i < nPredInsertable; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, MEMBER));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(possibleSet.contains(x) || requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
            assertFalse(excludedSet.contains(x));
        }

        // test fillPred over all the required non-member predecessors
        assertEquals(nPredInsertableRequired, seqVar.fillPred(node, buffer, INSERTABLE_REQUIRED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nPredInsertableRequired);
        Arrays.sort(actualSeq, 0, nPredInsertableRequired);
        assertEquals(nPredInsertableRequired, seqVar.getNodeVar(node).fillPred(buffer, INSERTABLE_REQUIRED));
        actualNode = Arrays.copyOfRange(buffer, 0, nPredInsertableRequired);
        Arrays.sort(actualNode, 0, nPredInsertableRequired);
        expectedPredecessors = Arrays.stream(preds)
                .filter(x -> requiredSet.contains(x) && !memberSet.contains(x))
                .toArray();
        Arrays.sort(expectedPredecessors, 0, nPredInsertableRequired);
        assertArrayEquals(expectedPredecessors, actualSeq);
        assertArrayEquals(expectedPredecessors, actualNode);
        for (int i = 0; i < nPredInsertableRequired; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(x, node));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, INSERTABLE));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertTrue(seqVar.isNode(x, INSERTABLE_REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, MEMBER));
            assertFalse(seqVar.isNode(x, MEMBER_ORDERED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
        }

        // test fillPred over all the excluded nodes (should be empty)
        Arrays.fill(buffer, Integer.MAX_VALUE);
        expectedPredecessors = Arrays.copyOf(buffer, buffer.length);
        assertEquals(0, seqVar.fillPred(node, buffer, EXCLUDED));
        assertArrayEquals(expectedPredecessors, buffer);
        assertEquals(0, seqVar.getNodeVar(node).fillPred(buffer, EXCLUDED));
        assertArrayEquals(expectedPredecessors, buffer);
    }

    /**
     * Assert values related to the successors of a node
     *
     * @param seqVar      the sequence variable under test
     * @param memberSet   supposed member nodes of the sequence
     * @param requiredSet supposed required nodes of the sequence
     * @param possibleSet supposed possible nodes of the sequence
     * @param node        node that must be tested
     * @param insertions  supposed insertions of the node
     * @param buffer      array used by fill operations for the test. Must be large enough for the fill,
     *                    its initial content is irrelevant and will be overwritten
     */
    private static void assertSucc(CPSeqVar seqVar, Set<Integer> memberSet, Set<Integer> requiredSet, Set<Integer> possibleSet, Set<Integer> excludedSet, int node, int[] succs, int[] insertions, int[] buffer) {
        // TODO check values for member and excluded nodes

        /*
        int nInsert = insertions.length;
        Set<Integer> insertionSet = Arrays.stream(insertions).boxed().collect(Collectors.toSet());
        int nSuccInsertable = possibleSet.size() + (requiredSet.size() - memberSet.size()) - 1; // not counting itself
        int nSucc = insertions.length + nSuccInsertable;

         */
        int nInserts = insertions.length;
        Set<Integer> insertionSet;
        int nSuccInsertable;
        int nSucc;
        int nSuccPossible;
        int nSuccRequired;
        int nSuccMember;
        int nSuccInsertableRequired;
        if (memberSet.contains(node) || excludedSet.contains(node)) {
            insertionSet = new HashSet();
            if (memberSet.contains(node) && node != seqVar.end()) {
                nSuccInsertable = (int) Arrays.stream(succs).filter(s -> possibleSet.contains(s) || (!memberSet.contains(s) && requiredSet.contains(s))).count();
                // if the node is a member node different from the start, it has one member predecessor (the node preceding it)
                nSuccPossible = (int) Arrays.stream(succs).filter(possibleSet::contains).count();
                nSuccRequired = (int) Arrays.stream(succs).filter(requiredSet::contains).count();
                nSucc = nSuccInsertable + 1;
                nSuccMember = 1;
                nSuccInsertableRequired = (int) Arrays.stream(succs).filter(s -> requiredSet.contains(s) && !memberSet.contains(s)).count();
            } else {
                assertEquals(0, succs.length);
                nSuccInsertable = 0;
                nSuccPossible = 0;
                nSuccRequired = 0;
                nSucc = 0;
                nSuccMember = 0;
                nSuccInsertableRequired = 0;
            }
        } else {
            insertionSet = Arrays.stream(insertions).boxed().collect(Collectors.toSet());
            nSuccPossible = possibleSet.size() - (possibleSet.contains(node) ? 1 : 0);
            nSuccRequired = (int) Arrays.stream(succs).filter(requiredSet::contains).count();
            nSuccInsertable = possibleSet.size() + (requiredSet.size() - memberSet.size()) - 1; // not counting itself
            nSucc = succs.length;
            nSuccMember = nInserts;
            nSuccInsertableRequired = (int) Arrays.stream(succs).filter(s -> requiredSet.contains(s) && !memberSet.contains(s)).count();
        }

        int[] actualSeq;
        int[] actualNode;
        int[] expectedSuccessors;

        // test nSucc methods
        assertEquals(nSucc, seqVar.nSucc(node), "Wrong number of succs for node " + node);
        assertEquals(nSucc, seqVar.getNodeVar(node).nSucc());

        // test fillSucc over all the successors
        assertEquals(nSucc, seqVar.fillSucc(node, buffer, NOT_EXCLUDED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSucc);
        Arrays.sort(actualSeq, 0, nSucc);

        assertEquals(nSucc, seqVar.getNodeVar(node).fillSucc(buffer, NOT_EXCLUDED));
        actualNode = Arrays.copyOfRange(buffer, 0, nSucc);
        Arrays.sort(actualNode, 0, nSucc);
        Arrays.sort(succs, 0, nSucc);

        assertArrayEquals(succs, actualSeq);
        assertArrayEquals(succs, actualNode);
        for (int i = 0; i < nSucc; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x) || possibleSet.contains(x));
            assertFalse(excludedSet.contains(x));
        }

        // test fillSucc over all the possible successors
        assertEquals(nSuccPossible, seqVar.fillSucc(node, buffer, POSSIBLE));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccPossible);
        Arrays.sort(actualSeq, 0, nSuccPossible);
        assertEquals(nSuccPossible, seqVar.getNodeVar(node).fillSucc(buffer, POSSIBLE));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccPossible);
        Arrays.sort(actualNode, 0, nSuccPossible);
        expectedSuccessors = Arrays.stream(succs)
                .filter(possibleSet::contains).toArray();
        Arrays.sort(expectedSuccessors, 0, nSuccPossible);
        assertArrayEquals(expectedSuccessors, actualSeq);
        assertArrayEquals(expectedSuccessors, actualNode);
        for (int i = 0; i < nSuccPossible; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(possibleSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
        }

        // test fillSucc over all the required successors
        assertEquals(nSuccRequired, seqVar.fillSucc(node, buffer, REQUIRED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccRequired);
        Arrays.sort(actualSeq, 0, nSuccRequired);
        assertEquals(nSuccRequired, seqVar.getNodeVar(node).fillSucc(buffer, REQUIRED));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccRequired);
        Arrays.sort(actualNode, 0, nSuccRequired);
        expectedSuccessors = Arrays.stream(succs)
                .filter(requiredSet::contains) // either not member, or member but is an insertion
                .toArray();
        Arrays.sort(expectedSuccessors, 0, nSuccRequired);
        assertArrayEquals(expectedSuccessors, actualSeq);
        assertArrayEquals(expectedSuccessors, actualNode);
        for (int i = 0; i < nSuccRequired; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
        }

        // test fillSucc over all the member successors
        assertEquals(nSuccMember, seqVar.fillSucc(node, buffer, MEMBER));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccMember);
        Arrays.sort(actualSeq, 0, nSuccMember);
        assertEquals(nSuccMember, seqVar.getNodeVar(node).fillSucc(buffer, MEMBER));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccMember);
        Arrays.sort(actualNode, 0, nSuccMember);
        expectedSuccessors = Arrays.stream(succs)
                .filter(memberSet::contains)
                .toArray();
        Arrays.sort(expectedSuccessors, 0, nSuccMember);
        assertArrayEquals(expectedSuccessors, actualSeq);
        assertArrayEquals(expectedSuccessors, actualNode);
        for (int i = 0; i < nSuccMember; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, MEMBER));
            assertTrue(seqVar.isNode(x, MEMBER_ORDERED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(memberSet.contains(x));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
            if (!memberSet.contains(node)) {
                int before = seqVar.memberBefore(x);
                assertTrue(seqVar.hasEdge(before, node));
                assertTrue(seqVar.hasInsert(before, node));
            }
        }

        // test fillSucc over all the member successors ordered by appearance in the sequence
        assertEquals(nSuccMember, seqVar.fillSucc(node, buffer, MEMBER_ORDERED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccMember);
        assertEquals(nSuccMember, seqVar.getNodeVar(node).fillSucc(buffer, MEMBER_ORDERED));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccMember);
        expectedSuccessors = Arrays.stream(succs)
                .filter(memberSet::contains)
                .toArray();
        int[] expectedOrdered = new int[nSuccMember];
        int current = seqVar.start();
        int j = 0;
        if (memberSet.contains(node) && node != seqVar.end()) {
            assertEquals(1, nSuccMember);
            expectedOrdered[0] = seqVar.memberAfter(node);
        } else {
            while (j < nSuccMember) {
                if (insertionSet.contains(current)) {
                    expectedOrdered[j] = seqVar.memberAfter(current);
                    j++;
                }
                current = seqVar.memberAfter(current);
            }
        }
        assertArrayEquals(expectedOrdered, actualSeq);
        assertArrayEquals(expectedOrdered, actualNode);
        Arrays.sort(expectedSuccessors, 0, nSuccMember);
        Arrays.sort(expectedOrdered, 0, nSuccMember);
        assertArrayEquals(expectedSuccessors, expectedOrdered);
        for (int i = 0; i < nSuccMember; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, MEMBER));
            assertTrue(seqVar.isNode(x, MEMBER_ORDERED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(memberSet.contains(x));
            assertTrue(requiredSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
            if (!memberSet.contains(node)) {
                int before = seqVar.memberBefore(x);
                assertTrue(seqVar.hasEdge(before, node));
                assertTrue(seqVar.hasInsert(before, node));
            }
        }

        // test fillSucc over all the non-member successors
        assertEquals(nSuccInsertable, seqVar.fillSucc(node, buffer, INSERTABLE));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccInsertable);
        Arrays.sort(actualSeq, 0, nSuccInsertable);
        assertEquals(nSuccInsertable, seqVar.getNodeVar(node).fillSucc(buffer, INSERTABLE));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccInsertable);
        Arrays.sort(actualNode, 0, nSuccInsertable);
        expectedSuccessors = Arrays.stream(succs)
                .filter(x -> possibleSet.contains(x) || (requiredSet.contains(x) && !memberSet.contains(x)))
                .toArray();
        Arrays.sort(expectedSuccessors, 0, nSuccInsertable);
        assertArrayEquals(expectedSuccessors, actualSeq);
        assertArrayEquals(expectedSuccessors, actualNode);
        for (int i = 0; i < nSuccInsertable; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, INSERTABLE));
            assertFalse(seqVar.isNode(x, MEMBER));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(possibleSet.contains(x) || requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
            assertFalse(excludedSet.contains(x));
        }

        // test fillSucc over all the required non-member successors
        assertEquals(nSuccInsertableRequired, seqVar.fillSucc(node, buffer, INSERTABLE_REQUIRED));
        actualSeq = Arrays.copyOfRange(buffer, 0, nSuccInsertableRequired);
        Arrays.sort(actualSeq, 0, nSuccInsertableRequired);
        assertEquals(nSuccInsertableRequired, seqVar.getNodeVar(node).fillSucc(buffer, INSERTABLE_REQUIRED));
        actualNode = Arrays.copyOfRange(buffer, 0, nSuccInsertableRequired);
        Arrays.sort(actualNode, 0, nSuccInsertableRequired);
        expectedSuccessors = Arrays.stream(succs)
                .filter(x -> requiredSet.contains(x) && !memberSet.contains(x))
                .toArray();
        Arrays.sort(expectedSuccessors, 0, nSuccInsertableRequired);
        assertArrayEquals(expectedSuccessors, actualSeq);
        assertArrayEquals(expectedSuccessors, actualNode);
        for (int i = 0; i < nSuccInsertableRequired; ++i) {
            int x = actualSeq[i];
            assertTrue(seqVar.hasEdge(node, x));
            assertTrue(seqVar.isNode(x, NOT_EXCLUDED));
            assertTrue(seqVar.isNode(x, INSERTABLE));
            assertTrue(seqVar.isNode(x, INSERTABLE_REQUIRED));
            assertTrue(seqVar.isNode(x, REQUIRED));
            assertFalse(seqVar.isNode(x, POSSIBLE));
            assertFalse(seqVar.isNode(x, MEMBER));
            assertFalse(seqVar.isNode(x, MEMBER_ORDERED));
            assertFalse(seqVar.isNode(x, EXCLUDED));
            assertTrue(requiredSet.contains(x));
            assertFalse(memberSet.contains(x));
            assertFalse(excludedSet.contains(x));
            assertFalse(possibleSet.contains(x));
        }

        // test fillSucc over all the excluded nodes (should be empty)
        Arrays.fill(buffer, Integer.MAX_VALUE);
        expectedSuccessors = Arrays.copyOf(buffer, buffer.length);
        assertEquals(0, seqVar.fillSucc(node, buffer, EXCLUDED));
        assertArrayEquals(expectedSuccessors, buffer);
        assertEquals(0, seqVar.getNodeVar(node).fillSucc(buffer, EXCLUDED));
        assertArrayEquals(expectedSuccessors, buffer);
    }

    /**
     * Ensures that the values given as input to the test are somewhat valid
     *
     * @param seqVar     the sequence under test
     * @param member     supposed member nodes of the sequence. Member nodes must be part of the required nodes
     * @param required   supposed required nodes of the sequence
     * @param possible   supposed possible nodes of the sequence
     * @param excluded   supposed excluded nodes of the sequence
     * @param insertions supposed predecessors for each node within the sequence
     */
    private static void validateArgs(CPSeqVar seqVar, int[] member, int[] required, int[] possible, int[] excluded, int[][] insertions) {
        try {
            //Checking for duplicates
            Set<Integer> memberSet = Arrays.stream(member).boxed().collect(toSet());
            assertEquals(member.length, memberSet.size(), "Duplicate node within the member set given as input");
            Set<Integer> requiredSet = Arrays.stream(required).boxed().collect(toSet());
            assertEquals(required.length, requiredSet.size(), "Duplicate node within the required set given as input");
            Set<Integer> possibleSet = Arrays.stream(possible).boxed().collect(toSet());
            assertEquals(possible.length, possibleSet.size(), "Duplicate node within the possible set given as input");
            Set<Integer> excludedSet = Arrays.stream(excluded).boxed().collect(toSet());
            assertEquals(excluded.length, excludedSet.size(), "Duplicate node within the excluded set given as input");

            //Start node and end node must be present in member and required sets
            assertTrue(memberSet.contains(seqVar.start()));
            assertTrue(memberSet.contains(seqVar.end()));
            assertTrue(requiredSet.contains(seqVar.start()));
            assertTrue(requiredSet.contains(seqVar.end()));

            //All members nodes are also required nodes
            for (int i : member) {
                assertTrue(requiredSet.contains(i), "A member node given as input is not marked as required");
            }
            Map<Integer, Integer> visit = new HashMap<>();
            for (int i = 0; i < member.length; ++i) {
                visit.put(member[i], i);
            }

            //Required, Possible and Excluded must be a partition of all the nodes
            Set<Integer> intersection = new HashSet<>(requiredSet);
            intersection.retainAll(possibleSet);
            assertTrue(intersection.isEmpty(), "The required and possible sets given as input are not disjoint");
            intersection.addAll(requiredSet);
            intersection.retainAll(excludedSet);
            assertTrue(intersection.isEmpty(), "The required and excluded sets given as input are not disjoint");
            intersection.addAll(possibleSet);
            intersection.retainAll(excludedSet);
            assertTrue(intersection.isEmpty(), "The possible and excluded sets given as input are not disjoint");
            int nNode = seqVar.nNode();
            assertEquals(nNode, requiredSet.size() + possibleSet.size() + excludedSet.size(), "The required, possible and excluded nodes given as input do not cover all nodes");

            //Check the insertions, they cannot include the node being tested and cannot include an excluded node
            //If the node being tested is a member node, it must contain its preceding member in its predecessors
            assertEquals(nNode, insertions.length);
            for (int node = 0; node < nNode; ++node) {
                if (excludedSet.contains(node)) {
                    assertEquals(0, insertions[node].length, String.format("The excluded node %d given as input has some insertions", node));
                } else if (memberSet.contains(node)) {
                    assertEquals(0, insertions[node].length, String.format("The member node %d given as input has some insertions", node));
                } else {
                    if (node != seqVar.start())
                        assertNotEquals(0, insertions[node].length, String.format("The unexcluded node %d has no insertions", node));
                    if (node != seqVar.end())
                        assertTrue(insertions[node].length < member.length, "The number of insertions given as input is always < number of member nodes");
                    for (int x : insertions[node]) {
                        assertTrue(x >= 0, "The insertions must be valid nodes for the sequence");
                        assertTrue(x < nNode, "The insertions must be valid nodes for the sequence");
                        assertNotEquals(node, x, String.format("The insertions of the node %d given as input include the node itself", x));
                        if (node != seqVar.start())
                            assertNotEquals(seqVar.end(), x, String.format("The insertions of the node %d given as input include the end node", x));
                        assertFalse(excludedSet.contains(x), "The insertions given as input cannot include an excluded node");
                        assertTrue(memberSet.contains(x), "The pred insertions given as input must only contain member nodes");
                    }
                }
            }
        } catch (AssertionFailedError e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void assertEdgeAppearsTwice(CPSeqVar seqVar) {
        int nNodes = seqVar.nNode();
        Set<Integer>[] preds = new Set[nNodes];
        Set<Integer>[] succs = new Set[nNodes];
        int[] edges = new int[nNodes];
        for (int node = 0; node < nNodes; node++) {
            preds[node] = new HashSet();
            succs[node] = new HashSet();
            int nPreds = seqVar.fillPred(node, edges);
            for (int i = 0; i < nPreds; i++) {
                int pred = edges[i];
                preds[node].add(pred);
            }
            int nSuccs = seqVar.fillSucc(node, edges);
            for (int i = 0; i < nSuccs; i++) {
                int succ = edges[i];
                succs[node].add(succ);
            }
        }
        for (int node = 0; node < nNodes; node++) {
            for (int pred : preds[node]) {
                assertTrue(succs[pred].contains(node), "An edge is stored twice: one for each of its endpoints");
            }
            for (int succ : succs[node]) {
                assertTrue(preds[succ].contains(node), "An edge is stored twice: one for each of its endpoints");
            }
        }
    }

    /**
     * Assert the full state of a sequence
     * Test quite a bunch of stuff and is quite expensive. This is rather safe but expensive if testing simple operations
     *
     * @param seqVar     sequence variable
     * @param member     member nodes, given in order of appearance within the sequence (member[0] is always the start and member[member.length-1] the end)
     * @param required   required nodes. Contains the member nodes as well
     * @param possible   possible nodes
     * @param excluded   excluded nodes
     * @param insertions predecessors of each node.
     */
    public static void assertSeqVar(CPSeqVar seqVar, int[] member, int[] required, int[] possible, int[] excluded, int[][] insertions) {
        //Check that the arguments are somewhat valid
        validateArgs(seqVar, member, required, possible, excluded, insertions);
        assertEdgeAppearsTwice(seqVar);

        Set<Integer> memberSet = Arrays.stream(member).boxed().collect(toSet());
        Set<Integer> requiredSet = Arrays.stream(required).boxed().collect(toSet());
        Set<Integer> possibleSet = Arrays.stream(possible).boxed().collect(toSet());
        Set<Integer> excludedSet = Arrays.stream(excluded).boxed().collect(toSet());
        Map<Integer, Integer> nodeToPosition = IntStream.range(0, member.length).boxed().collect(Collectors.toMap(i -> member[i], i -> i));

        int nNode = possible.length + required.length + excluded.length;
        int nNotExcluded = possible.length + required.length;
        int nInsertable = possible.length + (required.length - member.length);
        int nInsertableRequired = required.length - member.length;

        int[] notExcluded = new int[nNotExcluded];
        int[] insertable = new int[nInsertable];
        int[] insertableRequired = new int[nInsertableRequired];

        System.arraycopy(possible, 0, notExcluded, 0, possible.length);
        System.arraycopy(required, 0, notExcluded, possible.length, required.length);
        System.arraycopy(possible, 0, insertable, 0, possible.length);
        int j = 0;
        for (int x : required) {
            if (!memberSet.contains(x)) {
                insertable[j + possible.length] = x;
                insertableRequired[j] = x;
                j++;
            }
        }

        int[] actual;
        int[] expected;
        int[] values = new int[nNode];

        //Check count operations:
        assertEquals(nNode, seqVar.nNode());
        assertEquals(member.length, seqVar.nNode(MEMBER));
        assertEquals(member.length, seqVar.nNode(MEMBER_ORDERED));
        assertEquals(required.length, seqVar.nNode(REQUIRED));
        assertEquals(possible.length, seqVar.nNode(POSSIBLE));
        assertEquals(excluded.length, seqVar.nNode(EXCLUDED));
        assertEquals(nNotExcluded, seqVar.nNode(NOT_EXCLUDED));
        assertEquals(nInsertable, seqVar.nNode(INSERTABLE));
        assertEquals(nInsertableRequired, seqVar.nNode(INSERTABLE_REQUIRED));

        //Check fill operations

        record fillTest(int[] array, Function<int[], Integer> f) {
        }
        for (fillTest test : new fillTest[]{new fillTest(member.clone(), (int[] buffer) -> seqVar.fillNode(buffer, MEMBER)), //MEMBER_ORDERED is tested later
                new fillTest(required.clone(), (int[] buffer) -> seqVar.fillNode(buffer, REQUIRED)), new fillTest(possible.clone(), (int[] buffer) -> seqVar.fillNode(buffer, POSSIBLE)), new fillTest(excluded.clone(), (int[] buffer) -> seqVar.fillNode(buffer, EXCLUDED)), new fillTest(notExcluded.clone(), (int[] buffer) -> seqVar.fillNode(buffer, NOT_EXCLUDED)), new fillTest(insertable.clone(), (int[] buffer) -> seqVar.fillNode(buffer, INSERTABLE)), new fillTest(insertableRequired.clone(), (int[] buffer) -> seqVar.fillNode(buffer, INSERTABLE_REQUIRED))}) {
            assertEquals(test.array.length, (int) test.f.apply(values));
            actual = Arrays.copyOfRange(values, 0, test.array.length);
            Arrays.sort(actual);
            expected = test.array;
            Arrays.sort(expected);
            assertArrayEquals(expected, actual);
        }

        //Test nodes
        int[] buffer = new int[nNode];
        for (int i = 0; i < nNode; ++i) {
            // test operation over the status of a node
            if (seqVar.isNode(i, EXCLUDED)) {
                assertFalse(seqVar.isNode(i, NOT_EXCLUDED));
                assertFalse(seqVar.isNode(i, MEMBER));
                assertFalse(seqVar.isNode(i, MEMBER_ORDERED));
                assertFalse(seqVar.isNode(i, REQUIRED));
                assertFalse(seqVar.isNode(i, INSERTABLE));
                assertFalse(seqVar.isNode(i, INSERTABLE_REQUIRED));
                assertFalse(seqVar.isNode(i, POSSIBLE));
                assertTrue(excludedSet.contains(i));
                assertFalse(possibleSet.contains(i));
                assertFalse(requiredSet.contains(i));
                assertFalse(memberSet.contains(i));
                assertEquals(0, seqVar.nInsert(i));
                assertEquals(0, seqVar.fillInsert(i, values));
            } else {
                assertTrue(seqVar.isNode(i, NOT_EXCLUDED));
                assertFalse(excludedSet.contains(i));
                if (seqVar.isNode(i, MEMBER)) {
                    assertTrue(seqVar.isNode(i, MEMBER_ORDERED));
                    assertTrue(seqVar.isNode(i, REQUIRED));
                    assertFalse(seqVar.isNode(i, INSERTABLE));
                    assertFalse(seqVar.isNode(i, INSERTABLE_REQUIRED));
                    assertFalse(seqVar.isNode(i, POSSIBLE));
                    assertTrue(memberSet.contains(i));
                    assertTrue(requiredSet.contains(i));
                    assertFalse(possibleSet.contains(i));
                    assertEquals(0, seqVar.nInsert(i));
                    assertEquals(0, seqVar.fillInsert(i, values));
                } else {
                    assertTrue(seqVar.isNode(i, INSERTABLE));
                    assertFalse(seqVar.isNode(i, MEMBER_ORDERED));
                    assertFalse(memberSet.contains(i));
                    if (seqVar.isNode(i, INSERTABLE_REQUIRED)) {
                        assertTrue(seqVar.isNode(i, REQUIRED));
                        assertFalse(seqVar.isNode(i, POSSIBLE));
                        assertTrue(requiredSet.contains(i));
                        assertFalse(possibleSet.contains(i));
                    } else {
                        assertTrue(seqVar.isNode(i, POSSIBLE));
                        assertFalse(seqVar.isNode(i, REQUIRED));
                        assertTrue(possibleSet.contains(i));
                        assertFalse(requiredSet.contains(i));
                    }
                    // test the insertion counter and fill insert of the insertable nodes
                    int nInsertExpected = insertions[i].length;
                    assertEquals(seqVar.fillInsert(i, values), seqVar.nInsert(i), "Wrong value for the counter of insertions");
                    assertEquals(nInsertExpected, seqVar.nInsert(i), "Wrong value for the counter of insertions");
                    assertTrue(nInsertExpected >= 1, "An insertable node has at least one insertion");
                    assertTrue(nInsertExpected < member.length, "An insertable node has at most as many insertions as there are member nodes, minus 1");
                    for (int p = 0; p < nInsertExpected; p++) {
                        int pred = values[p];
                        assertTrue(memberSet.contains(pred), "Only member nodes can be insertions");
                        assertTrue(seqVar.hasInsert(pred, i), "A fill insert operation must only give nodes for which an insertion exists");
                    }
                }
            }

            //Test predecessors and successors
            int[] preds = buildExpectedPreds(seqVar, i, insertions, member, memberSet, requiredSet, possibleSet, excludedSet);
            assertPred(seqVar, memberSet, requiredSet, possibleSet, excludedSet, i, preds, insertions[i], buffer);
            int[] succs = buildExpectedSuccs(seqVar, i, insertions, member, memberSet, requiredSet, possibleSet, excludedSet);
            assertSucc(seqVar, memberSet, requiredSet, possibleSet, excludedSet, i, succs, insertions[i], buffer);
        }

        // test the ordering
        int[] ordering = new int[member.length];
        assertEquals(member.length, seqVar.fillNode(ordering, MEMBER_ORDERED));
        for (int i = 0; i < ordering.length; ++i) {
            assertEquals(member[i], ordering[i]);
        }

        // Test member nodes
        int pred = seqVar.end();
        for (int x : member) {
            // a member node has no insertion
            assertEquals(0, seqVar.nInsert(x));
            assertEquals(0, seqVar.fillInsert(x, buffer));

            // test memberBefore and memberAfter
            if (x != seqVar.start()) {
                assertEquals(x, seqVar.memberAfter(pred));
                assertEquals(pred, seqVar.memberBefore(x));
            }
            pred = x;
        }

        //Test excluded nodes
        Arrays.fill(buffer, Integer.MAX_VALUE);
        expected = Arrays.copyOf(buffer, buffer.length);
        for (int i : excluded) {
            assertEquals(0, seqVar.nPred(i));

            assertEquals(0, seqVar.nSucc(i));

            assertEquals(0, seqVar.fillPred(i, buffer, NOT_EXCLUDED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, EXCLUDED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, POSSIBLE));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, REQUIRED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, MEMBER));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, MEMBER_ORDERED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, INSERTABLE));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillPred(i, buffer, INSERTABLE_REQUIRED));
            assertArrayEquals(expected, buffer);

            assertEquals(0, seqVar.fillSucc(i, buffer, NOT_EXCLUDED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, EXCLUDED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, POSSIBLE));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, REQUIRED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, MEMBER));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, MEMBER_ORDERED));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, INSERTABLE));
            assertArrayEquals(expected, buffer);
            assertEquals(0, seqVar.fillSucc(i, buffer, INSERTABLE_REQUIRED));
            assertArrayEquals(expected, buffer);

            assertEquals(0, seqVar.nInsert(i));
            assertEquals(0, seqVar.fillInsert(i, buffer));
            assertArrayEquals(expected, buffer);
        }
        assertEquals(member.length + excluded.length == nNode, seqVar.isFixed());
    }

    /**
     * Assert the full state of a sequence
     * Assumes that no insertion has been removed
     * Test quite a bunch of stuff and is quite expensive. This is rather safe but expensive if testing simple operations
     *
     * @param seqVar   sequence variable
     * @param member   member nodes, given in order of appearance within the sequence (member[0] is always the start and member[member.length-1] the end)
     * @param required required nodes. Contains the member nodes as well
     * @param possible possible nodes
     * @param excluded excluded nodes
     */
    public static void assertSeqVar(CPSeqVar seqVar, int[] member, int[] required, int[] possible, int[] excluded) {
        // assume that the predecessors and successors are all present, fill their values and call the assertion
        int nNode = seqVar.nNode();
        int[][] insertions = new int[nNode][];
        Set<Integer> excludedSet = Arrays.stream(excluded).boxed().collect(toSet());
        Set<Integer> memberSet = Arrays.stream(member).boxed().collect(toSet());
        for (int node = 0; node < nNode; ++node) {
            if (excludedSet.contains(node) || memberSet.contains(node)) {
                // node is member or excluded -> no insertion
                insertions[node] = new int[0];
            } else {
                // node is insertable after every member except the last one
                insertions[node] = new int[member.length - 1];
                System.arraycopy(member, 0, insertions[node], 0, member.length - 1);
            }
        }
        assertSeqVar(seqVar, member, required, possible, excluded, insertions);
    }

    /**
     * Assert the full state of a sequence
     * Assumes that the required nodes correspond exactly to the member nodes
     * Test quite a bunch of stuff and is quite expensive. This is rather safe but expensive if testing simple operations
     *
     * @param seqVar     sequence variable
     * @param member     member nodes, given in order of appearance within the sequence (member[0] is always the begin and member[member.length-1] the end)
     * @param possible   possible nodes
     * @param excluded   excluded nodes
     * @param insertions predecessors of each node.
     */
    public static void assertSeqVar(CPSeqVar seqVar, int[] member, int[] possible, int[] excluded, int[][] insertions) {
        assertSeqVar(seqVar, member, member, possible, excluded, insertions);
    }

    /**
     * Assert the full state of a sequence
     * Assumes that
     * - no particular insertion has been removed outside the ones related to the exclusion of nodes
     * - the required nodes correspond exactly to the member nodes
     * Test quite a bunch of stuff and is quite expensive. This is rather safe but expensive if testing simple operations
     *
     * @param seqVar   sequence variable
     * @param member   member nodes, given in order of appearance within the sequence (member[0] is always the begin and member[member.length-1] the end)
     * @param possible possible nodes
     * @param excluded excluded nodes
     */
    public static void assertSeqVar(CPSeqVar seqVar, int[] member, int[] possible, int[] excluded) {
        assertSeqVar(seqVar, member, member, possible, excluded);
    }

    /**
     * Assert the invariants maintained within a sequence
     * This is only relevant for testing stuff like the maintaining of the counters, which is supposedly done in a lazy fashion
     *
     * @param seqVar sequence variable
     */
    public static void assertCounterInvariant(CPSeqVar seqVar) {
        int[] member = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(member, MEMBER_ORDERED);
        int[] possible = new int[seqVar.nNode(POSSIBLE)];
        seqVar.fillNode(possible, POSSIBLE);
        int[] excluded = new int[seqVar.nNode(EXCLUDED)];
        seqVar.fillNode(excluded, EXCLUDED);
        int[] required = new int[seqVar.nNode(REQUIRED)];
        seqVar.fillNode(required, REQUIRED);
        int[][] predecessors = new int[seqVar.nNode()][];
        for (int node = 0; node < seqVar.nNode(); node++) {
            predecessors[node] = new int[seqVar.nInsert(node)];
            seqVar.fillInsert(node, predecessors[node]);
        }
        assertSeqVar(seqVar, member, required, possible, excluded, predecessors);
    }

    private static <E> List<List<E>> generatePerm(List<E> original) {
        if (original.isEmpty()) {
            List<List<E>> result = new ArrayList<>();
            result.add(new ArrayList<>());
            return result;
        }
        E firstElement = original.remove(0);
        List<List<E>> returnValue = new ArrayList<>();
        List<List<E>> permutations = generatePerm(original);
        for (List<E> smallerPermutated : permutations) {
            for (int index = 0; index <= smallerPermutated.size(); index++) {
                List<E> temp = new ArrayList<>(smallerPermutated);
                temp.add(index, firstElement);
                returnValue.add(temp);
            }
        }
        return returnValue;
    }

}
