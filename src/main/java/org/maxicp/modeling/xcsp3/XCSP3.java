package org.maxicp.modeling.xcsp3;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.search.blackbox.ModelingBlackBox;
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
import org.xcsp.parser.callbacks.SolutionChecker;
import org.xcsp.parser.callbacks.XCallbacks;
import org.xcsp.parser.entries.XVariables;
import org.xcsp.parser.entries.XVariables.XArray;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class XCSP3 extends XCallbacksDecomp {

    /**
     * Runs BlackBoxSearch on the XCSP3 instance at {@code path}.
     */
    public static void runWithBlackBox(String path, int timeoutInSeconds) throws Exception {
        try (XCSP3LoadedInstance instance = load(path)) {
            ModelingBlackBox.Result result = instance.objective() != null
                    ? ModelingBlackBox.optimize(instance.md(), instance.objective(),
                            timeoutInSeconds,
                            c -> c.verbosity(BlackBoxSearch.Verbosity.PROGRESS))
                    : ModelingBlackBox.solve(instance.md(), timeoutInSeconds,
                            c -> c.verbosity(BlackBoxSearch.Verbosity.PROGRESS));

            System.out.printf("blackbox status: %s%n", result.status());
            System.out.printf("feasible solution found: %s%n", result.solution().isPresent() ? "yes" : "no");
            if (result.objectiveValue().isPresent()) {
                System.out.printf("objective: %d%n", result.objectiveValue().get());
            }

            if (result.solution().isPresent()) {
                String sol = instance.formatSolution(result);
                SolutionChecker sc = new SolutionChecker(false, path, new ByteArrayInputStream(sol.getBytes()));
                assertEquals(0, sc.invalidObjs.size());
                assertEquals(0, sc.violatedCtrs.size());
                System.out.println("solution validated by XCSP3 checker");
            } else {
                System.out.println("no solution produced within the given timeout");
            }
        }
    }

    public record XCSP3LoadedInstance(ModelDispatcher md, IntExpression[] decisionVars,
            String[] decisionVarIds,
            Supplier<String> solutionGenerator,
            Objective objective,
            Function<List<Integer>, String> solutionFormatter) implements AutoCloseable {

        public XCSP3LoadedInstance(ModelDispatcher md, IntExpression[] decisionVars,
                String[] decisionVarIds,
                Supplier<String> solutionGenerator,
                Objective objective) {
            this(md, decisionVars, decisionVarIds, solutionGenerator, objective,
                    vals -> buildInstantiation(decisionVarIds, vals));
        }

        public String formatSolution(List<Integer> values) {
            return solutionFormatter != null ? solutionFormatter.apply(values) : buildInstantiation(decisionVarIds, values);
        }

        public String formatSolution(ModelingBlackBox.Result result) {
            List<Integer> values = result.values(decisionVars)
                    .orElseGet(() -> result.solution()
                            .orElseThrow(() -> new IllegalArgumentException("Result has no solution")));
            return formatSolution(values);
        }

        @Override
        public void close() throws Exception {
            md.close();
        }
    }

    public static XCSP3LoadedInstance load(String filename) throws Exception {
        XCSP3 xcsp3 = new XCSP3();
        try {
            xcsp3.loadInstance(filename);
        } catch (Throwable t) {
            xcsp3.md.close();
            throw t;
        }

        String[] decisionVarIds = xcsp3.decisionVars.toArray(String[]::new);
        Function<List<Integer>, String> formatter = values -> xcsp3.formatInstantiation(decisionVarIds, values);

        Supplier<String> solutionGenerator = () -> {
            List<Integer> values = Arrays.stream(decisionVarIds)
                    .map(xcsp3.varHashMap::get)
                    .map(x -> {
                        try {
                            return x.evaluate();
                        } catch (VariableNotFixedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
            return formatter.apply(values);
        };

        return new XCSP3LoadedInstance(
                xcsp3.md,
                Arrays.stream(decisionVarIds).map(xcsp3.varHashMap::get).toArray(IntExpression[]::new),
                decisionVarIds,
                solutionGenerator,
                xcsp3.objective,
                formatter);
    }

    public static String buildInstantiation(String[] varIds, List<Integer> values) {
        if (varIds.length != values.size()) {
            throw new IllegalArgumentException("decision variable ids and values must have same size");
        }
        StringBuilder b = new StringBuilder();
        b.append("<instantiation>\n\t<list>\n\t\t");
        b.append(String.join(" ", varIds));
        b.append("\n\t</list>\n\t<values>\n\t\t");
        b.append(values.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        b.append("\n\t</values>\n</instantiation>");
        return b.toString();
    }

    private String formatInstantiation(String[] varIds, List<Integer> values) {
        if (varIds.length != values.size()) {
            throw new IllegalArgumentException("decision variable ids and values must have same size");
        }
        if (symbolicVariables.isEmpty()) {
            return buildInstantiation(varIds, values);
        }
        StringBuilder b = new StringBuilder();
        b.append("<instantiation>\n\t<list>\n\t\t");
        b.append(String.join(" ", varIds));
        b.append("\n\t</list>\n\t<values>\n\t\t");
        for (int i = 0; i < varIds.length; i++) {
            if (i > 0) b.append(' ');
            if (symbolicVariables.containsKey(varIds[i])) {
                b.append(symbolicValuesReversed.get(values.get(i)));
            } else {
                b.append(values.get(i));
            }
        }
        b.append("\n\t</values>\n</instantiation>");
        return b.toString();
    }

    private final LinkedHashMap<String, IntExpression> varHashMap;
    private final LinkedHashSet<String> decisionVars;
    private final ModelDispatcher md;
    private Objective objective;

    private final XCallbacks.Implem impl;

    private XCSP3() {
        varHashMap = new LinkedHashMap<>();
        decisionVars = new LinkedHashSet<>();
        md = Factory.makeModelDispatcher();
        objective = null;

        impl = new XCallbacks.Implem(this);
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_UNARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_BINARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZE_TERNARY_PRIMITIVES, new Object());
        impl.currParameters.put(XCallbacksParameters.RECOGNIZING_BEFORE_CONVERTING, true);
        // Avoid aggressive intension-to-extension conversion, which can create huge tables.
        impl.currParameters.put(XCallbacksParameters.CONVERT_INTENSION_TO_EXTENSION_ARITY_LIMIT, 5);
        impl.currParameters.put(XCallbacksParameters.CONVERT_INTENSION_TO_EXTENSION_SPACE_LIMIT, 200_000L);
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
    public void endArray(XArray a) {
        super.endArray(a);
        List<IntExpression> groupVars = new ArrayList<>();
        for (XVariables.XVar x : a.vars) {
            if (x == null) continue;
            if (x instanceof XVariables.XVarInteger) {
                IntExpression expr = varHashMap.get(x.id());
                if (expr != null) {
                    groupVars.add(expr);
                }
            } else if (x instanceof XVariables.XVarSymbolic && symbolicVariables.containsKey(x.id)) {
                IntExpression expr = varHashMap.get(x.id);
                if (expr != null) {
                    groupVars.add(expr);
                }
            }
        }
        if (!groupVars.isEmpty()) {
            md.addVariableGroup(groupVars);
        }
    }

    private BoolExpression asBoolExpr(IntExpression expr) {
        if (expr instanceof BoolExpression be) {
            return be;
        }
        return Factory.eq(expr, 1);
    }

    private IntExpression buildModExpr(IntExpression a, IntExpression b) {
        if (b instanceof Constant c && c.v() != 0 && a.size() <= 10_000) {
            int k = c.v();
            int[] dom = new int[a.size()];
            int sz = a.fillArray(dom);
            int[][] tuples = new int[sz][2];
            int minR = Integer.MAX_VALUE;
            int maxR = Integer.MIN_VALUE;
            for (int i = 0; i < sz; i++) {
                int r = dom[i] % k;
                tuples[i][0] = dom[i];
                tuples[i][1] = r;
                minR = Math.min(minR, r);
                maxR = Math.max(maxR, r);
            }
            IntVar rVar = md.intVar(minR, maxR);
            md.add(new Table(new IntExpression[]{a, rVar}, tuples));
            return rVar;
        }
        return Factory.minus(a, Factory.mul(Factory.div(a, b), b));
    }

    private IntExpression buildPowExpr(IntExpression a, IntExpression b) {
        if (b instanceof Constant c) {
            int exp = c.v();
            if (exp == 0) return md.constant(1);
            if (exp == 1) return a;
            if (exp == 2) return buildSqrExpr(a);
            if (exp > 2 && a.size() <= 10_000) {
                int[] dom = new int[a.size()];
                int sz = a.fillArray(dom);
                int[][] tuples = new int[sz][2];
                int minP = Integer.MAX_VALUE;
                int maxP = Integer.MIN_VALUE;
                for (int i = 0; i < sz; i++) {
                    long p = 1L;
                    for (int e = 0; e < exp; e++) p *= dom[i];
                    int pv = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, p));
                    tuples[i][0] = dom[i];
                    tuples[i][1] = pv;
                    minP = Math.min(minP, pv);
                    maxP = Math.max(maxP, pv);
                }
                IntVar pVar = md.intVar(minP, maxP);
                md.add(new Table(new IntExpression[]{a, pVar}, tuples));
                return pVar;
            }
            if (exp > 2 && exp <= 12) {
                IntExpression[] factors = new IntExpression[exp];
                Arrays.fill(factors, a);
                return new Mul(factors);
            }
        }
        if ((long) a.size() * b.size() <= 20_000L) {
            int[] domA = new int[a.size()];
            int szA = a.fillArray(domA);
            int[] domB = new int[b.size()];
            int szB = b.fillArray(domB);
            List<int[]> tupleList = new ArrayList<>(szA * szB);
            int minP = Integer.MAX_VALUE;
            int maxP = Integer.MIN_VALUE;
            for (int i = 0; i < szA; i++) {
                for (int j = 0; j < szB; j++) {
                    if (domB[j] < 0) continue;
                    double pow = Math.pow(domA[i], domB[j]);
                    if (pow >= Integer.MIN_VALUE && pow <= Integer.MAX_VALUE) {
                        int pv = (int) Math.round(pow);
                        tupleList.add(new int[]{domA[i], domB[j], pv});
                        minP = Math.min(minP, pv);
                        maxP = Math.max(maxP, pv);
                    }
                }
            }
            IntVar pVar = md.intVar(minP, maxP);
            md.add(new Table(new IntExpression[]{a, b, pVar}, tupleList.toArray(int[][]::new)));
            return pVar;
        }
        throw new NotImplementedException("Unsupported POW expression with large variable exponent");
    }

    private IntExpression buildSqrExpr(IntExpression a) {
        if (a.size() <= 10_000 && !(a instanceof IntVar)) {
            IntVar aux = md.intVar(a.min(), a.max());
            md.add(Factory.eq(aux, a));
            return Factory.mul(aux, aux);
        }
        return Factory.mul(a, a);
    }

    private IntExpression applyBinaryArithmetic(IntExpression x, Types.TypeArithmeticOperator aop, IntExpression y) {
        return switch (aop) {
            case DIST -> x.minus(y).abs();
            case ADD -> x.plus(y);
            case SUB -> x.minus(y);
            case MUL -> Factory.mul(x, y);
            case DIV -> Factory.div(x, y);
            case MOD -> buildModExpr(x, y);
            case POW -> buildPowExpr(x, y);
        };
    }

    private void addRelConstraint(IntExpression lhs, Types.TypeConditionOperatorRel op, int rhs) {
        switch (op) {
            case EQ -> md.add(Factory.eq(lhs, rhs));
            case NE -> md.add(Factory.neq(lhs, rhs));
            case GT -> md.add(Factory.gt(lhs, rhs));
            case GE -> md.add(Factory.ge(lhs, rhs));
            case LT -> md.add(Factory.lt(lhs, rhs));
            case LE -> md.add(Factory.le(lhs, rhs));
        }
    }

    private void addRelConstraint(IntExpression lhs, Types.TypeConditionOperatorRel op, IntExpression rhs) {
        switch (op) {
            case EQ -> md.add(Factory.eq(lhs, rhs));
            case NE -> md.add(Factory.neq(lhs, rhs));
            case GT -> md.add(Factory.gt(lhs, rhs));
            case GE -> md.add(Factory.ge(lhs, rhs));
            case LT -> md.add(Factory.lt(lhs, rhs));
            case LE -> md.add(Factory.le(lhs, rhs));
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorRel op, int k) {
        addRelConstraint($(x), op, k);
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop,
            XVariables.XVarInteger y, Types.TypeConditionOperatorRel op, int k) {
        IntExpression $x = $(x);
        IntExpression $y = $(y);

        if (aop == Types.TypeArithmeticOperator.SUB && op == Types.TypeConditionOperatorRel.EQ && k == 0) {
            md.add(Factory.eq($x, $y));
            return;
        }
        if (aop == Types.TypeArithmeticOperator.ADD && op == Types.TypeConditionOperatorRel.EQ && k == 0) {
            md.add(Factory.eq($x, Factory.minus($y)));
            return;
        }
        addRelConstraint(applyBinaryArithmetic($x, aop, $y), op, k);
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop, int p,
            Types.TypeConditionOperatorRel op, XVariables.XVarInteger z) {
        IntExpression $x = $(x);
        IntExpression $z = $(z);
        IntExpression o = switch (aop) {
            case DIST -> $x.minus(p).abs();
            case ADD -> $x.plus(p);
            case SUB -> $x.minus(p);
            case MUL -> Factory.mul($x, p);
            case DIV -> Factory.div($x, p);
            case MOD -> buildModExpr($x, md.constant(p));
            case POW -> buildPowExpr($x, md.constant(p));
        };
        addRelConstraint(o, op, $z);
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop, int p,
            Types.TypeConditionOperatorRel op, int k) {
        IntExpression $x = $(x);
        IntExpression o = switch (aop) {
            case DIST -> $x.minus(p).abs();
            case ADD -> $x.plus(p);
            case SUB -> $x.minus(p);
            case MUL -> Factory.mul($x, p);
            case DIV -> {
                if (p == 0)
                    throw new NotImplementedException("Division by zero in primitive constraint");
                yield Factory.div($x, md.constant(p));
            }
            case MOD -> buildModExpr($x, md.constant(p));
            case POW -> buildPowExpr($x, md.constant(p));
        };
        addRelConstraint(o, op, k);
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeArithmeticOperator aop,
            XVariables.XVarInteger y, Types.TypeConditionOperatorRel op, XVariables.XVarInteger z) {
        addRelConstraint(applyBinaryArithmetic($(x), aop, $(y)), op, $(z));
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeUnaryArithmeticOperator aop,
            XVariables.XVarInteger y) {
        IntExpression $x = $(x);
        IntExpression $y = $(y);
        switch (aop) {
            case ABS -> md.add(Factory.eq($x, Factory.abs($y)));
            case NEG -> md.add(Factory.eq($x, Factory.minus($y)));
            case SQR -> md.add(Factory.eq($x, buildSqrExpr($y)));
            case NOT -> md.add(Factory.eq($x, Factory.not(asBoolExpr($y))));
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorSet op, int[] t) {
        IntExpression $x = $(x);
        switch (op) {
            case IN -> md.add(new InSet($x, Arrays.stream(t).boxed().collect(ImmutableSet.toImmutableSet())));
            case NOTIN -> {
                for (int v : t)
                    md.add(Factory.neq($x, v));
            }
        }
    }

    @Override
    public void buildCtrPrimitive(String id, XVariables.XVarInteger x, Types.TypeConditionOperatorSet op, int min,
            int max) {
        IntExpression $x = $(x);
        switch (op) {
            case IN -> {
                md.add(Factory.le(min, $x));
                md.add(Factory.le($x, max));
            }
            case NOTIN -> md.add(Factory.or(Factory.lt($x, min), Factory.gt($x, max)));
        }
    }

    private BoolExpression buildLogicExpr(Types.TypeLogicalOperator lop, IntExpression[] vars) {
        BoolExpression[] bvars = Arrays.stream(vars).map(this::asBoolExpr).toArray(BoolExpression[]::new);
        return switch (lop) {
            case AND -> Factory.and(bvars);
            case OR -> Factory.or(bvars);
            case IFF -> {
                if (bvars.length == 2) {
                    yield new Eq(bvars[0], bvars[1]);
                }
                BoolExpression[] eqs = new BoolExpression[bvars.length - 1];
                for (int i = 0; i < bvars.length - 1; i++) {
                    eqs[i] = new Eq(bvars[i], bvars[i + 1]);
                }
                yield Factory.and(eqs);
            }
            case XOR -> {
                if (bvars.length == 2) {
                    yield new NotEq(bvars[0], bvars[1]);
                }
                Set<Integer> odds = new HashSet<>();
                for (int v = 1; v <= bvars.length; v += 2) {
                    odds.add(v);
                }
                yield new InSet(new Sum(bvars), odds);
            }
            case IMP -> Factory.or(Factory.not(bvars[0]), bvars[1]);
        };
    }

    @Override
    public void buildCtrLogic(String id, Types.TypeLogicalOperator lop, XVariables.XVarInteger[] vars) {
        addToDV(vars);
        md.add(buildLogicExpr(lop, $(vars)));
    }

    @Override
    public void buildCtrLogic(String id, XVariables.XVarInteger y, Types.TypeEqNeOperator op,
            Types.TypeLogicalOperator lop, XVariables.XVarInteger[] vars) {
        addToDV(y);
        addToDV(vars);
        BoolExpression rhs = buildLogicExpr(lop, $(vars));
        if (op == Types.TypeEqNeOperator.EQ) {
            md.add(Factory.eq($(y), rhs));
        } else {
            md.add(Factory.neq($(y), rhs));
        }
    }

    @Override
    public void buildCtrLogic(String id, XVariables.XVarInteger x, XVariables.XVarInteger y,
            Types.TypeConditionOperatorRel op, int k) {
        addToDV(x, y);
        BoolExpression rel = switch (op) {
            case EQ -> Factory.eq($(y), k);
            case NE -> Factory.neq($(y), k);
            case LT -> Factory.lt($(y), k);
            case LE -> Factory.le($(y), k);
            case GT -> Factory.gt($(y), k);
            case GE -> Factory.ge($(y), k);
        };
        md.add(Factory.eq($(x), rel));
    }

    @Override
    public void buildCtrLogic(String id, XVariables.XVarInteger x, XVariables.XVarInteger y,
            Types.TypeConditionOperatorRel op, XVariables.XVarInteger z) {
        addToDV(x, y, z);
        BoolExpression rel = switch (op) {
            case EQ -> Factory.eq($(y), $(z));
            case NE -> Factory.neq($(y), $(z));
            case LT -> Factory.lt($(y), $(z));
            case LE -> Factory.le($(y), $(z));
            case GT -> Factory.gt($(y), $(z));
            case GE -> Factory.ge($(y), $(z));
        };
        md.add(Factory.eq($(x), rel));
    }

    @Override
    public void buildCtrClause(String id, XVariables.XVarInteger[] pos, XVariables.XVarInteger[] neg) {
        addToDV(pos);
        addToDV(neg);
        BoolExpression[] lits = new BoolExpression[pos.length + neg.length];
        int idx = 0;
        for (XVariables.XVarInteger p : pos) {
            lits[idx++] = asBoolExpr($(p));
        }
        for (XVariables.XVarInteger n : neg) {
            lits[idx++] = Factory.not(asBoolExpr($(n)));
        }
        md.add(Factory.or(lits));
    }

    protected IdentityHashMap<XNode<?>, IntExpression> expr_cache = new IdentityHashMap<>();

    private <V extends IVar> IntExpression _recursiveIntentionBuilder(XNode<V> node) {
        IntExpression val = expr_cache.get(node);
        if (val == null) {
            val = switch (node) {
                case XNodeLeaf<V> leaf -> _recursiveIntentionBuilderLeafNode(leaf);
                case XNodeParent<V> parent -> _recursiveIntentionBuilderParentNode(parent);
                default -> throw new RuntimeException("Unknown node type");
            };
            expr_cache.put(node, val);
        }
        return val;
    }

    private <V extends IVar> IntExpression _recursiveIntentionBuilderLeafNode(XNodeLeaf<V> node) {
        return switch (node.getType()) {
            case VAR -> varHashMap.get(node.value.toString());
            case LONG -> md.constant(((Long) node.value).intValue());
            case SYMBOL -> md.constant(symbolicValues.get(node.value.toString()));
            default ->
                throw new NotYetImplementedException(
                        "Unknown node type %s in _recursiveIntentionBuilderLeafNode".formatted(node.getType()));
        };
    }

    private <V extends IVar> BoolExpression buildInExpression(XNodeParent<V> tree) {
        assert (tree.sons[1].getType() == Types.TypeExpr.SET);
        try {
            Set<Integer> set = Arrays.stream(((XNodeParent<V>) tree.sons[1]).sons)
                    .map(i -> ((Long) ((XNodeLeaf<V>) i).value).intValue()).collect(Collectors.toSet());
            return new InSet(_recursiveIntentionBuilder(tree.sons[0]), set);
        } catch (ClassCastException a) {
            IntExpression main = _recursiveIntentionBuilder(tree.sons[0]);
            return new Or(Arrays.stream(tree.sons[1].sons).map(x -> new Eq(_recursiveIntentionBuilder(x), main))
                    .collect(ImmutableSet.toImmutableSet()));
        }
    }

    private <V extends IVar> IntExpression _recursiveIntentionBuilderParentNode(XNodeParent<V> tree) {
        return switch (tree.getType()) {
            case IN -> buildInExpression(tree);
            case NOTIN -> Factory.not(buildInExpression(tree));
            case NE -> {
                if (tree.sons.length == 2) {
                    yield new NotEq(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
                }
                IntExpression[] args = Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
                List<BoolExpression> neqs = new ArrayList<>();
                for (int i = 0; i < args.length; i++) {
                    for (int j = i + 1; j < args.length; j++) {
                        neqs.add(new NotEq(args[i], args[j]));
                    }
                }
                yield Factory.and(neqs.toArray(BoolExpression[]::new));
            }
            case EQ -> {
                if (tree.sons.length == 2) {
                    yield new Eq(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
                }
                IntExpression[] args = Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
                BoolExpression[] eqs = new BoolExpression[args.length - 1];
                for (int i = 0; i < args.length - 1; i++) {
                    eqs[i] = new Eq(args[i], args[i + 1]);
                }
                yield Factory.and(eqs);
            }
            case ADD ->
                new Sum(Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case SUB ->
                Factory.minus(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case MUL ->
                new Mul(Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case LT -> Factory.lt(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case LE -> Factory.le(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case GT -> Factory.gt(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case GE -> Factory.ge(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case AND ->
                Factory.and(Arrays.stream(tree.sons).map(x -> asBoolExpr(_recursiveIntentionBuilder(x)))
                        .toArray(BoolExpression[]::new));
            case OR ->
                Factory.or(Arrays.stream(tree.sons).map(x -> asBoolExpr(_recursiveIntentionBuilder(x)))
                        .toArray(BoolExpression[]::new));
            case XOR ->
                buildLogicExpr(Types.TypeLogicalOperator.XOR,
                        Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case DIST ->
                Factory.abs(Factory.minus(_recursiveIntentionBuilder(tree.sons[0]),
                        _recursiveIntentionBuilder(tree.sons[1])));
            case ABS -> Factory.abs(_recursiveIntentionBuilder(tree.sons[0]));
            case SQR -> buildSqrExpr(_recursiveIntentionBuilder(tree.sons[0]));
            case POW -> buildPowExpr(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case MIN ->
                new Min(Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case MAX ->
                new Max(Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case DIV -> {
                IntExpression left = _recursiveIntentionBuilder(tree.sons[0]);
                IntExpression right = _recursiveIntentionBuilder(tree.sons[1]);
                yield Factory.div(left, right);
            }
            case MOD -> buildModExpr(_recursiveIntentionBuilder(tree.sons[0]), _recursiveIntentionBuilder(tree.sons[1]));
            case IMP -> {
                BoolExpression a = asBoolExpr(_recursiveIntentionBuilder(tree.sons[0]));
                BoolExpression b = asBoolExpr(_recursiveIntentionBuilder(tree.sons[1]));
                yield Factory.or(Factory.not(a), b);
            }
            case NOT -> Factory.not(asBoolExpr(_recursiveIntentionBuilder(tree.sons[0])));
            case NEG -> Factory.minus(_recursiveIntentionBuilder(tree.sons[0]));
            case IFF ->
                buildLogicExpr(Types.TypeLogicalOperator.IFF,
                        Arrays.stream(tree.sons).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new));
            case IF -> {
                BoolExpression cond = asBoolExpr(_recursiveIntentionBuilder(tree.sons[0]));
                IntExpression thenExpr = _recursiveIntentionBuilder(tree.sons[1]);
                IntExpression elseExpr = _recursiveIntentionBuilder(tree.sons[2]);
                yield Factory.plus(Factory.mul(thenExpr, cond), Factory.mul(elseExpr, Factory.not(cond)));
            }
            default ->
                throw new NotYetImplementedException(
                        "Unknown type %s in _recursiveIntentionBuilderParentNode".formatted(tree.getType()));
        };
    }

    @Override
    public void buildCtrIntension(String id, XVariables.XVarInteger[] scope,
            XNodeParent<XVariables.XVarInteger> syntaxTreeRoot) {
        addToDV(scope);
        md.add(asBoolExpr(_recursiveIntentionBuilder(syntaxTreeRoot)));
    }

    private void buildCrtWithCondition(String id, IntExpression expr, Condition operator) {
        if (operator == null) return;
        switch (operator) {
            case Condition.ConditionVal cv -> {
                int cst = (int) cv.k;
                addRelConstraint(expr, cv.operator, cst);
            }
            case Condition.ConditionVar cv -> {
                if (cv.x instanceof XVariables.XVarInteger xvi) {
                    addToDV(xvi);
                }
                IntExpression v = $(cv.x);
                addRelConstraint(expr, cv.operator, v);
            }
            case Condition.ConditionIntvl ci -> {
                int min = (int) ci.min;
                int max = (int) ci.max;
                switch (ci.operator) {
                    case IN -> {
                        md.add(Factory.ge(expr, min));
                        md.add(Factory.le(expr, max));
                    }
                    case NOTIN -> md.add(Factory.or(Factory.lt(expr, min), Factory.gt(expr, max)));
                }
            }
            case Condition.ConditionIntset cs -> {
                switch (cs.operator) {
                    case IN -> md.add(new InSet(expr, Arrays.stream(cs.t).boxed().collect(ImmutableSet.toImmutableSet())));
                    case NOTIN -> {
                        for (int v : cs.t) {
                            md.add(Factory.neq(expr, v));
                        }
                    }
                }
            }
            default -> throw new RuntimeException("Unknown operator %s".formatted(operator.getClass()));
        }
    }

    // -------------------------------------------------------------------------
    // AllDifferent, AllEqual, NotAllEqual
    // -------------------------------------------------------------------------

    @Override
    public void buildCtrAllDifferent(String id, XVariables.XVarInteger[] list) {
        addToDV(list);
        md.add(new AllDifferent($(list)));
    }

    @Override
    public void buildCtrAllDifferent(String id, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        md.add(new AllDifferent(exprs));
    }

    @Override
    public void buildCtrAllDifferentExcept(String id, XVariables.XVarInteger[] list, int[] except) {
        addToDV(list);
        if (except == null || except.length == 0) {
            md.add(new AllDifferent($(list)));
            return;
        }
        IntExpression[] vars = $(list);
        Set<Integer> exSet = Arrays.stream(except).boxed().collect(ImmutableSet.toImmutableSet());
        for (int i = 0; i < vars.length; i++) {
            BoolExpression xiInEx = except.length == 1
                    ? Factory.eq(vars[i], except[0])
                    : new InSet(vars[i], exSet);
            for (int j = i + 1; j < vars.length; j++) {
                md.add(Factory.or(xiInEx, Factory.neq(vars[i], vars[j])));
            }
        }
    }

    @Override
    public void buildCtrAllDifferentList(String id, XVariables.XVarInteger[][] lists) {
        for (XVariables.XVarInteger[] list : lists) {
            addToDV(list);
        }
        for (int i = 0; i < lists.length; i++) {
            IntExpression[] t1 = $(lists[i]);
            for (int j = i + 1; j < lists.length; j++) {
                IntExpression[] t2 = $(lists[j]);
                BoolExpression[] neqs = new BoolExpression[t1.length];
                for (int k = 0; k < t1.length; k++) {
                    neqs[k] = Factory.neq(t1[k], t2[k]);
                }
                md.add(Factory.or(neqs));
            }
        }
    }

    @Override
    public void buildCtrAllDifferentList(String id, XVariables.XVarInteger[][] lists, int[][] except) {
        for (XVariables.XVarInteger[] list : lists) {
            addToDV(list);
        }
        if (except == null || except.length == 0) {
            buildCtrAllDifferentList(id, lists);
            return;
        }
        for (int i = 0; i < lists.length; i++) {
            IntExpression[] t1 = $(lists[i]);
            BoolExpression[] t1InEx = new BoolExpression[except.length];
            for (int e = 0; e < except.length; e++) {
                int[] exTuple = except[e];
                BoolExpression[] eqs = new BoolExpression[t1.length];
                for (int k = 0; k < t1.length; k++) {
                    eqs[k] = Factory.eq(t1[k], exTuple[k]);
                }
                t1InEx[e] = Factory.and(eqs);
            }
            for (int j = i + 1; j < lists.length; j++) {
                IntExpression[] t2 = $(lists[j]);
                BoolExpression[] disj = new BoolExpression[t1.length + except.length];
                for (int k = 0; k < t1.length; k++) {
                    disj[k] = Factory.neq(t1[k], t2[k]);
                }
                System.arraycopy(t1InEx, 0, disj, t1.length, except.length);
                md.add(Factory.or(disj));
            }
        }
    }

    @Override
    public void buildCtrAllDifferentMatrix(String id, XVariables.XVarInteger[][] matrix, int[] except) {
        for (XVariables.XVarInteger[] row : matrix) {
            buildCtrAllDifferentExcept(id, row, except);
        }
        int nCols = matrix[0].length;
        for (int c = 0; c < nCols; c++) {
            final int col = c;
            XVariables.XVarInteger[] colVars = Arrays.stream(matrix).map(r -> r[col]).toArray(XVariables.XVarInteger[]::new);
            buildCtrAllDifferentExcept(id, colVars, except);
        }
    }

    @Override
    public void buildCtrAllEqual(String id, XVariables.XVarInteger[] list) {
        addToDV(list);
        IntExpression[] vars = $(list);
        for (int i = 0; i < vars.length - 1; i++) {
            md.add(Factory.eq(vars[i], vars[i + 1]));
        }
    }

    @Override
    public void buildCtrAllEqual(String id, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        for (int i = 0; i < exprs.length - 1; i++) {
            md.add(Factory.eq(exprs[i], exprs[i + 1]));
        }
    }

    @Override
    public void buildCtrNotAllEqual(String id, XVariables.XVarInteger[] list) {
        addToDV(list);
        IntExpression[] vars = $(list);
        BoolExpression[] neqs = new BoolExpression[vars.length - 1];
        for (int i = 0; i < vars.length - 1; i++) {
            neqs[i] = Factory.neq(vars[i], vars[i + 1]);
        }
        md.add(Factory.or(neqs));
    }

    // -------------------------------------------------------------------------
    // Ordered, Precedence, Lex
    // -------------------------------------------------------------------------

    private BiFunction<IntExpression, IntExpression, BoolExpression> getRelOpFunc(Types.TypeOperatorRel operator) {
        return switch (operator) {
            case GE -> Factory::ge;
            case GT -> Factory::gt;
            case LT -> Factory::lt;
            case LE -> Factory::le;
        };
    }

    @Override
    public void buildCtrOrdered(String id, XVariables.XVarInteger[] list, Types.TypeOperatorRel operator) {
        addToDV(list);
        BiFunction<IntExpression, IntExpression, BoolExpression> op = getRelOpFunc(operator);
        IntExpression[] $list = $(list);
        for (int i = 0; i < $list.length - 1; i++)
            md.add(op.apply($list[i], $list[i + 1]));
    }

    @Override
    public void buildCtrOrdered(String id, XVariables.XVarInteger[] list, int[] lengths, Types.TypeOperatorRel operator) {
        addToDV(list);
        BiFunction<IntExpression, IntExpression, BoolExpression> op = getRelOpFunc(operator);
        IntExpression[] $list = $(list);
        for (int i = 0; i < $list.length - 1; i++)
            md.add(op.apply(Factory.plus($list[i], lengths[i]), $list[i + 1]));
    }

    @Override
    public void buildCtrOrdered(String id, XVariables.XVarInteger[] list, XVariables.XVarInteger[] lengths,
            Types.TypeOperatorRel operator) {
        addToDV(list);
        addToDV(lengths);
        BiFunction<IntExpression, IntExpression, BoolExpression> op = getRelOpFunc(operator);
        IntExpression[] $list = $(list);
        IntExpression[] $lengths = $(lengths);
        for (int i = 0; i < $list.length - 1; i++)
            md.add(op.apply(Factory.plus($list[i], $lengths[i]), $list[i + 1]));
    }

    @Override
    public void buildCtrPrecedence(String id, XVariables.XVarInteger[] list) {
        addToDV(list);
        IntExpression[] vars = $(list);
        TreeSet<Integer> allVals = new TreeSet<>();
        for (IntExpression v : vars) {
            int[] dom = new int[v.size()];
            int sz = v.fillArray(dom);
            for (int i = 0; i < sz; i++) allVals.add(dom[i]);
        }
        int[] values = allVals.stream().mapToInt(Integer::intValue).toArray();
        buildCtrPrecedence(id, list, values, false);
    }

    @Override
    public void buildCtrPrecedence(String id, XVariables.XVarInteger[] list, int[] values, boolean covered) {
        addToDV(list);
        IntExpression[] x = $(list);
        int n = x.length;
        int m = values.length;
        if (m == 0 || n == 0) return;

        BoolExpression[][] seen = new BoolExpression[m][n];
        for (int k = 0; k < m; k++) {
            seen[k][0] = Factory.eq(x[0], values[k]);
            for (int j = 1; j < n; j++) {
                seen[k][j] = Factory.or(seen[k][j - 1], Factory.eq(x[j], values[k]));
            }
        }
        for (int k = 0; k < m - 1; k++) {
            md.add(Factory.neq(x[0], values[k + 1]));
            for (int j = 1; j < n; j++) {
                md.add(Factory.or(Factory.neq(x[j], values[k + 1]), seen[k][j - 1]));
            }
        }
        if (covered) {
            for (int k = 0; k < m; k++) {
                md.add(seen[k][n - 1]);
            }
        }
    }

    private void addLexPair(IntExpression[] a, IntExpression[] b, Types.TypeOperatorRel operator) {
        switch (operator) {
            case LE -> md.add(Factory.lexLe(a, b));
            case LT -> md.add(Factory.lexLt(a, b));
            case GE -> md.add(Factory.lexLe(b, a));
            case GT -> md.add(Factory.lexLt(b, a));
        }
    }

    @Override
    public void buildCtrLex(String id, XVariables.XVarInteger[] list, int[] limit, Types.TypeOperatorRel operator) {
        addToDV(list);
        IntExpression[] a = $(list);
        IntExpression[] b = Arrays.stream(limit).mapToObj(md::constant).toArray(IntExpression[]::new);
        addLexPair(a, b, operator);
    }

    @Override
    public void buildCtrLex(String id, XVariables.XVarInteger[][] lists, Types.TypeOperatorRel operator) {
        for (XVariables.XVarInteger[] list : lists) {
            addToDV(list);
        }
        for (int i = 0; i < lists.length - 1; i++) {
            addLexPair($(lists[i]), $(lists[i + 1]), operator);
        }
    }

    // -------------------------------------------------------------------------
    // Sum
    // -------------------------------------------------------------------------

    @Override
    public void buildCtrSum(String id, XVariables.XVarInteger[] list, Condition condition) {
        addToDV(list);
        buildCrtWithCondition(id, new Sum($(list)), condition);
    }

    @Override
    public void buildCtrSum(String id, XVariables.XVarInteger[] list, int[] coeffs, Condition condition) {
        addToDV(list);
        buildCrtWithCondition(id, new WeightedSum($(list), coeffs), condition);
    }

    @Override
    public void buildCtrSum(String id, XVariables.XVarInteger[] list, XVariables.XVarInteger[] coeffs,
            Condition condition) {
        addToDV(list);
        addToDV(coeffs);
        IntExpression[] terms = IntStream.range(0, list.length)
                .mapToObj(i -> Factory.mul($(list[i]), $(coeffs[i])))
                .toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new Sum(terms), condition);
    }

    @Override
    public void buildCtrSum(String id, XNode<XVariables.XVarInteger>[] trees, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new Sum(exprs), condition);
    }

    @Override
    public void buildCtrSum(String id, XNode<XVariables.XVarInteger>[] trees, int[] coeffs, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new WeightedSum(exprs, coeffs), condition);
    }

    @Override
    public void buildCtrSum(String id, XNode<XVariables.XVarInteger>[] trees, XVariables.XVarInteger[] coeffs,
            Condition condition) {
        addToDV(coeffs);
        IntExpression[] terms = IntStream.range(0, trees.length)
                .mapToObj(i -> Factory.mul(_recursiveIntentionBuilder(trees[i]), $(coeffs[i])))
                .toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new Sum(terms), condition);
    }

    // -------------------------------------------------------------------------
    // Count, AtLeast, AtMost, Exactly, Among, NValues, Cardinality
    // -------------------------------------------------------------------------

    private void buildCountForExpressions(String id, IntExpression[] exprs, int[] values, Condition condition) {
        if (values.length == 1) {
            int val = values[0];
            IntExpression[] eqs = Arrays.stream(exprs).map(x -> Factory.eq(x, val)).toArray(IntExpression[]::new);
            buildCrtWithCondition(id, new Sum(eqs), condition);
        } else {
            Set<Integer> valSet = Arrays.stream(values).boxed().collect(ImmutableSet.toImmutableSet());
            IntVar countVar = md.intVar(0, exprs.length);
            md.add(new Among(exprs, valSet, countVar));
            IntExpression[] inSets = Arrays.stream(exprs).map(x -> new InSet(x, valSet)).toArray(IntExpression[]::new);
            md.add(Factory.eq(new Sum(inSets), countVar));
            buildCrtWithCondition(id, countVar, condition);
        }
    }

    @Override
    public void buildCtrCount(String id, XVariables.XVarInteger[] list, int[] values, Condition condition) {
        addToDV(list);
        buildCountForExpressions(id, $(list), values, condition);
    }

    @Override
    public void buildCtrCount(String id, XNode<XVariables.XVarInteger>[] trees, int[] values, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildCountForExpressions(id, exprs, values, condition);
    }

    @Override
    public void buildCtrCount(String id, XVariables.XVarInteger[] list, XVariables.XVarInteger[] values,
            Condition condition) {
        addToDV(list);
        addToDV(values);
        IntExpression[] $list = $(list);
        IntExpression[] $values = $(values);
        if ($values.length == 1) {
            IntExpression v = $values[0];
            IntExpression[] eqs = Arrays.stream($list).map(x -> Factory.eq(x, v)).toArray(IntExpression[]::new);
            buildCrtWithCondition(id, new Sum(eqs), condition);
        } else {
            IntExpression[] indicators = Arrays.stream($list)
                    .map(x -> Factory.or(Arrays.stream($values).map(v -> Factory.eq(x, v)).toArray(BoolExpression[]::new)))
                    .toArray(IntExpression[]::new);
            buildCrtWithCondition(id, new Sum(indicators), condition);
        }
    }

    @Override
    public void buildCtrAtLeast(String id, XVariables.XVarInteger[] list, int value, int k) {
        addToDV(list);
        IntExpression[] eqs = Arrays.stream($(list)).map(x -> Factory.eq(x, value)).toArray(IntExpression[]::new);
        md.add(Factory.ge(new Sum(eqs), k));
    }

    @Override
    public void buildCtrAtMost(String id, XVariables.XVarInteger[] list, int value, int k) {
        addToDV(list);
        IntExpression[] eqs = Arrays.stream($(list)).map(x -> Factory.eq(x, value)).toArray(IntExpression[]::new);
        md.add(Factory.le(new Sum(eqs), k));
    }

    @Override
    public void buildCtrExactly(String id, XVariables.XVarInteger[] list, int value, int k) {
        addToDV(list);
        IntExpression[] eqs = Arrays.stream($(list)).map(x -> Factory.eq(x, value)).toArray(IntExpression[]::new);
        md.add(Factory.eq(new Sum(eqs), k));
    }

    @Override
    public void buildCtrExactly(String id, XVariables.XVarInteger[] list, int value, XVariables.XVarInteger k) {
        addToDV(list);
        addToDV(k);
        IntExpression[] eqs = Arrays.stream($(list)).map(x -> Factory.eq(x, value)).toArray(IntExpression[]::new);
        md.add(Factory.eq(new Sum(eqs), $(k)));
    }

    @Override
    public void buildCtrAmong(String id, XVariables.XVarInteger[] list, int[] values, int k) {
        addToDV(list);
        Set<Integer> valSet = Arrays.stream(values).boxed().collect(ImmutableSet.toImmutableSet());
        IntExpression[] $list = $(list);
        md.add(new Among($list, valSet, md.constant(k)));
        IntExpression[] inSets = Arrays.stream($list).map(x -> new InSet(x, valSet)).toArray(IntExpression[]::new);
        md.add(Factory.eq(new Sum(inSets), k));
    }

    @Override
    public void buildCtrAmong(String id, XVariables.XVarInteger[] list, int[] values, XVariables.XVarInteger k) {
        addToDV(list);
        addToDV(k);
        Set<Integer> valSet = Arrays.stream(values).boxed().collect(ImmutableSet.toImmutableSet());
        IntExpression[] $list = $(list);
        md.add(new Among($list, valSet, $(k)));
        IntExpression[] inSets = Arrays.stream($list).map(x -> new InSet(x, valSet)).toArray(IntExpression[]::new);
        md.add(Factory.eq(new Sum(inSets), $(k)));
    }

    private IntExpression buildNValuesExpr(IntExpression[] exprs, Set<Integer> except) {
        TreeSet<Integer> candidateVals = new TreeSet<>();
        for (IntExpression e : exprs) {
            int[] dom = new int[e.size()];
            int sz = e.fillArray(dom);
            for (int i = 0; i < sz; i++) {
                if (!except.contains(dom[i])) {
                    candidateVals.add(dom[i]);
                }
            }
        }
        if (candidateVals.isEmpty()) {
            return md.constant(0);
        }
        List<IntExpression> presentIndicators = new ArrayList<>(candidateVals.size());
        for (int v : candidateVals) {
            List<BoolExpression> eqs = new ArrayList<>();
            for (IntExpression e : exprs) {
                if (e.contains(v)) {
                    eqs.add(Factory.eq(e, v));
                }
            }
            if (eqs.size() == 1) {
                presentIndicators.add(eqs.get(0));
            } else if (eqs.size() > 1) {
                presentIndicators.add(Factory.or(eqs.toArray(BoolExpression[]::new)));
            }
        }
        return new Sum(presentIndicators.toArray(IntExpression[]::new));
    }

    private void buildNValuesWithCondition(String id, IntExpression[] exprs, Set<Integer> except, Condition condition) {
        if (except.isEmpty() && condition instanceof Condition.ConditionVal cv) {
            int k = (int) cv.k;
            if ((cv.operator == Types.TypeConditionOperatorRel.EQ && k == exprs.length)
                    || (cv.operator == Types.TypeConditionOperatorRel.GE && k == exprs.length)
                    || (cv.operator == Types.TypeConditionOperatorRel.GT && k == exprs.length - 1)) {
                md.add(new AllDifferent(exprs));
                return;
            }
            if (cv.operator == Types.TypeConditionOperatorRel.NE && k == 1) {
                BoolExpression[] neqs = new BoolExpression[exprs.length - 1];
                for (int i = 0; i < exprs.length - 1; i++) {
                    neqs[i] = Factory.neq(exprs[i], exprs[i + 1]);
                }
                md.add(Factory.or(neqs));
                return;
            }
        }
        IntExpression nValExpr = buildNValuesExpr(exprs, except);
        buildCrtWithCondition(id, nValExpr, condition);
    }

    @Override
    public void buildCtrNValues(String id, XVariables.XVarInteger[] list, Condition condition) {
        addToDV(list);
        buildNValuesWithCondition(id, $(list), Collections.emptySet(), condition);
    }

    @Override
    public void buildCtrNValuesExcept(String id, XVariables.XVarInteger[] list, int[] except, Condition condition) {
        addToDV(list);
        Set<Integer> exSet = Arrays.stream(except).boxed().collect(Collectors.toSet());
        buildNValuesWithCondition(id, $(list), exSet, condition);
    }

    @Override
    public void buildCtrNValues(String id, XNode<XVariables.XVarInteger>[] trees, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildNValuesWithCondition(id, exprs, Collections.emptySet(), condition);
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values,
            XVariables.XVarInteger[] occurs) {
        addToDV(list);
        addToDV(occurs);
        IntExpression[] $list = $(list);
        IntExpression[] $occurs = $(occurs);
        if (closed) {
            Set<Integer> valSet = Arrays.stream(values).boxed().collect(ImmutableSet.toImmutableSet());
            for (IntExpression x : $list) {
                md.add(new InSet(x, valSet));
            }
            md.add(Factory.eq(new Sum($occurs), $list.length));
        }
        for (int k = 0; k < values.length; k++) {
            int val = values[k];
            IntExpression[] eqs = Arrays.stream($list).map(x -> Factory.eq(x, val)).toArray(IntExpression[]::new);
            md.add(Factory.eq(new Sum(eqs), $occurs[k]));
        }
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values,
            int[] occurs) {
        buildCtrCardinality(id, list, closed, values, occurs, occurs);
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed, int[] values,
            int[] occursMin, int[] occursMax) {
        addToDV(list);
        IntExpression[] $list = $(list);
        if (closed) {
            Set<Integer> valSet = Arrays.stream(values).boxed().collect(ImmutableSet.toImmutableSet());
            for (IntExpression x : $list) {
                md.add(new InSet(x, valSet));
            }
        }
        int minVal = Arrays.stream(values).min().orElse(0);
        int maxVal = Arrays.stream(values).max().orElse(-1);
        for (IntExpression x : $list) {
            maxVal = Math.max(maxVal, x.max());
        }
        if (minVal >= 0 && maxVal >= 0 && maxVal <= 2000) {
            int[] minCard = new int[maxVal + 1];
            int[] maxCard = new int[maxVal + 1];
            Arrays.fill(maxCard, closed ? 0 : $list.length);
            for (int k = 0; k < values.length; k++) {
                int v = values[k];
                minCard[v] = occursMin[k];
                maxCard[v] = occursMax[k];
            }
            md.add(new CardinalityMin($list, minCard));
            md.add(new CardinalityMax($list, maxCard));
        }
        for (int k = 0; k < values.length; k++) {
            int val = values[k];
            IntExpression[] eqs = Arrays.stream($list).map(x -> Factory.eq(x, val)).toArray(IntExpression[]::new);
            IntExpression sumEqs = new Sum(eqs);
            if (occursMin[k] == occursMax[k]) {
                md.add(Factory.eq(sumEqs, occursMin[k]));
            } else {
                if (occursMin[k] > 0) md.add(Factory.ge(sumEqs, occursMin[k]));
                if (occursMax[k] < $list.length) md.add(Factory.le(sumEqs, occursMax[k]));
            }
        }
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed,
            XVariables.XVarInteger[] values, XVariables.XVarInteger[] occurs) {
        addToDV(list);
        addToDV(values);
        addToDV(occurs);
        IntExpression[] $list = $(list);
        IntExpression[] $values = $(values);
        IntExpression[] $occurs = $(occurs);
        if (closed) {
            for (IntExpression x : $list) {
                md.add(Factory.or(Arrays.stream($values).map(v -> Factory.eq(x, v)).toArray(BoolExpression[]::new)));
            }
            md.add(Factory.eq(new Sum($occurs), $list.length));
        }
        for (int k = 0; k < $values.length; k++) {
            IntExpression val = $values[k];
            IntExpression[] eqs = Arrays.stream($list).map(x -> Factory.eq(x, val)).toArray(IntExpression[]::new);
            md.add(Factory.eq(new Sum(eqs), $occurs[k]));
        }
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed,
            XVariables.XVarInteger[] values, int[] occurs) {
        buildCtrCardinality(id, list, closed, values, occurs, occurs);
    }

    @Override
    public void buildCtrCardinality(String id, XVariables.XVarInteger[] list, boolean closed,
            XVariables.XVarInteger[] values, int[] occursMin, int[] occursMax) {
        addToDV(list);
        addToDV(values);
        IntExpression[] $list = $(list);
        IntExpression[] $values = $(values);
        if (closed) {
            for (IntExpression x : $list) {
                md.add(Factory.or(Arrays.stream($values).map(v -> Factory.eq(x, v)).toArray(BoolExpression[]::new)));
            }
        }
        for (int k = 0; k < $values.length; k++) {
            IntExpression val = $values[k];
            IntExpression[] eqs = Arrays.stream($list).map(x -> Factory.eq(x, val)).toArray(IntExpression[]::new);
            IntExpression sumEqs = new Sum(eqs);
            if (occursMin[k] == occursMax[k]) {
                md.add(Factory.eq(sumEqs, occursMin[k]));
            } else {
                if (occursMin[k] > 0) md.add(Factory.ge(sumEqs, occursMin[k]));
                if (occursMax[k] < $list.length) md.add(Factory.le(sumEqs, occursMax[k]));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Extension, Regular, MDD, Stretch, Instantiation
    // -------------------------------------------------------------------------

    @Override
    public void buildCtrExtension(String id, XVariables.XVarInteger x, int[] values, boolean positive,
            Set<Types.TypeFlag> flags) {
        addToDV(x);
        if (positive) {
            md.add(new InSet(varHashMap.get(x.id()), ImmutableSet.of(values)));
        } else {
            IntExpression expr = varHashMap.get(x.id());
            for (int v : values)
                md.add(new NotEq(expr, v));
        }
    }

    @Override
    public void buildCtrExtension(String id, XVariables.XVarInteger[] list, int[][] tuples, boolean positive,
            Set<Types.TypeFlag> flags) {
        addToDV(list);
        IntExpression[] vars = $(list);
        boolean starred = flags.contains(Types.TypeFlag.STARRED_TUPLES);
        if (positive) {
            if (starred && tryDecomposeConditionalEquality(vars, tuples, Constants.STAR_INT)) {
                return;
            }
            md.add(new Table(vars, tuples,
                    starred ? Optional.of(Constants.STAR_INT)
                            : Optional.empty()));
        } else {
            if (starred) {
                int star = Constants.STAR_INT;
                for (int[] tuple : tuples) {
                    List<BoolExpression> neqs = new ArrayList<>(vars.length);
                    for (int j = 0; j < vars.length; j++) {
                        if (tuple[j] != star) {
                            neqs.add(Factory.neq(vars[j], tuple[j]));
                        }
                    }
                    if (!neqs.isEmpty()) {
                        md.add(Factory.or(neqs.toArray(BoolExpression[]::new)));
                    }
                }
            } else {
                md.add(new NegTable(vars, tuples, Optional.empty()));
            }
        }
    }

    private boolean tryDecomposeConditionalEquality(IntExpression[] vars, int[][] tuples, int star) {
        if (vars.length != 3 || tuples.length <= 512) {
            return false;
        }
        IntExpression x0 = vars[0];
        IntExpression x1 = vars[1];
        IntExpression x2 = vars[2];
        int min1 = x1.min();
        int max1 = x1.max();
        int size1 = x1.size();
        if (min1 != x2.min() || max1 != x2.max() || size1 != x2.size() || max1 - min1 + 1 != size1) {
            return false;
        }
        Set<Integer> starSet = new HashSet<>();
        Map<Integer, Integer> eqCounts = new HashMap<>();
        for (int[] t : tuples) {
            if (t.length != 3 || t[0] == star) {
                return false;
            }
            if (t[1] == star && t[2] == star) {
                starSet.add(t[0]);
            } else if (t[1] != star && t[1] == t[2] && t[1] >= min1 && t[1] <= max1) {
                eqCounts.merge(t[0], 1, Integer::sum);
            } else {
                return false;
            }
        }
        if (eqCounts.isEmpty()) {
            return false;
        }
        for (Map.Entry<Integer, Integer> e : eqCounts.entrySet()) {
            if (starSet.contains(e.getKey()) || e.getValue() < size1) {
                return false;
            }
        }
        Set<Integer> allowed0 = new HashSet<>(starSet);
        allowed0.addAll(eqCounts.keySet());
        int validDom0 = 0;
        for (int v : allowed0) {
            if (x0.contains(v)) {
                validDom0++;
            }
        }
        if (validDom0 < x0.size()) {
            md.add(new InSet(x0, allowed0));
        }
        if (starSet.isEmpty()) {
            md.add(Factory.eq(x1, x2));
            return true;
        }
        int minStar = Integer.MAX_VALUE, maxStar = Integer.MIN_VALUE;
        for (int v : starSet) {
            minStar = Math.min(minStar, v);
            maxStar = Math.max(maxStar, v);
        }
        int minEq = Integer.MAX_VALUE, maxEq = Integer.MIN_VALUE;
        for (int v : eqCounts.keySet()) {
            minEq = Math.min(minEq, v);
            maxEq = Math.max(maxEq, v);
        }
        BoolExpression starCond;
        if (minStar > maxEq && maxStar - minStar + 1 == starSet.size()) {
            starCond = Factory.ge(x0, minStar);
        } else if (maxStar < minEq && maxStar - minStar + 1 == starSet.size()) {
            starCond = Factory.le(x0, maxStar);
        } else {
            starCond = new InSet(x0, starSet);
        }
        md.add(Factory.or(starCond, Factory.eq(x1, x2)));
        return true;
    }

    private int transitionIntValue(Object val) {
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String s && symbolicValues.containsKey(s)) {
            return symbolicValues.get(s);
        }
        return Integer.parseInt(val.toString());
    }

    @Override
    public void buildCtrRegular(String id, XVariables.XVarInteger[] list, Transition[] transitions, String startState,
            String[] finalStates) {
        addToDV(list);
        IntExpression[] x = $(list);
        int n = x.length;
        Map<String, Integer> stateMap = new LinkedHashMap<>();
        stateMap.put(startState, 0);
        for (String fs : finalStates) {
            stateMap.putIfAbsent(fs, stateMap.size());
        }
        for (Transition tr : transitions) {
            stateMap.putIfAbsent(tr.start, stateMap.size());
            stateMap.putIfAbsent(tr.end, stateMap.size());
        }
        int nStates = stateMap.size();
        int[][] tuples = new int[transitions.length][3];
        for (int i = 0; i < transitions.length; i++) {
            Transition tr = transitions[i];
            tuples[i][0] = stateMap.get(tr.start);
            tuples[i][1] = transitionIntValue(tr.value);
            tuples[i][2] = stateMap.get(tr.end);
        }
        int[] finalIds = Arrays.stream(finalStates).mapToInt(stateMap::get).distinct().toArray();
        IntExpression[] q = new IntExpression[n + 1];
        q[0] = md.constant(stateMap.get(startState));
        for (int i = 1; i < n; i++) {
            q[i] = md.intVar(0, nStates - 1);
        }
        q[n] = md.intVar(finalIds);
        for (int i = 0; i < n; i++) {
            md.add(new Table(new IntExpression[]{q[i], x[i], q[i + 1]}, tuples));
        }
    }

    @Override
    public void buildCtrMDD(String id, XVariables.XVarInteger[] list, Transition[] transitions) {
        addToDV(list);
        Map<String, Integer> stateMap = new LinkedHashMap<>();
        Set<String> hasIncoming = new HashSet<>();
        Set<String> hasOutgoing = new HashSet<>();
        for (Transition tr : transitions) {
            stateMap.putIfAbsent(tr.start, stateMap.size());
            stateMap.putIfAbsent(tr.end, stateMap.size());
            hasOutgoing.add(tr.start);
            hasIncoming.add(tr.end);
        }
        String root = stateMap.keySet().stream().filter(s -> !hasIncoming.contains(s)).findFirst()
                .orElse(transitions[0].start);
        String[] sinks = stateMap.keySet().stream().filter(s -> !hasOutgoing.contains(s)).toArray(String[]::new);
        buildCtrRegular(id, list, transitions, root, sinks);
    }

    @Override
    public void buildCtrStretch(String id, XVariables.XVarInteger[] list, int[] values, int[] widthsMin,
            int[] widthsMax) {
        buildCtrStretch(id, list, values, widthsMin, widthsMax, null);
    }

    @Override
    public void buildCtrStretch(String id, XVariables.XVarInteger[] list, int[] values, int[] widthsMin,
            int[] widthsMax, int[][] patterns) {
        addToDV(list);
        int m = values.length;
        Set<Long> allowedTransitions = null;
        if (patterns != null) {
            allowedTransitions = new HashSet<>();
            for (int[] p : patterns) {
                allowedTransitions.add((((long) p[0]) << 32) | (p[1] & 0xffffffffL));
            }
        }
        String startState = "START";
        List<Transition> transitions = new ArrayList<>();
        List<String> finalStates = new ArrayList<>();
        for (int k = 0; k < m; k++) {
            int v = values[k];
            int wMin = Math.max(1, widthsMin[k]);
            int wMax = Math.min(list.length, widthsMax[k]);
            if (wMin > wMax) continue;
            transitions.add(new Transition(startState, (long) v, "S_" + k + "_1"));
            for (int l = 1; l <= wMax; l++) {
                String cur = "S_" + k + "_" + l;
                if (l < wMax) {
                    transitions.add(new Transition(cur, (long) v, "S_" + k + "_" + (l + 1)));
                }
                if (l >= wMin) {
                    finalStates.add(cur);
                    for (int k2 = 0; k2 < m; k2++) {
                        if (k2 == k) continue;
                        int v2 = values[k2];
                        if (allowedTransitions != null) {
                            long key = (((long) v) << 32) | (v2 & 0xffffffffL);
                            if (!allowedTransitions.contains(key)) continue;
                        }
                        if (widthsMin[k2] <= Math.min(list.length, widthsMax[k2])) {
                            transitions.add(new Transition(cur, (long) v2, "S_" + k2 + "_1"));
                        }
                    }
                }
            }
        }
        buildCtrRegular(id, list, transitions.toArray(Transition[]::new), startState,
                finalStates.toArray(String[]::new));
    }

    @Override
    public void buildCtrInstantiation(String id, XVariables.XVarInteger[] list, int[] values) {
        addToDV(list);
        for (int i = 0; i < list.length; i++)
            md.add(new Eq($(list[i]), values[i]));
    }

    // -------------------------------------------------------------------------
    // Minimum, Maximum, MinimumArg, MaximumArg, Element, Channel
    // -------------------------------------------------------------------------

    @Override
    public void buildCtrMinimum(String id, XVariables.XVarInteger[] list, Condition condition) {
        addToDV(list);
        buildCrtWithCondition(id, new Min($(list)), condition);
    }

    @Override
    public void buildCtrMinimum(String id, XNode<XVariables.XVarInteger>[] trees, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new Min(exprs), condition);
    }

    @Override
    public void buildCtrMaximum(String id, XVariables.XVarInteger[] list, Condition condition) {
        addToDV(list);
        buildCrtWithCondition(id, new Max($(list)), condition);
    }

    @Override
    public void buildCtrMaximum(String id, XNode<XVariables.XVarInteger>[] trees, Condition condition) {
        IntExpression[] exprs = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        buildCrtWithCondition(id, new Max(exprs), condition);
    }

    private void enforceExtremeIndex(IntExpression[] array, IntExpression extremeExpr, IntExpression idx0,
            Types.TypeRank rank, boolean isMax) {
        md.add(Factory.ge(idx0, 0));
        md.add(Factory.lt(idx0, array.length));
        md.add(Factory.eq(Factory.get(array, idx0), extremeExpr));
        if (rank == Types.TypeRank.FIRST) {
            for (int j = 0; j < array.length; j++) {
                BoolExpression strict = isMax ? Factory.lt(array[j], extremeExpr) : Factory.gt(array[j], extremeExpr);
                md.add(Factory.or(Factory.le(idx0, j), strict));
            }
        } else if (rank == Types.TypeRank.LAST) {
            for (int j = 0; j < array.length; j++) {
                BoolExpression strict = isMax ? Factory.lt(array[j], extremeExpr) : Factory.gt(array[j], extremeExpr);
                md.add(Factory.or(Factory.ge(idx0, j), strict));
            }
        }
    }

    @Override
    public void buildCtrMaximum(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger index,
            Types.TypeRank rank, Condition condition) {
        addToDV(list);
        addToDV(index);
        IntExpression[] array = $(list);
        IntExpression maxExpr = new Max(array);
        buildCrtWithCondition(id, maxExpr, condition);
        IntExpression idx0 = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        enforceExtremeIndex(array, maxExpr, idx0, rank, true);
    }

    @Override
    public void buildCtrMinimum(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger index,
            Types.TypeRank rank, Condition condition) {
        addToDV(list);
        addToDV(index);
        IntExpression[] array = $(list);
        IntExpression minExpr = new Min(array);
        buildCrtWithCondition(id, minExpr, condition);
        IntExpression idx0 = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        enforceExtremeIndex(array, minExpr, idx0, rank, false);
    }

    @Override
    public void buildCtrMaximumArg(String id, XVariables.XVarInteger[] list, Types.TypeRank rank, Condition condition) {
        addToDV(list);
        IntExpression[] array = $(list);
        IntExpression maxExpr = new Max(array);
        IntVar idx0 = md.intVar(0, array.length - 1);
        enforceExtremeIndex(array, maxExpr, idx0, rank, true);
        buildCrtWithCondition(id, idx0, condition);
    }

    @Override
    public void buildCtrMaximumArg(String id, XNode<XVariables.XVarInteger>[] trees, Types.TypeRank rank,
            Condition condition) {
        IntExpression[] array = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        IntExpression maxExpr = new Max(array);
        IntVar idx0 = md.intVar(0, array.length - 1);
        enforceExtremeIndex(array, maxExpr, idx0, rank, true);
        buildCrtWithCondition(id, idx0, condition);
    }

    @Override
    public void buildCtrMinimumArg(String id, XVariables.XVarInteger[] list, Types.TypeRank rank, Condition condition) {
        addToDV(list);
        IntExpression[] array = $(list);
        IntExpression minExpr = new Min(array);
        IntVar idx0 = md.intVar(0, array.length - 1);
        enforceExtremeIndex(array, minExpr, idx0, rank, false);
        buildCrtWithCondition(id, idx0, condition);
    }

    @Override
    public void buildCtrMinimumArg(String id, XNode<XVariables.XVarInteger>[] trees, Types.TypeRank rank,
            Condition condition) {
        IntExpression[] array = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        IntExpression minExpr = new Min(array);
        IntVar idx0 = md.intVar(0, array.length - 1);
        enforceExtremeIndex(array, minExpr, idx0, rank, false);
        buildCrtWithCondition(id, idx0, condition);
    }

    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[] list, Condition condition) {
        addToDV(list);
        IntExpression[] array = $(list);
        IntExpression indexExpr = md.intVar(0, array.length - 1);
        IntExpression element = Factory.get(array, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger index,
            Types.TypeRank rank, Condition condition) {
        if (rank != Types.TypeRank.ANY)
            throw new NotImplementedException("Element constraint only supports ANY as position for the index");
        addToDV(list);
        addToDV(index);
        IntExpression[] array = $(list);
        IntExpression indexExpr = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        IntExpression element = Factory.get(array, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, int[] list, int startIndex, XVariables.XVarInteger index,
            Types.TypeRank rank, Condition condition) {
        if (rank != Types.TypeRank.ANY)
            throw new NotImplementedException("Element constraint only supports ANY as position for the index");
        addToDV(index);
        IntExpression indexExpr = startIndex == 0 ? $(index) : Factory.minus($(index), startIndex);
        IntExpression element = Factory.get(list, indexExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, int[][] matrix, int startRowIndex,
            XVariables.XVarInteger rowIndex, int startColIndex, XVariables.XVarInteger colIndex,
            Condition condition) {
        addToDV(rowIndex, colIndex);
        IntExpression rExpr = startRowIndex == 0 ? $(rowIndex) : Factory.minus($(rowIndex), startRowIndex);
        IntExpression cExpr = startColIndex == 0 ? $(colIndex) : Factory.minus($(colIndex), startColIndex);
        IntExpression element = Factory.get(matrix, rExpr, cExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrElement(String id, XVariables.XVarInteger[][] matrix, int startRowIndex,
            XVariables.XVarInteger rowIndex, int startColIndex, XVariables.XVarInteger colIndex,
            Condition condition) {
        for (XVariables.XVarInteger[] row : matrix) {
            addToDV(row);
        }
        addToDV(rowIndex, colIndex);
        IntExpression[][] mat = Arrays.stream(matrix).map(this::$).toArray(IntExpression[][]::new);
        IntExpression rExpr = startRowIndex == 0 ? $(rowIndex) : Factory.minus($(rowIndex), startRowIndex);
        IntExpression cExpr = startColIndex == 0 ? $(colIndex) : Factory.minus($(colIndex), startColIndex);
        IntExpression element = Factory.get(mat, rExpr, cExpr);
        buildCrtWithCondition(id, element, condition);
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list, int startIndex) {
        addToDV(list);
        IntExpression[] raw = $(list);
        int n = raw.length;
        IntExpression[] x = startIndex == 0 ? raw
                : Arrays.stream(raw).map(v -> Factory.minus(v, startIndex)).toArray(IntExpression[]::new);
        md.add(new AllDifferent(raw));
        for (int i = 0; i < n; i++) {
            md.add(Factory.ge(x[i], 0));
            md.add(Factory.lt(x[i], n));
            md.add(Factory.eq(Factory.get(x, x[i]), i));
            if (n <= 200) {
                for (int j = i + 1; j < n; j++) {
                    md.add(Factory.eq(Factory.eq(x[i], j), Factory.eq(x[j], i)));
                }
            }
        }
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list1, int startIndex1,
            XVariables.XVarInteger[] list2, int startIndex2) {
        addToDV(list1);
        addToDV(list2);
        IntExpression[] raw1 = $(list1);
        IntExpression[] raw2 = $(list2);
        int n1 = raw1.length;
        int n2 = raw2.length;
        IntExpression[] x = startIndex1 == 0 ? raw1
                : Arrays.stream(raw1).map(v -> Factory.minus(v, startIndex1)).toArray(IntExpression[]::new);
        IntExpression[] y = startIndex2 == 0 ? raw2
                : Arrays.stream(raw2).map(v -> Factory.minus(v, startIndex2)).toArray(IntExpression[]::new);
        for (int i = 0; i < n1; i++) {
            md.add(Factory.ge(x[i], 0));
            md.add(Factory.lt(x[i], n2));
            md.add(Factory.eq(Factory.get(y, x[i]), i));
        }
        if (n1 == n2) {
            md.add(new AllDifferent(raw1));
            md.add(new AllDifferent(raw2));
            for (int j = 0; j < n2; j++) {
                md.add(Factory.ge(y[j], 0));
                md.add(Factory.lt(y[j], n1));
                md.add(Factory.eq(Factory.get(x, y[j]), j));
            }
            if (n1 <= 150) {
                for (int i = 0; i < n1; i++) {
                    for (int j = 0; j < n2; j++) {
                        md.add(Factory.eq(Factory.eq(x[i], j), Factory.eq(y[j], i)));
                    }
                }
            }
        }
    }

    @Override
    public void buildCtrChannel(String id, XVariables.XVarInteger[] list, int startIndex,
            XVariables.XVarInteger value) {
        addToDV(list);
        addToDV(value);
        IntExpression[] x = $(list);
        IntExpression val = $(value);
        md.add(Factory.ge(val, startIndex));
        md.add(Factory.lt(val, startIndex + x.length));
        md.add(Factory.eq(new Sum(x), 1));
        for (int i = 0; i < x.length; i++) {
            md.add(Factory.eq(x[i], Factory.eq(val, startIndex + i)));
        }
    }

    // -------------------------------------------------------------------------
    // Scheduling: Cumulative, NoOverlap, BinPacking, Knapsack, Circuit
    // -------------------------------------------------------------------------

    private void postCumulativeDecomp(String id, IntExpression[] starts, IntExpression[] lengths, IntExpression[] ends,
            IntExpression[] heights, Condition condition) {
        int n = starts.length;
        if (ends != null) {
            for (int i = 0; i < n; i++) {
                md.add(Factory.eq(Factory.plus(starts[i], lengths[i]), ends[i]));
            }
        }
        int minT = Integer.MAX_VALUE;
        int maxT = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            minT = Math.min(minT, starts[i].min());
            maxT = Math.max(maxT, starts[i].max() + lengths[i].max());
        }
        if (maxT >= minT && (long) (maxT - minT) * n <= 100_000L) {
            for (int t = minT; t < maxT; t++) {
                final int time = t;
                List<IntExpression> activeHeights = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    IntExpression endExpr = ends != null ? ends[i] : Factory.plus(starts[i], lengths[i]);
                    if (starts[i].min() <= time && endExpr.max() > time && heights[i].max() > 0 && lengths[i].max() > 0) {
                        BoolExpression active = Factory.and(Factory.le(starts[i], time), Factory.gt(endExpr, time));
                        if (heights[i] instanceof Constant c) {
                            activeHeights.add(Factory.mul(active, c.v()));
                        } else {
                            activeHeights.add(Factory.mul(heights[i], active));
                        }
                    }
                }
                if (!activeHeights.isEmpty()) {
                    buildCrtWithCondition(id, new Sum(activeHeights.toArray(IntExpression[]::new)), condition);
                }
            }
        } else {
            // Event-point decomposition at each task start time
            for (int i = 0; i < n; i++) {
                IntExpression ti = starts[i];
                List<IntExpression> overlapHeights = new ArrayList<>(n);
                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        overlapHeights.add(heights[i]);
                    } else {
                        IntExpression endJ = ends != null ? ends[j] : Factory.plus(starts[j], lengths[j]);
                        BoolExpression overlaps = Factory.and(Factory.le(starts[j], ti), Factory.gt(endJ, ti));
                        if (heights[j] instanceof Constant c) {
                            overlapHeights.add(Factory.mul(overlaps, c.v()));
                        } else {
                            overlapHeights.add(Factory.mul(heights[j], overlaps));
                        }
                    }
                }
                buildCrtWithCondition(id, new Sum(overlapHeights.toArray(IntExpression[]::new)), condition);
            }
        }
    }

    private void postCumulativeConstraint(String id, IntExpression[] starts, IntExpression[] lengths,
            IntExpression[] ends, IntExpression[] heights, Condition condition) {
        if (condition instanceof Condition.ConditionVal cv
                && (cv.operator == Types.TypeConditionOperatorRel.LE || cv.operator == Types.TypeConditionOperatorRel.LT)) {
            int cap = (int) (cv.operator == Types.TypeConditionOperatorRel.LT ? cv.k - 1 : cv.k);
            md.add(new Cumulative(starts, lengths, ends, heights, md.constant(cap)));
            return;
        }
        if (condition instanceof Condition.ConditionVar cvar
                && (cvar.operator == Types.TypeConditionOperatorRel.LE || cvar.operator == Types.TypeConditionOperatorRel.LT)) {
            addToDV((XVariables.XVarInteger) cvar.x);
            IntExpression capExpr = cvar.operator == Types.TypeConditionOperatorRel.LT
                    ? Factory.minus($(cvar.x), 1)
                    : $(cvar.x);
            md.add(new Cumulative(starts, lengths, ends, heights, capExpr));
            return;
        }
        postCumulativeDecomp(id, starts, lengths, ends, heights, condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, int[] heights,
            Condition condition) {
        addToDV(origins);
        IntExpression[] lenExprs = Arrays.stream(lengths).mapToObj(md::constant).toArray(IntExpression[]::new);
        IntExpression[] hExprs = Arrays.stream(heights).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), lenExprs, null, hExprs, condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths,
            XVariables.XVarInteger[] ends, int[] heights, Condition condition) {
        addToDV(origins);
        addToDV(ends);
        IntExpression[] lenExprs = Arrays.stream(lengths).mapToObj(md::constant).toArray(IntExpression[]::new);
        IntExpression[] hExprs = Arrays.stream(heights).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), lenExprs, $(ends), hExprs, condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths,
            XVariables.XVarInteger[] heights, Condition condition) {
        addToDV(origins);
        addToDV(heights);
        IntExpression[] lenExprs = Arrays.stream(lengths).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), lenExprs, null, $(heights), condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths,
            int[] heights, Condition condition) {
        addToDV(origins);
        addToDV(lengths);
        IntExpression[] hExprs = Arrays.stream(heights).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), $(lengths), null, hExprs, condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths,
            XVariables.XVarInteger[] heights, Condition condition) {
        addToDV(origins);
        addToDV(lengths);
        addToDV(heights);
        postCumulativeConstraint(id, $(origins), $(lengths), null, $(heights), condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths,
            XVariables.XVarInteger[] ends, XVariables.XVarInteger[] heights, Condition condition) {
        addToDV(origins);
        addToDV(ends);
        addToDV(heights);
        IntExpression[] lenExprs = Arrays.stream(lengths).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), lenExprs, $(ends), $(heights), condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths,
            XVariables.XVarInteger[] ends, int[] heights, Condition condition) {
        addToDV(origins);
        addToDV(lengths);
        addToDV(ends);
        IntExpression[] hExprs = Arrays.stream(heights).mapToObj(md::constant).toArray(IntExpression[]::new);
        postCumulativeConstraint(id, $(origins), $(lengths), $(ends), hExprs, condition);
    }

    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths,
            XVariables.XVarInteger[] ends, XVariables.XVarInteger[] heights, Condition condition) {
        addToDV(origins);
        addToDV(lengths);
        addToDV(ends);
        addToDV(heights);
        postCumulativeConstraint(id, $(origins), $(lengths), $(ends), $(heights), condition);
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[] origins, int[] lengths, boolean zeroIgnored) {
        addToDV(origins);
        md.add(new Disjunctive($(origins), lengths));
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths,
            boolean zeroIgnored) {
        addToDV(origins);
        addToDV(lengths);
        IntExpression[] o = $(origins);
        IntExpression[] l = $(lengths);
        boolean allPositiveWhenZeroIgnored = true;
        if (zeroIgnored) {
            for (IntExpression len : l) {
                if (len.min() <= 0 && len.max() > 0) {
                    allPositiveWhenZeroIgnored = false;
                    break;
                }
            }
        }
        if (allPositiveWhenZeroIgnored) {
            md.add(new Disjunctive(o, l));
            return;
        }
        for (int i = 0; i < o.length; i++) {
            for (int j = i + 1; j < o.length; j++) {
                if (zeroIgnored && (l[i].min() <= 0 || l[j].min() <= 0)) {
                    md.add(Factory.or(
                            Factory.le(l[i], 0),
                            Factory.le(l[j], 0),
                            Factory.le(Factory.plus(o[i], l[i]), o[j]),
                            Factory.le(Factory.plus(o[j], l[j]), o[i])));
                } else {
                    md.add(Factory.or(
                            Factory.le(Factory.plus(o[i], l[i]), o[j]),
                            Factory.le(Factory.plus(o[j], l[j]), o[i])));
                }
            }
        }
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, int[][] lengths, boolean zeroIgnored) {
        for (XVariables.XVarInteger[] o : origins) addToDV(o);
        int n = origins.length;
        int dim = origins[0].length;
        for (int i = 0; i < n; i++) {
            if (zeroIgnored) {
                boolean anyZero = false;
                for (int d = 0; d < dim; d++) {
                    if (lengths[i][d] <= 0) anyZero = true;
                }
                if (anyZero) continue;
            }
            for (int j = i + 1; j < n; j++) {
                if (zeroIgnored) {
                    boolean anyZero = false;
                    for (int d = 0; d < dim; d++) {
                        if (lengths[j][d] <= 0) anyZero = true;
                    }
                    if (anyZero) continue;
                }
                BoolExpression[] sep = new BoolExpression[2 * dim];
                for (int d = 0; d < dim; d++) {
                    IntExpression oi = $(origins[i][d]);
                    IntExpression oj = $(origins[j][d]);
                    sep[2 * d] = Factory.le(Factory.plus(oi, lengths[i][d]), oj);
                    sep[2 * d + 1] = Factory.le(Factory.plus(oj, lengths[j][d]), oi);
                }
                md.add(Factory.or(sep));
            }
        }
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, XVariables.XVarInteger[][] lengths,
            boolean zeroIgnored) {
        for (XVariables.XVarInteger[] o : origins) addToDV(o);
        for (XVariables.XVarInteger[] l : lengths) addToDV(l);
        int n = origins.length;
        int dim = origins[0].length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                List<BoolExpression> sep = new ArrayList<>(2 * dim + 2 * dim);
                if (zeroIgnored) {
                    for (int d = 0; d < dim; d++) {
                        if ($(lengths[i][d]).min() <= 0) sep.add(Factory.le($(lengths[i][d]), 0));
                        if ($(lengths[j][d]).min() <= 0) sep.add(Factory.le($(lengths[j][d]), 0));
                    }
                }
                for (int d = 0; d < dim; d++) {
                    IntExpression oi = $(origins[i][d]);
                    IntExpression oj = $(origins[j][d]);
                    IntExpression li = $(lengths[i][d]);
                    IntExpression lj = $(lengths[j][d]);
                    sep.add(Factory.le(Factory.plus(oi, li), oj));
                    sep.add(Factory.le(Factory.plus(oj, lj), oi));
                }
                md.add(Factory.or(sep.toArray(BoolExpression[]::new)));
            }
        }
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[] xs, XVariables.XVarInteger[] ys,
            XVariables.XVarInteger[] lx, int[] ly, boolean zeroIgnored) {
        addToDV(xs);
        addToDV(ys);
        addToDV(lx);
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                List<BoolExpression> sep = new ArrayList<>(6);
                if (zeroIgnored) {
                    if (ly[i] <= 0 || ly[j] <= 0) continue;
                    if ($(lx[i]).min() <= 0) sep.add(Factory.le($(lx[i]), 0));
                    if ($(lx[j]).min() <= 0) sep.add(Factory.le($(lx[j]), 0));
                }
                sep.add(Factory.le(Factory.plus($(xs[i]), $(lx[i])), $(xs[j])));
                sep.add(Factory.le(Factory.plus($(xs[j]), $(lx[j])), $(xs[i])));
                sep.add(Factory.le(Factory.plus($(ys[i]), ly[i]), $(ys[j])));
                sep.add(Factory.le(Factory.plus($(ys[j]), ly[j]), $(ys[i])));
                md.add(Factory.or(sep.toArray(BoolExpression[]::new)));
            }
        }
    }

    @Override
    public void buildCtrBinPacking(String id, XVariables.XVarInteger[] list, int[] sizes, Condition condition) {
        addToDV(list);
        IntExpression[] x = $(list);
        int minBin = Arrays.stream(x).mapToInt(IntExpression::min).min().orElse(0);
        int maxBin = Arrays.stream(x).mapToInt(IntExpression::max).max().orElse(0);
        int nBins = maxBin - minBin + 1;
        IntExpression[] shifted = minBin == 0 ? x
                : Arrays.stream(x).map(v -> Factory.minus(v, minBin)).toArray(IntExpression[]::new);
        int maxLoad = Arrays.stream(sizes).sum();
        IntExpression[] loads = new IntExpression[nBins];
        for (int b = 0; b < nBins; b++) {
            loads[b] = md.intVar(0, maxLoad);
            buildCrtWithCondition(id, loads[b], condition);
        }
        md.add(new BinPacking(shifted, sizes, loads));
    }

    @Override
    public void buildCtrBinPacking(String id, XVariables.XVarInteger[] list, int[] sizes, int[] capacities,
            boolean load) {
        addToDV(list);
        IntExpression[] x = $(list);
        int minBin = Arrays.stream(x).mapToInt(IntExpression::min).min().orElse(0);
        IntExpression[] shifted = minBin == 0 ? x
                : Arrays.stream(x).map(v -> Factory.minus(v, minBin)).toArray(IntExpression[]::new);
        IntExpression[] loads = new IntExpression[capacities.length];
        for (int b = 0; b < capacities.length; b++) {
            loads[b] = load ? md.intVar(capacities[b], capacities[b]) : md.intVar(0, capacities[b]);
        }
        md.add(new BinPacking(shifted, sizes, loads));
    }

    @Override
    public void buildCtrBinPacking(String id, XVariables.XVarInteger[] list, int[] sizes,
            XVariables.XVarInteger[] loads, boolean load) {
        addToDV(list);
        addToDV(loads);
        IntExpression[] x = $(list);
        int minBin = Arrays.stream(x).mapToInt(IntExpression::min).min().orElse(0);
        IntExpression[] shifted = minBin == 0 ? x
                : Arrays.stream(x).map(v -> Factory.minus(v, minBin)).toArray(IntExpression[]::new);
        IntExpression[] $loads = $(loads);
        if (load) {
            md.add(new BinPacking(shifted, sizes, $loads));
        } else {
            int maxLoad = Arrays.stream(sizes).sum();
            IntExpression[] actualLoads = new IntExpression[$loads.length];
            for (int b = 0; b < $loads.length; b++) {
                actualLoads[b] = md.intVar(0, maxLoad);
                md.add(Factory.le(actualLoads[b], $loads[b]));
            }
            md.add(new BinPacking(shifted, sizes, actualLoads));
        }
    }

    @Override
    public void buildCtrBinPacking(String id, XVariables.XVarInteger[] list, int[] sizes, Condition[] conditions,
            int startIndex) {
        addToDV(list);
        IntExpression[] x = $(list);
        IntExpression[] shifted = startIndex == 0 ? x
                : Arrays.stream(x).map(v -> Factory.minus(v, startIndex)).toArray(IntExpression[]::new);
        int maxLoad = Arrays.stream(sizes).sum();
        IntExpression[] loads = new IntExpression[conditions.length];
        for (int b = 0; b < conditions.length; b++) {
            loads[b] = md.intVar(0, maxLoad);
            buildCrtWithCondition(id, loads[b], conditions[b]);
        }
        md.add(new BinPacking(shifted, sizes, loads));
    }

    @Override
    public void buildCtrKnapsack(String id, XVariables.XVarInteger[] list, int[] weights, Condition wcondition,
            int[] profits, Condition pcondition) {
        addToDV(list);
        IntExpression[] x = $(list);
        buildCrtWithCondition(id, new WeightedSum(x, weights), wcondition);
        buildCrtWithCondition(id, new WeightedSum(x, profits), pcondition);
    }

    @Override
    public void buildCtrCircuit(String id, XVariables.XVarInteger[] list, int startIndex) {
        addToDV(list);
        IntExpression[] succ = startIndex == 0 ? $(list)
                : Arrays.stream($(list)).map(x -> Factory.minus(x, startIndex)).toArray(IntExpression[]::new);
        md.add(new Circuit(succ));
    }

    @Override
    public void buildCtrCircuit(String id, XVariables.XVarInteger[] list, int startIndex, int size) {
        addToDV(list);
        IntExpression[] succ = startIndex == 0 ? $(list)
                : Arrays.stream($(list)).map(x -> Factory.minus(x, startIndex)).toArray(IntExpression[]::new);
        if (size == list.length) {
            md.add(new Circuit(succ));
        } else {
            md.add(new SubCircuit(succ));
            IntExpression[] active = IntStream.range(0, succ.length)
                    .mapToObj(i -> Factory.neq(succ[i], i))
                    .toArray(IntExpression[]::new);
            md.add(Factory.eq(new Sum(active), size));
        }
    }

    @Override
    public void buildCtrCircuit(String id, XVariables.XVarInteger[] list, int startIndex, XVariables.XVarInteger size) {
        addToDV(list);
        addToDV(size);
        IntExpression[] succ = startIndex == 0 ? $(list)
                : Arrays.stream($(list)).map(x -> Factory.minus(x, startIndex)).toArray(IntExpression[]::new);
        md.add(new SubCircuit(succ));
        IntExpression[] active = IntStream.range(0, succ.length)
                .mapToObj(i -> Factory.neq(succ[i], i))
                .toArray(IntExpression[]::new);
        md.add(Factory.eq(new Sum(active), $(size)));
    }

    // -------------------------------------------------------------------------
    // Objectives
    // -------------------------------------------------------------------------

    private IntExpression getExprForTypeObjective(Types.TypeObjective objtype, IntExpression[] list) {
        return switch (objtype) {
            case MAXIMUM -> new Max(list);
            case MINIMUM -> new Min(list);
            case SUM -> new Sum(list);
            case PRODUCT -> new Mul(list);
            case NVALUES -> buildNValuesExpr(list, Collections.emptySet());
            default -> throw new NotImplementedException("Unsupported objective type %s".formatted(objtype));
        };
    }

    private IntExpression getExprForTypeObjective(Types.TypeObjective objtype, IntExpression[] list, int[] coefs) {
        IntExpression[] listWithCoef = IntStream.range(0, list.length).mapToObj(i -> Factory.mul(list[i], coefs[i]))
                .toArray(IntExpression[]::new);
        return switch (objtype) {
            case MAXIMUM -> new Max(listWithCoef);
            case MINIMUM -> new Min(listWithCoef);
            case SUM -> new WeightedSum(list, coefs);
            case PRODUCT -> new Mul(listWithCoef);
            case NVALUES -> buildNValuesExpr(listWithCoef, Collections.emptySet());
            default -> throw new NotImplementedException("Unsupported objective type %s".formatted(objtype));
        };
    }

    @Override
    public void buildObjToMinimize(String id, XVariables.XVarInteger x) {
        addToDV(x);
        objective = md.minimize($(x));
    }

    @Override
    public void buildObjToMaximize(String id, XVariables.XVarInteger x) {
        addToDV(x);
        objective = md.maximize($(x));
    }

    @Override
    public void buildObjToMinimize(String id, XNodeParent<XVariables.XVarInteger> tree) {
        objective = md.minimize(_recursiveIntentionBuilder(tree));
    }

    @Override
    public void buildObjToMaximize(String id, XNodeParent<XVariables.XVarInteger> tree) {
        objective = md.maximize(_recursiveIntentionBuilder(tree));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list) {
        addToDV(list);
        objective = md.minimize(getExprForTypeObjective(type, $(list)));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list) {
        addToDV(list);
        objective = md.maximize(getExprForTypeObjective(type, $(list)));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list, int[] coeffs) {
        addToDV(list);
        objective = md.minimize(getExprForTypeObjective(type, $(list), coeffs));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XVariables.XVarInteger[] list, int[] coeffs) {
        addToDV(list);
        objective = md.maximize(getExprForTypeObjective(type, $(list), coeffs));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        objective = md.minimize(getExprForTypeObjective(type, list));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        objective = md.maximize(getExprForTypeObjective(type, list));
    }

    @Override
    public void buildObjToMinimize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees,
            int[] coeffs) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        objective = md.minimize(getExprForTypeObjective(type, list, coeffs));
    }

    @Override
    public void buildObjToMaximize(String id, Types.TypeObjective type, XNode<XVariables.XVarInteger>[] trees,
            int[] coeffs) {
        IntExpression[] list = Arrays.stream(trees).map(this::_recursiveIntentionBuilder).toArray(IntExpression[]::new);
        objective = md.maximize(getExprForTypeObjective(type, list, coeffs));
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

    private void addToDV(XVariables.XVarInteger... v) {
        for (XVariables.XVarInteger e : v) {
            if (e != null && !e.id().startsWith(reservedArray)) {
                decisionVars.add(e.id());
            }
        }
    }
}
