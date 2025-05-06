package org.maxicp.xcsp3;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.constraints.*;
import org.maxicp.search.DFSearch;
import org.maxicp.util.ImmutableSet;
import org.maxicp.util.exception.NotImplementedException;
import org.maxicp.util.exception.NotYetImplementedException;
import org.xcsp.common.Condition;
import org.xcsp.common.Constants;
import org.xcsp.common.IVar;
import org.xcsp.common.Types;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.common.structures.Transition;
import org.xcsp.parser.callbacks.XCallbacks;
import org.xcsp.parser.entries.XVariables;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class XCSP3 extends XCallbacksDecomp {
    public record XCSP3LoadedInstance(ModelDispatcher md, IntExpression[] decisionVars, Supplier<String> solutionGenerator) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            md.close();
        }
    }

    public static XCSP3LoadedInstance load(String filename) throws Exception {
        XCSP3 xcsp3 = new XCSP3();
        try {
            xcsp3.loadInstance(filename);
            System.out.println("here");
        }
        catch (Throwable t) {
            xcsp3.md.close();
            throw t;
        }

        Supplier<String> solutionGenerator = () -> {
            StringBuilder b = new StringBuilder();
            b.append("<instantiation>\n\t<list>\n\t\t");
            b.append(String.join(" ", xcsp3.decisionVars));
            b.append("\n\t</list>\n\t<values>\n\t\t");
            b.append(String.join(" ", xcsp3.decisionVars.stream().map(xcsp3.varHashMap::get).map(x -> {
                        try {
                            return Integer.toString(x.evaluate());
                        } catch (VariableNotFixedException e) {
                            throw new RuntimeException(e);
                        }
                    }).toArray(String[]::new)));
            b.append("\n\t</values>\n</instantiation>");
            return b.toString();
        };

        return new XCSP3LoadedInstance(xcsp3.md, xcsp3.decisionVars.stream().map(xcsp3.varHashMap::get).toArray(IntExpression[]::new), solutionGenerator);
    }

    private final LinkedHashMap<String, IntExpression> varHashMap;
    private final LinkedHashSet<String> decisionVars;
    private final ModelDispatcher md;

    private final XCallbacks.Implem impl;

    private XCSP3() {
        varHashMap = new LinkedHashMap<>();
        decisionVars = new LinkedHashSet<>();
        md = Factory.makeModelDispatcher();

        impl = new XCallbacks.Implem(this);
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_UNARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_BINARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_TERNARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZING_BEFORE_CONVERTING, true);
        impl.currParameters.put(XCallbacksParameters.CONVERT_INTENSION_TO_EXTENSION_ARITY_LIMIT, 0); // included
        impl.currParameters.put(XCallbacksParameters.CONVERT_INTENSION_TO_EXTENSION_SPACE_LIMIT, 0L); // included
    }

    @Override
    public Implem implem() {
        return impl;
    }

    @Override
    public void buildVarInteger(XVariables.XVarInteger x, int minValue, int maxValue) {
        addToDV(x);
        varHashMap.put(x.id(), md.intVar(x.id(), minValue, maxValue));
    }

    @Override
    public void buildVarInteger(XVariables.XVarInteger x, int[] values) {
        addToDV(x);
        varHashMap.put(x.id(), md.intVar(x.id(), values));
    }

    @Override
    public void buildCtrAllDifferent(String id, XVariables.XVarInteger[] list) {
        addToDV(list);
        md.add(new AllDifferent($(list)));
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorRel op, int k) {
        IntExpression o = $(x);
        switch (op) {
            case EQ -> md.add(Factory.eq(o, k));
            case NE -> md.add(Factory.neq(o, k));
            case GT -> md.add(Factory.gt(o, k));
            case GE -> md.add(Factory.ge(o, k));
            case LT -> md.add(Factory.lt(o, k));
            case LE -> md.add(Factory.le(o, k));
            default -> unimplementedCase(op);
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop, XVariables.XVarInteger y, Types.TypeConditionOperatorRel op, int k) {
        IntExpression $x = $(x);
        IntExpression $y = $(y);

        // special case x - y == 0 <=> x == y
        if(aop == Types.TypeArithmeticOperator.SUB && op == Types.TypeConditionOperatorRel.EQ && k == 0) {
            md.add(Factory.eq($x, $y));
            return;
        }
        // special case x + y == 0  <=> x - (-y) == 0 <=> x == -y
        if(aop == Types.TypeArithmeticOperator.ADD && op == Types.TypeConditionOperatorRel.EQ && k == 0) {
            md.add(Factory.eq($x, Factory.minus($y)));
            return;
        }


        IntExpression o = switch (aop) {
            case DIST -> $x.minus($y).abs();
            case ADD -> $x.plus($y);
            case SUB -> $x.minus($y);
            default -> {
                unimplementedCase(aop, op);
                yield null;
            }
        };
        switch (op) {
            case EQ -> md.add(Factory.eq(o, k));
            case NE -> md.add(Factory.neq(o, k));
            case GT -> md.add(Factory.gt(o, k));
            case GE -> md.add(Factory.ge(o, k));
            case LT -> md.add(Factory.lt(o, k));
            case LE -> md.add(Factory.le(o, k));
            default -> unimplementedCase(aop, op);
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop, int p, Types.TypeConditionOperatorRel op, XVariables.XVarInteger z) {
        IntExpression $x = $(x);
        IntExpression $z = $(z);
        IntExpression o = switch (aop) {
            case DIST -> $x.minus(p).abs();
            case ADD -> $x.plus(p);
            case SUB -> $x.minus(p);
            case MUL -> $x.times(p);
            default -> {
                unimplementedCase(aop, op);
                yield null;
            }
        };
        switch (op) {
            case EQ -> md.add(Factory.eq(o, $z));
            case NE -> md.add(Factory.neq(o, $z));
            case GT -> md.add(Factory.gt(o, $z));
            case GE -> md.add(Factory.ge(o, $z));
            case LT -> md.add(Factory.lt(o, $z));
            case LE -> md.add(Factory.le(o, $z));
            default -> unimplementedCase(aop, op);
        }
    }

    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop, XVariables.XVarInteger y, Types.TypeConditionOperatorRel op, XVariables.XVarInteger z) {
        IntExpression $x = $(x);
        IntExpression $y = $(y);
        IntExpression $z = $(z);
        IntExpression o = switch (aop) {
            case DIST -> $x.minus($y).abs();
            case ADD -> $x.plus($y);
            case SUB -> $x.minus($y);
            case MUL -> throw new NotImplementedException("No support for multiplication of variables");
            default -> {
                unimplementedCase(aop, op);
                yield null;
            }
        };
        switch (op) {
            case EQ -> md.add(Factory.eq(o, $z));
            case NE -> md.add(Factory.neq(o, $z));
            case GT -> md.add(Factory.gt(o, $z));
            case GE -> md.add(Factory.ge(o, $z));
            case LT -> md.add(Factory.lt(o, $z));
            case LE -> md.add(Factory.le(o, $z));
            default -> unimplementedCase(aop, op);
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorSet op, int[] t) {
        IntExpression $x = $(x);
        switch (op) {
            case IN -> md.add(new InSet($x, Arrays.stream(t).boxed().collect(ImmutableSet.toImmutableSet())));
            case NOTIN -> {
                for(int v: t)
                    md.add(Factory.neq($x, v));
            }
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorSet op, int min, int max) {
        IntExpression $x = $(x);
        switch (op) {
            case IN -> {
                md.add(Factory.le(min, $x));
                md.add(Factory.le($x, max));
            }
            case NOTIN -> {
                for(int v = min; v <= max; v++)
                    md.add(Factory.neq($x, v));
            }
        }
    }

    protected HashMap<String, IntExpression> expr_cache = new HashMap<>();
    
    private <V extends IVar> IntExpression _recursiveIntentionBuilder(XNode<V> node) {
        String key = node.toString();
        IntExpression val = expr_cache.get(key);
        if(val == null) {
            val = switch (node) {
                case XNodeLeaf<V> leaf -> _recursiveIntentionBuilderLeafNode(leaf);
                case XNodeParent<V> parent -> _recursiveIntentionBuilderParentNode(parent);
                default -> throw new RuntimeException("Unknown node type");
            };
            expr_cache.put(key, val);
        }
        return val;
    }

    private <V extends IVar> IntExpression _recursiveIntentionBuilderLeafNode(XNodeLeaf<V> node) {
        return switch (node.getType()) {
                case VAR -> varHashMap.get(node.value.toString());
                case LONG -> md.constant(((Long)node.value).intValue());
                default -> throw new NotYetImplementedException("Unknown node type %s in _recursiveIntentionBuilderLeafNode".formatted(node.getType()));
        };
    }

    private <V extends IVar> IntExpression _recursiveIntentionBuilderParentNode(XNodeParent<V> tree) {
        return switch (tree.getType()) {
            case IN -> {
                assert (tree.sons[1].getType() == Types.TypeExpr.SET);
                try {
                    Set<Integer> set = Arrays.stream(((XNodeParent<V>)tree.sons[1]).sons).map(i -> ((Long)((XNodeLeaf<V>)i).value).intValue()).collect(Collectors.toSet());
                    yield new InSet(_recursiveIntentionBuilder(tree.sons[0]), set);
                }
                catch (ClassCastException a){
                    //Cannot cast to XNodeLeaf => not only integers
                    IntExpression main = _recursiveIntentionBuilder(tree.sons[0]);
                    yield new Or(Arrays.stream(tree.sons[1].sons).map(x -> new Eq(_recursiveIntentionBuilder(x),main)).collect(ImmutableSet.toImmutableSet()));
                }
            }
            case NE -> {
                if(tree.sons.length != 2)
                    throw new NotImplementedException("No support for unequality of more than two elements");
                yield new NotEq(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            }
            case EQ -> {
                if(tree.sons.length != 2)
                    throw new NotYetImplementedException("No support for EQ with more than 2 elements yet");
                yield new Eq(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            }
            case ADD ->
                    new Sum(Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case MUL -> {
                if(tree.sons.length != 2)
                    throw new NotImplementedException("No support for multiplication of more than 2 elements directly");

                int cst = -1;
                XNode<V> other = null;
                if(tree.sons[0].getType() == Types.TypeExpr.LONG) {
                    throw new NotYetImplementedException("No support for multiplication yet");
                }
                else if(tree.sons[1].getType() == Types.TypeExpr.LONG) {
                    throw new NotYetImplementedException("No support for multiplication yet");
                }

                if(other == null) //no support for var-var multiplication.
                    throw new NotImplementedException("No support for var-var multiplication");
                throw new NotYetImplementedException("No support for multiplication yet");
            }
            case LT -> Factory.lt(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case LE -> Factory.le(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case GT -> Factory.gt(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case GE -> Factory.ge(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case AND -> Factory.and(Arrays.stream(tree.sons).map(x -> (BoolExpression) _recursiveIntentionBuilder(x)).toArray(BoolExpression[]::new));
            case OR -> Factory.or(Arrays.stream(tree.sons).map(x -> (BoolExpression) _recursiveIntentionBuilder(x)).toArray(BoolExpression[]::new));
            case DIST -> Factory.abs(Factory.minus(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1])));
            case DIV -> throw new NotImplementedException("No support for division");
            case MOD -> throw new NotImplementedException("No support for modulos");
            case IMP -> {
                BoolExpression a = (BoolExpression) _recursiveIntentionBuilder(tree.sons[0]);
                BoolExpression b = (BoolExpression) _recursiveIntentionBuilder(tree.sons[1]);
                yield Factory.or(Factory.not(a), b);
            }
            case NOT -> Factory.not((BoolExpression) _recursiveIntentionBuilder(tree.sons[0]));
            case NEG -> Factory.minus(_recursiveIntentionBuilder(tree.sons[0]));
            case IFF -> {
                if(tree.sons.length != 2)
                    throw new NotImplementedException("No support for IFF of more than 2 elements directly");
                yield Factory.eq(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            }
            default -> throw new NotYetImplementedException("Unknown type %s in _recursiveIntentionBuilderParentNode".formatted(tree.getType()));
        };
    }

    @Override
    public void buildCtrIntension(String id, XVariables.XVarInteger[] scope, XNodeParent<XVariables.XVarInteger> syntaxTreeRoot) {
        md.add(((BoolExpression)_recursiveIntentionBuilder(syntaxTreeRoot)));
    }

    private void buildCrtWithCondition(String id, IntExpression expr, Condition operator) {
        switch (operator) {
            case Condition.ConditionVal cv -> {
                int cst = (int)cv.k;
                switch (cv.operator) {
                    case EQ -> md.add(Factory.eq(expr, cst));
                    case LE -> md.add(Factory.le(expr, cst));
                    case LT -> md.add(Factory.lt(expr, cst));
                    case GE -> md.add(Factory.ge(expr, cst));
                    case GT -> md.add(Factory.gt(expr, cst));
                    case NE -> md.add(Factory.neq(expr, cst));
                    default -> throw new RuntimeException("Unknown operator %s".formatted(cv.operator));
                }
            }
            case Condition.ConditionVar cv -> {
                IntExpression v = $(cv.x);
                switch (cv.operator) {
                    case EQ -> md.add(Factory.eq(expr, v));
                    case LE -> md.add(Factory.le(expr, v));
                    case LT -> md.add(Factory.lt(expr, v));
                    case GE -> md.add(Factory.ge(expr, v));
                    case GT -> md.add(Factory.gt(expr, v));
                    case NE -> md.add(Factory.neq(expr, v));
                    default -> throw new RuntimeException("Unknown operator %s".formatted(cv.operator));
                }
            }
            default -> throw new RuntimeException("Unknown operator %s".formatted(operator.getClass()));
        }
    }
    public void buildCtrSum(String id, XVariables.XVarInteger[] list, Condition condition) {
        buildCrtWithCondition(id, new Sum(Arrays.stream(list).map(i -> varHashMap.get(i.id())).toArray(IntExpression[]::new)), condition);
    }

    public void buildCtrSum(String id, XVariables.XVarInteger[] list, int[] coeffs, Condition condition) {
        buildCrtWithCondition(id, new WeightedSum(Arrays.stream(list).map(i -> varHashMap.get(i.id())).toArray(IntExpression[]::new), coeffs), condition);
    }

    public void buildCtrExtension(String id, XVariables.XVarInteger x, int[] values, boolean positive, Set<Types.TypeFlag> flags) {
        assert(!flags.contains(Types.TypeFlag.STARRED_TUPLES)); // no sense!
        if(positive) {
            //InSet constraint
            md.add(new InSet(varHashMap.get(x.id()), ImmutableSet.of(values)));
        }
        else {
            IntExpression expr = varHashMap.get(x.id());
            for(int v: values)
                md.add(new NotEq(expr, v));
        }
    }

    public void buildCtrExtension(String id, XVariables.XVarInteger[] list, int[][] tuples, boolean positive, Set<Types.TypeFlag> flags) {
        if(positive) {
            md.add(new Table($(list), tuples, flags.contains(Types.TypeFlag.STARRED_TUPLES) ? Optional.of(Constants.STAR_INT) : Optional.empty()));
        }
        else {
            md.add(new NegTable($(list), tuples, flags.contains(Types.TypeFlag.STARRED_TUPLES) ? Optional.of(Constants.STAR_INT) : Optional.empty()));
        }
    }

    public void buildCtrInstantiation(String id, XVariables.XVarInteger[] list, int[] values) {
        for(int i = 0; i < list.length; i++)
            md.add(new Eq($(list[i]), values[i]));
    }

    public void buildCtrMinimum(String id, XVariables.XVarInteger[] list, Condition condition) {
        buildCrtWithCondition(id, new Min($(list)), condition);
    }

    public void buildCtrMaximum(String id, XVariables.XVarInteger[] list, Condition condition) {
        buildCrtWithCondition(id, new Max($(list)), condition);
    }

    public void buildCtrOrdered(String id, XVariables.XVarInteger[] list, Types.TypeOperatorRel operator) {
        BiFunction<IntExpression, IntExpression, BoolExpression> op = switch (operator) {
            case GE -> Factory::ge;
            case GT -> Factory::gt;
            case LT -> Factory::lt;
            case LE -> Factory::le;
        };
        IntExpression[] $list = $(list);
        for(int i = 0; i < $list.length - 1; i++)
            md.add(op.apply($list[i], $list[i+1]));
    }


    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[] list, Condition condition) {
        IntExpression[] array = $(list);
        IntExpression indexExpr = md.intVar(0, array.length-1);
        IntExpression element = Factory.get(array, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger index, Types.TypeRank rank, Condition condition) {
        if(rank != Types.TypeRank.ANY)
            throw new NotImplementedException("Element constraint only supports ANY as position for the index");
        IntExpression[] array = $(list);
        IntExpression indexExpr = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        decisionVars.add(index.id());
        IntExpression element = Factory.get(array, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, int[] list, int startIndex, XVariables.XVarInteger index, Types.TypeRank rank, Condition condition) {
        if(rank != Types.TypeRank.ANY)
            throw new NotImplementedException("Element constraint only supports ANY as position for the index");
        IntExpression indexExpr = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        decisionVars.add(index.id());
        IntExpression element = Factory.get(list, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, int[][] matrix, int startRowIndex, XVariables.XVarInteger rowIndex, int startColIndex, XVariables.XVarInteger colIndex,
                                 Condition condition) {
        unimplementedCase(id);
    }

    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[][] matrix, int startRowIndex, XVariables.XVarInteger rowIndex, int startColIndex, XVariables.XVarInteger colIndex,
                                 Condition condition) {
        unimplementedCase(id);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, int[] heights, Condition condition) {
        if(!(condition instanceof Condition.ConditionVal))
            throw new NotImplementedException("No support for variable capacities");
        int cap = (int)((Condition.ConditionVal)condition).k;
        md.add(new Cumulative($(origins), lengths, heights, cap));
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, XVariables.XVarInteger[] ends, int[] heights, Condition condition) {
        IntExpression[] $origins = $(origins);
        IntExpression[] $ends = $(ends);
        for(int i = 0; i < origins.length; i++)
            md.add(Factory.eq(Factory.plus($origins[i], lengths[i]), $ends[i]));
        buildCtrCumulative(id, origins, lengths, heights, condition);
    }



    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[] origins, int[] lengths, boolean zeroIgnored) {
        //TODO check for zeroIgnored.
        md.add(new Disjunctive($(origins), lengths));
    }

    @Override
    public void buildCtrCircuit(String id, XVariables.XVarInteger[] list, int startIndex) {
        if(startIndex != 0)
            throw new NotImplementedException("startIndex is not supported");
        md.add(new Circuit($(list)));
    }

    @Override
    public void buildCtrCircuit(String id, XVariables.XVarInteger[] list, int startIndex, int size) {
        if(size != list.length)
            throw new NotImplementedException("subcircuits are not supported");
        if(startIndex != 0)
            throw new NotImplementedException("startIndex is not supported");
        md.add(new Circuit($(list)));
    }

    private IntExpression getExprForTypeObjective(Types.TypeObjective objtype, IntExpression[] list) {
        return switch(objtype) {
            case MAXIMUM -> new Max(list);
            case MINIMUM -> new Min(list);
            case SUM -> new Sum(list);
            default -> throw new NotImplementedException("Unsupported objective type %s".formatted(objtype));
        };
    }

    private IntExpression getExprForTypeObjective(Types.TypeObjective objtype, IntExpression[] list, int[] coefs) {
        IntExpression[] listWithCoef = IntStream.range(0, list.length).mapToObj(i -> Factory.mul(list[i], coefs[i])).toArray(IntExpression[]::new);
        return switch(objtype) {
            case MAXIMUM -> new Max(listWithCoef);
            case MINIMUM -> new Min(listWithCoef);
            case SUM -> new Sum(listWithCoef);
            default -> throw new NotImplementedException("Unsupported objective type %s".formatted(objtype));
        };
    }

    @Override
    public void buildObjToMinimize(String id, XVariables.XVarInteger x) {
        md.minimize($(x));
    }

    @Override
    public void buildObjToMaximize(String id, XVariables.XVarInteger x) {
        md.maximize($(x));
    }

    @Override
    public void buildObjToMinimize(String id, XNodeParent<XVariables.XVarInteger> tree) {
        md.minimize(_recursiveIntentionBuilder(tree));
    }

    @Override
    public void buildObjToMaximize(String id, XNodeParent<XVariables.XVarInteger> tree) {
        md.maximize(_recursiveIntentionBuilder(tree));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list) {
        md.minimize(getExprForTypeObjective(type, $(list)));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list) {
        md.maximize(getExprForTypeObjective(type, $(list)));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list, int[] coeffs) {
        md.minimize(getExprForTypeObjective(type, $(list), coeffs));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list, int[] coeffs) {
        md.maximize(getExprForTypeObjective(type, $(list), coeffs));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        md.minimize(getExprForTypeObjective(type, list));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        md.maximize(getExprForTypeObjective(type, list));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees, int[] coeffs) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        md.minimize(getExprForTypeObjective(type, list, coeffs));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees, int[] coeffs) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        md.maximize(getExprForTypeObjective(type, list, coeffs));
    }


    private IntExpression[] $(XVariables.XVarInteger[] list) {
        return Arrays.stream(list).map(this::$).toArray(IntExpression[]::new);
    }

    private IntExpression $(XVariables.XVarInteger v) {
        return varHashMap.get(v.id());
    }

    private IntExpression[] $(IVar[] list) {
        return Arrays.stream(list).map(this::$).toArray(IntExpression[]::new);
    }

    private IntExpression $(IVar v) {
        return varHashMap.get(v.id());
    }

    private void addToDV(XVariables.XVarInteger ...v) {
        for(XVariables.XVarInteger e: v)
            decisionVars.add(e.id());
    }


    /*
     * CONSTRAINTS NOT IMPLEMENTED IN MAXICP
     */



    public void buildCtrLex(String id, XVariables.XVarInteger[][] lists, Types.TypeOperatorRel operator) {
        throw new NotImplementedException();
    }

    public void buildCtrNotAllEqual(String id, XVariables.XVarInteger[] list) {
        throw new NotImplementedException();
    }

    public void buildCtrAllEqual(String id, XVariables.XVarInteger[] list) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values, XVariables.XVarInteger[] occurs) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values, int[] occurs) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values, int[] occursMin, int[] occursMax) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, XVariables.XVarInteger[] values, XVariables.XVarInteger[] occurs) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, XVariables.XVarInteger[] values, int[] occurs) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, XVariables.XVarInteger[] values, int[] occursMin, int[] occursMax) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrSum(String id, XVariables.XVarInteger[] list, XVariables.XVarInteger[] coeffs, Condition condition) {
        //var*var
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrRegular(String id, XVariables.XVarInteger[] list, Transition[] transitions, String startState, String[] finalStates) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrMDD(String id, XVariables.XVarInteger[] list, Transition[] transitions) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, XVariables.XVarInteger[] heights, Condition condition) {
        //no support for variable heights
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, int[] heights, Condition condition) {
        //no support for variable lengths
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, XVariables.XVarInteger[] heights, Condition condition) {
        //no support for variable lengths and heights
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, XVariables.XVarInteger[] ends, XVariables.XVarInteger[] heights, Condition condition) {
        //no support for variable heights
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, XVariables.XVarInteger[] ends, int[] heights, Condition condition) {
        //no support for variable lengths
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, XVariables.XVarInteger[] ends, XVariables.XVarInteger[] heights, Condition condition) {
        //no support for variable lengths and heights
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCount(String id, XVariables.XVarInteger[] list, int[] values, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCount(String id, XNode<XVariables.XVarInteger>[] trees, int[] values, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrCount(String id, XVariables.XVarInteger[] list, XVariables.XVarInteger[] values, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrAtLeast(String id, XVariables.XVarInteger[] list, int value, int k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrAtMost(String id, XVariables.XVarInteger[] list, int value, int k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrExactly(String id, XVariables.XVarInteger[] list, int value, int k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrExactly(String id, XVariables.XVarInteger[] list, int value, XVariables.XVarInteger k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrAmong(String id, XVariables.XVarInteger[] list, int[] values, int k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrAmong(String id, XVariables.XVarInteger[] list, int[] values, XVariables.XVarInteger k) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNValues(String id, XVariables.XVarInteger[] list, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNValuesExcept(String id, XVariables.XVarInteger[] list, int[] except, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNValues(String id, XNode<XVariables.XVarInteger>[] trees, Condition condition) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, boolean zeroIgnored) {
        //no support for variable lengths
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, XVariables.XVarInteger[][] lengths, boolean zeroIgnored) {
        //no support for variable lengths
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, int[][] lengths, boolean zeroIgnored) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list, int startIndex) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list1, int startIndex1, XVariables.XVarInteger[] list2, int startIndex2) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger value) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrStretch(String id, XVariables.XVarInteger[] list, int[] values, int[] widthsMin, int[] widthsMax) {
        throw new NotImplementedException();
    }

    @Override
    public void buildCtrStretch(String id, XVariables.XVarInteger[] list, int[] values, int[] widthsMin, int[] widthsMax, int[][] patterns) {
        throw new NotImplementedException();
    }
}
