/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.constraints.seqvar.*;
import org.maxicp.modeling.symbolic.SeqVarImpl;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ModelingConstraintsAndExpressionsTest {

    @Test
    public void testElement1DVarSymbolicAndConcrete() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            IntVar a0 = baseModel.intVar(10, 12);
            IntVar a1 = baseModel.intVar(20, 22);
            IntVar a2 = baseModel.intVar(30, 32);
            IntVar[] array = new IntVar[]{a0, a1, a2};
            IntVar idx = baseModel.intVar(-1, 4);

            IntExpression elem = Factory.get(array, idx);
            assertInstanceOf(Element1DVar.class, elem);

            // Symbolic bounds & domain methods
            assertEquals(10, elem.min());
            assertEquals(32, elem.max());
            assertTrue(elem.contains(11));
            assertTrue(elem.contains(21));
            assertFalse(elem.contains(15));
            int[] dom = new int[elem.size()];
            int sz = elem.fillArray(dom);
            assertEquals(9, sz);
            assertEquals(4, elem.computeSubexpressions().size());
            assertNotNull(elem.toString());

            Expression mapped = elem.mapSubexpressions(e -> e);
            assertInstanceOf(Element1DVar.class, mapped);

            assertThrows(VariableNotFixedException.class, ((Element1DVar) elem)::defaultEvaluate);

            // Evaluate when fixed in symbolic model
            IntVar fixedElem = baseModel.intVar(15, 15);
            IntVar fixedIdx = baseModel.intVar(0, 0);
            Element1DVar fixedExpr = new Element1DVar(new IntExpression[]{fixedElem}, fixedIdx);
            assertEquals(15, fixedExpr.defaultEvaluate());

            // Concrete instantiation via both getCPVar and enforceEqualityIntExpression (Eq)
            IntVar target = baseModel.intVar(0, 40);
            baseModel.add(new Eq(target, elem));
            baseModel.add(Factory.le(elem, 25));

            baseModel.runCP(cp -> {
                assertEquals(0, idx.min());
                assertEquals(1, idx.max());
                assertEquals(10, target.min());
                assertEquals(22, target.max());

                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(a0, a1, a2, idx, target));
                SearchStatistics stats = dfs.solve();
                assertEquals(54, stats.numberOfSolutions());
            });
        }
    }

    @Test
    public void testElement2DSymbolicAndConcrete() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            int[][] T = new int[][]{
                    {1, 5, 9},
                    {2, 6, 10}
            };
            IntVar x = baseModel.intVar(-1, 3);
            IntVar y = baseModel.intVar(-1, 3);

            IntExpression elem = Factory.get(T, x, y);
            assertInstanceOf(Element2D.class, elem);

            assertEquals(1, elem.min());
            assertEquals(10, elem.max());
            assertTrue(elem.contains(6));
            assertFalse(elem.contains(4));
            int[] dom = new int[10];
            int sz = elem.fillArray(dom);
            assertEquals(6, sz);
            assertEquals(2, elem.computeSubexpressions().size());
            assertEquals(2, ((Element2D) elem).decisionVariables().size());
            assertNotNull(elem.toString());

            Expression mapped = elem.mapSubexpressions(e -> e);
            assertInstanceOf(Element2D.class, mapped);

            assertThrows(VariableNotFixedException.class, ((Element2D) elem)::defaultEvaluate);
            Element2D fixedElem = new Element2D(T, baseModel.intVar(1, 1), baseModel.intVar(2, 2));
            assertEquals(10, fixedElem.defaultEvaluate());

            IntVar z = baseModel.intVar(0, 20);
            baseModel.add(new Eq(z, elem));
            baseModel.add(Factory.ge(elem, 5));

            baseModel.runCP(cp -> {
                assertEquals(0, x.min());
                assertEquals(1, x.max());
                assertEquals(1, y.min());
                assertEquals(2, y.max());
                assertEquals(5, z.min());
                assertEquals(10, z.max());

                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(x, y, z));
                SearchStatistics stats = dfs.solve();
                assertEquals(4, stats.numberOfSolutions());
            });
        }
    }

    @Test
    public void testElement2DVarSymbolicAndConcrete() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            IntVar v00 = baseModel.intVar(1, 2);
            IntVar v01 = baseModel.intVar(3, 4);
            IntVar v10 = baseModel.intVar(5, 6);
            IntVar v11 = baseModel.intVar(7, 8);
            IntVar[][] matrix = new IntVar[][]{
                    {v00, v01},
                    {v10, v11}
            };
            IntVar x = baseModel.intVar(-1, 2);
            IntVar y = baseModel.intVar(0, 3);

            IntExpression elem = Factory.get(matrix, x, y);
            assertInstanceOf(Element2DVar.class, elem);

            assertEquals(1, elem.min());
            assertEquals(8, elem.max());
            assertTrue(elem.contains(4));
            assertFalse(elem.contains(10));
            int[] dom = new int[10];
            int sz = elem.fillArray(dom);
            assertEquals(8, sz);
            assertEquals(6, elem.computeSubexpressions().size());
            assertNotNull(elem.toString());

            Expression mapped = elem.mapSubexpressions(e -> e);
            assertInstanceOf(Element2DVar.class, mapped);

            assertThrows(VariableNotFixedException.class, ((Element2DVar) elem)::defaultEvaluate);
            Element2DVar fixedElem = new Element2DVar(
                    new IntExpression[][]{{baseModel.intVar(42, 42)}},
                    baseModel.intVar(0, 0),
                    baseModel.intVar(0, 0)
            );
            assertEquals(42, fixedElem.defaultEvaluate());

            IntVar z = baseModel.intVar(0, 20);
            baseModel.add(new Eq(z, elem));
            baseModel.add(Factory.ge(elem, 5));

            baseModel.runCP(cp -> {
                assertEquals(1, x.min());
                assertEquals(1, x.max());
                assertEquals(0, y.min());
                assertEquals(1, y.max());

                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(v00, v01, v10, v11, x, y, z));
                SearchStatistics stats = dfs.solve();
                assertEquals(32, stats.numberOfSolutions());
            });
        }
    }

    @Test
    public void testDivSymbolicAndConcrete() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            IntVar x = baseModel.intVar(-6, 8);
            IntVar yPos = baseModel.intVar(2, 4);
            IntVar yNeg = baseModel.intVar(-4, -2);
            IntVar ySpanZero = baseModel.intVar(-2, 2);

            Div divPos = new Div(x, yPos);
            assertEquals(-3, divPos.min());
            assertEquals(4, divPos.max());
            assertTrue(divPos.contains(2));
            assertFalse(divPos.contains(10));

            Div divNeg = new Div(x, yNeg);
            assertEquals(-4, divNeg.min());
            assertEquals(3, divNeg.max());

            Div divSpan = new Div(x, ySpanZero);
            assertEquals(-8, divSpan.min());
            assertEquals(8, divSpan.max());
            int[] dom = new int[divPos.size()];
            int sz = divPos.fillArray(dom);
            assertTrue(sz > 0);
            assertEquals(2, divPos.computeSubexpressions().size());
            assertInstanceOf(Div.class, divPos.mapSubexpressions(e -> e));

            assertThrows(VariableNotFixedException.class, divPos::defaultEvaluate);
            Div divByZeroFixed = new Div(baseModel.intVar(4, 4), baseModel.intVar(0, 0));
            assertThrows(ArithmeticException.class, divByZeroFixed::defaultEvaluate);
            Div divFixed = new Div(baseModel.intVar(8, 8), baseModel.intVar(2, 2));
            assertEquals(4, divFixed.defaultEvaluate());

            // Concrete instantiation with constant divisor (both Eq and getCPVar)
            IntVar a = baseModel.intVar(4, 9);
            IntExpression divConst = Factory.div(a, 2);
            IntVar q = baseModel.intVar(0, 10);
            baseModel.add(new Eq(q, divConst));
            baseModel.add(Factory.le(divConst, 3));

            baseModel.runCP(cp -> {
                assertEquals(2, q.min());
                assertEquals(3, q.max());
                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(a, q));
                SearchStatistics stats = dfs.solve();
                assertEquals(4, stats.numberOfSolutions());
            });
        }
    }

    @Test
    public void testIntervalStartEndLengthOrValueAndPrecedences() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            // intervalVar(startMin, startMax, endMin, endMax, lengthMin, lengthMax, isPresent)
            IntervalVar opt = baseModel.intervalVar(2, 3, 5, 6, 3, 4, false);
            IntervalVar pres = baseModel.intervalVar(1, 5, 2, true);
            IntervalVar abs = baseModel.intervalVar(0, 10, 2, false);
            baseModel.add(Factory.eq(abs.status(), 0));
            org.maxicp.modeling.BoolVar bv = baseModel.boolVar();
            baseModel.add(Factory.eq(bv, 1));

            IntExpression sOpt = Factory.startOr(opt, 0);
            IntExpression eOpt = Factory.endOr(opt, 10);
            IntExpression lOpt = Factory.lengthOr(opt, 0);
            assertInstanceOf(IntervalStartOrValue.class, sOpt);
            assertInstanceOf(IntervalEndOrValue.class, eOpt);
            assertInstanceOf(IntervalLengthOrValue.class, lOpt);

            // Optional symbolic properties
            assertEquals(0, sOpt.min());
            assertEquals(3, sOpt.max());
            assertTrue(sOpt.contains(0));
            assertTrue(sOpt.contains(2));
            assertFalse(sOpt.contains(1));
            assertEquals(3, sOpt.size());
            int[] arr = new int[5];
            assertEquals(3, sOpt.fillArray(arr));
            assertFalse(sOpt.isFixed());
            assertEquals(1, sOpt.computeSubexpressions().size());
            assertSame(baseModel, sOpt.getModelProxy());
            assertThrows(org.maxicp.util.exception.NotYetImplementedException.class, () -> sOpt.mapSubexpressions(e -> e));
            assertThrows(VariableNotFixedException.class, ((IntervalStartOrValue) sOpt)::defaultEvaluate);

            assertEquals(5, eOpt.min());
            assertEquals(10, eOpt.max());
            assertTrue(eOpt.contains(10));
            assertTrue(eOpt.contains(5));
            assertFalse(eOpt.contains(8));
            assertEquals(3, eOpt.size());
            assertEquals(3, eOpt.fillArray(arr));
            assertFalse(eOpt.isFixed());
            assertEquals(1, eOpt.computeSubexpressions().size());
            assertSame(baseModel, eOpt.getModelProxy());
            assertThrows(org.maxicp.util.exception.NotYetImplementedException.class, () -> eOpt.mapSubexpressions(e -> e));
            assertThrows(VariableNotFixedException.class, ((IntervalEndOrValue) eOpt)::defaultEvaluate);

            assertEquals(0, lOpt.min());
            assertEquals(4, lOpt.max());
            assertTrue(lOpt.contains(0));
            assertTrue(lOpt.contains(3));
            assertFalse(lOpt.contains(2));
            assertEquals(3, lOpt.size());
            assertEquals(3, lOpt.fillArray(arr));
            assertFalse(lOpt.isFixed());
            assertEquals(1, lOpt.computeSubexpressions().size());
            assertSame(baseModel, lOpt.getModelProxy());
            assertThrows(org.maxicp.util.exception.NotYetImplementedException.class, () -> lOpt.mapSubexpressions(e -> e));
            assertThrows(VariableNotFixedException.class, ((IntervalLengthOrValue) lOpt)::defaultEvaluate);

            // Present symbolic properties
            IntExpression sPres = Factory.startOr(pres, 99);
            IntExpression ePres = Factory.endOr(pres, 99);
            IntExpression lPres = Factory.lengthOr(pres, 99);
            assertEquals(1, sPres.min());
            assertEquals(3, sPres.max());
            assertFalse(sPres.contains(99));
            assertEquals(3, sPres.size());
            assertEquals(3, sPres.fillArray(arr));

            assertEquals(3, ePres.min());
            assertEquals(5, ePres.max());
            assertFalse(ePres.contains(99));
            assertEquals(3, ePres.size());
            assertEquals(3, ePres.fillArray(arr));

            assertEquals(2, lPres.min());
            assertEquals(2, lPres.max());
            assertTrue(lPres.isFixed());
            assertEquals(2, ((IntervalLengthOrValue) lPres).defaultEvaluate());
            assertEquals(1, lPres.size());
            assertEquals(1, lPres.fillArray(arr));

            // Fixed present interval symbolic evaluation
            IntervalVar fixedPres = baseModel.intervalVar(4, 6, 2, true);
            IntExpression sFixed = Factory.startOr(fixedPres, 99);
            IntExpression eFixed = Factory.endOr(fixedPres, 99);
            assertTrue(sFixed.isFixed());
            assertTrue(eFixed.isFixed());
            assertEquals(4, ((IntervalStartOrValue) sFixed).defaultEvaluate());
            assertEquals(6, ((IntervalEndOrValue) eFixed).defaultEvaluate());

            // Modeling precedence constraints + EqHelper on startOr/endOr/lengthOr
            IntVar xStart = baseModel.intVar(0, 10);
            IntVar xEnd = baseModel.intVar(0, 10);
            IntVar xLen = baseModel.intVar(0, 10);
            baseModel.add(Factory.eq(sPres, xStart));
            baseModel.add(Factory.eq(ePres, xEnd));
            baseModel.add(Factory.eq(lPres, xLen));
            baseModel.add(Factory.startAfter(pres, 2));
            baseModel.add(Factory.endBefore(pres, baseModel.constant(5)));
            baseModel.add(Factory.startBefore(fixedPres, baseModel.constant(4)));
            baseModel.add(Factory.endAfter(fixedPres, 6));
            baseModel.add(Factory.endBeforeStart(pres, fixedPres));

            baseModel.runCP(cp -> {
                assertTrue(bv.isFixed() && bv.min() == 1);
                assertEquals(2, xStart.min());
                assertEquals(2, xStart.max());
                assertEquals(4, xEnd.min());
                assertEquals(4, xEnd.max());
                assertEquals(2, xLen.min());
                assertEquals(2, xLen.max());
                // Inside ConcreteCPModel, test absent interval expressions too
                IntExpression sAbs = Factory.startOr(abs, 77);
                IntExpression eAbs = Factory.endOr(abs, 88);
                IntExpression lAbs = Factory.lengthOr(abs, 99);
                assertTrue(sAbs.isFixed());
                assertTrue(eAbs.isFixed());
                assertTrue(lAbs.isFixed());
                assertEquals(77, sAbs.min());
                assertEquals(77, sAbs.max());
                assertTrue(sAbs.contains(77));
                assertEquals(1, sAbs.size());
                assertEquals(1, sAbs.fillArray(arr));
                assertEquals(77, assertDoesNotThrow(((IntervalStartOrValue) sAbs)::defaultEvaluate));

                assertEquals(88, eAbs.min());
                assertEquals(88, eAbs.max());
                assertTrue(eAbs.contains(88));
                assertEquals(1, eAbs.size());
                assertEquals(1, eAbs.fillArray(arr));
                assertEquals(88, assertDoesNotThrow(((IntervalEndOrValue) eAbs)::defaultEvaluate));

                assertEquals(99, lAbs.min());
                assertEquals(99, lAbs.max());
                assertTrue(lAbs.contains(99));
                assertEquals(1, lAbs.size());
                assertEquals(1, lAbs.fillArray(arr));
                assertEquals(99, assertDoesNotThrow(((IntervalLengthOrValue) lAbs)::defaultEvaluate));

                assertEquals(2, sPres.min());
                assertEquals(2, sPres.max());
                assertEquals(4, ePres.min());
                assertEquals(4, ePres.max());
                assertEquals(2, lPres.min());
            });
        }
    }

    @Test
    public void testSeqVarSymbolicAndConcreteAndSeqVarConstraints() throws Exception {
        try (ModelDispatcher baseModel = Factory.makeModelDispatcher()) {
            SeqVar seq = baseModel.seqVar(6, 0, 5);
            assertInstanceOf(SeqVarImpl.class, seq);
            SeqVarImpl seqImpl = (SeqVarImpl) seq;

            // Symbolic methods before cpInstantiate across all SeqStatus values
            assertSame(baseModel, seqImpl.getModelProxy());
            assertEquals(6, seqImpl.nNode());
            assertEquals(0, seqImpl.start());
            assertEquals(5, seqImpl.end());
            assertFalse(seqImpl.isFixed());
            assertEquals("0 -> 5", seqImpl.toString());
            assertNotNull(seqImpl.isNodeRequired(1));

            int[] buf = new int[10];
            for (SeqStatus status : SeqStatus.values()) {
                assertTrue(seqImpl.fillNode(buf, status) >= 0);
                assertTrue(seqImpl.nNode(status) >= 0);
                seqImpl.isNode(0, status);
                seqImpl.isNode(1, status);
                assertTrue(seqImpl.fillPred(1, buf, status) >= 0);
                assertEquals(0, seqImpl.fillPred(0, buf, status));
                assertTrue(seqImpl.fillSucc(1, buf, status) >= 0);
                assertEquals(0, seqImpl.fillSucc(5, buf, status));
            }
            assertEquals(0, seqImpl.nPred(0));
            assertEquals(4, seqImpl.nPred(1));
            assertEquals(0, seqImpl.nSucc(5));
            assertEquals(4, seqImpl.nSucc(1));
            assertEquals(5, seqImpl.memberAfter(0));
            assertEquals(0, seqImpl.memberAfter(5));
            assertEquals(1, seqImpl.memberAfter(1));
            assertEquals(5, seqImpl.memberBefore(0));
            assertEquals(0, seqImpl.memberBefore(5));
            assertEquals(1, seqImpl.memberBefore(1));
            assertEquals(0, seqImpl.fillInsert(0, buf));
            assertEquals(1, seqImpl.fillInsert(1, buf));
            assertEquals(0, seqImpl.nInsert(0));
            assertEquals(1, seqImpl.nInsert(1));
            assertTrue(seqImpl.hasEdge(0, 1));
            assertFalse(seqImpl.hasEdge(1, 0));
            assertFalse(seqImpl.hasEdge(5, 1));
            assertTrue(seqImpl.hasInsert(0, 1));
            assertFalse(seqImpl.hasInsert(2, 1));

            // Add modeling seqvar constraints:
            SeqVar subSeq = baseModel.seqVar(6, 0, 5);
            baseModel.add(Factory.require(seq, 1));
            baseModel.add(Factory.require(seq, 2));
            baseModel.add(Factory.require(seq, 3));
            baseModel.add(Factory.exclude(seq, 4));
            baseModel.add(Factory.insert(seq, 0, 1));
            baseModel.add(new Precedence(seq, 1, 2, 3));
            baseModel.add(Factory.require(subSeq, 1));
            baseModel.add(Factory.require(subSeq, 3));
            baseModel.add(Factory.exclude(subSeq, 2));
            baseModel.add(Factory.exclude(subSeq, 4));
            baseModel.add(new SubSequence(seq, subSeq));
            baseModel.add(Factory.notBetween(seq, 0, 4, 5));

            // TransitionTimes, Distance, Cumulative on seq
            IntVar[] time = baseModel.intVarArray(6, 25);
            int[][] distMatrix = new int[6][6];
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 6; j++) {
                    distMatrix[i][j] = (i == j) ? 0 : 1;
                }
            }
            IntVar totalDist = baseModel.intVar(0, 20);
            baseModel.add(Factory.transitionTimes(seq, time, distMatrix, new int[]{1, 1, 1, 1, 1, 1}));
            baseModel.add(new TransitionTimes(seq, time, distMatrix));
            baseModel.add(Factory.distance(seq, distMatrix, totalDist));
            baseModel.add(Factory.cumulative(seq, new int[]{1}, new int[]{3}, new int[]{1}, 2));

            baseModel.runCP(cp -> {
                assertTrue(seq.isNode(1, SeqStatus.REQUIRED));
                assertTrue(seq.isNode(4, SeqStatus.EXCLUDED));
                assertTrue(assertDoesNotThrow(() -> seq.isNodeRequired(1).evaluateBool()));
                assertTrue(seq.nNode() >= 2);
                assertEquals(1, seq.memberAfter(0));
                assertEquals(0, seq.memberBefore(1));
                assertTrue(seq.nPred(1) >= 0);
                assertTrue(seq.nSucc(0) >= 0);
                seq.fillPred(1, buf, SeqStatus.MEMBER);
                seq.fillSucc(0, buf, SeqStatus.MEMBER);
                seq.fillInsert(2, buf);
                assertTrue(seq.nInsert(2) >= 0);
                seq.hasInsert(1, 2);
                assertNotNull(seq.toString());

                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(seq, subSeq));
                SearchStatistics stats = dfs.solve();
                assertEquals(1, stats.numberOfSolutions());
            });
        }
    }

    @Test
    public void testModelingGlobalAndSchedulingConstraints() throws Exception {
        // 1. Among & CardinalityMin & CardinalityMax & LexLe
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] x = model.intVarArray(4, 4);
            IntVar nAmong = model.intVar(2, 2);
            model.add(new Among(x, Set.of(1, 2), nAmong));
            model.add(Factory.cardinalityMin(x, new int[]{1, 1, 0, 0}));
            model.add(Factory.cardinalityMax(x, new int[]{2, 1, 1, 2}));

            IntVar[] y = model.intVarArray(4, 4);
            model.add(Factory.lexLe(x, y));
            model.add(Factory.lexLt(new IntVar[]{x[0]}, new IntVar[]{y[0]}));

            model.runCP(cp -> {
                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(
                        new IntVar[]{x[0], x[1], x[2], x[3], y[0], y[1], y[2], y[3]}));
                SearchStatistics stats = dfs.solve(s -> s.numberOfSolutions() >= 5);
                assertEquals(5, stats.numberOfSolutions());
            });
        }

        // 2. SubCircuit
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] succ = model.intVarArray(4, 4);
            model.add(new SubCircuit(succ));
            model.runCP(cp -> {
                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(succ));
                SearchStatistics stats = dfs.solve();
                assertEquals(21, stats.numberOfSolutions());
            });
        }

        // 3. Disjunctive (int[] and IntExpression[]) & Cumulative (fixed and variable durations/demands/capa)
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] s1 = model.intVarArray(3, 6);
            model.add(new Disjunctive(s1, new int[]{2, 2, 2}));

            IntVar[] s2 = model.intVarArray(2, 4);
            IntVar[] d2 = new IntVar[]{model.intVar(2, 2), model.intVar(2, 2)};
            model.add(new Disjunctive(s2, d2));

            IntVar[] s3 = model.intVarArray(3, 4);
            IntVar[] e3 = model.intVarArray(3, 6);
            IntVar[] dur3 = new IntVar[]{model.intVar(2, 2), model.intVar(2, 2), model.intVar(0, 0)};
            IntVar[] req3 = new IntVar[]{model.intVar(1, 2), model.intVar(1, 2), model.intVar(0, 0)};
            IntVar capa3 = model.intVar(2, 2);

            org.maxicp.modeling.constraints.Cumulative cumVar =
                    new org.maxicp.modeling.constraints.Cumulative(s3, dur3, e3, req3, capa3);
            assertTrue(cumVar.toString().contains("Cumulative"));
            model.add(cumVar);

            IntVar[] s4 = model.intVarArray(2, 3);
            org.maxicp.modeling.constraints.Cumulative cumFixed =
                    new org.maxicp.modeling.constraints.Cumulative(s4, new int[]{2, 0}, new int[]{1, 0}, 1);
            model.add(cumFixed);

            model.runCP(cp -> {
                DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(s1));
                SearchStatistics stats = dfs.solve();
                assertEquals(24, stats.numberOfSolutions());
            });
        }
    }
}
