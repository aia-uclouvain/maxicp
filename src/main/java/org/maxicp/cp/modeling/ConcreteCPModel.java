/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.Equal;
import org.maxicp.cp.engine.constraints.IsOr;
import org.maxicp.cp.engine.constraints.scheduling.*;
import org.maxicp.cp.engine.core.*;
import org.maxicp.modeling.*;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.bool.EndAfter;
import org.maxicp.modeling.algebra.bool.EndBefore;
import org.maxicp.modeling.algebra.bool.EndBeforeStart;
import org.maxicp.modeling.algebra.bool.StartAfter;
import org.maxicp.modeling.algebra.bool.StartBefore;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.algebra.scheduling.*;
import org.maxicp.modeling.algebra.sequence.SeqExpression;
import org.maxicp.modeling.concrete.*;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.constraints.scheduling.Length;
import org.maxicp.modeling.constraints.scheduling.NoOverlap;
import org.maxicp.modeling.constraints.scheduling.Present;
import org.maxicp.modeling.constraints.scheduling.Start;
import org.maxicp.modeling.constraints.seqvar.*;
import org.maxicp.modeling.symbolic.*;
import org.maxicp.modeling.utils.EqHelper;
import org.maxicp.search.IntObjective;
import org.maxicp.state.State;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateMap;
import org.maxicp.util.exception.NotImplementedException;
import org.maxicp.util.exception.NotYetImplementedException;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class ConcreteCPModel implements ConcreteModel {
    private final State<SymbolicModel> model;
    private final SymbolicModel concretizedNode;
    public final CPSolver solver;
    final StateMap<BoolExpression, CPBoolVar> boolExprMapping;
    final StateMap<IntExpression, CPIntVar> intExprMapping;
    final StateMap<SeqExpression, CPSeqVar> seqExprMapping;
    final StateMap<IntervalExpression, CPIntervalVar> intervalExprMapping;
    final StateMap<CumulFunction, CPCumulFunction> cumulFunMapping;
    private final ModelProxy modelProxy;

    private boolean firstConstruction;
    private boolean disableFixPoint;

    /**
     * Temporarily disables the fix point while r is run, then run the fixpoint
     *
     * @param r
     */
    private void noFixPoint(java.lang.Runnable r) {
        boolean oldVal = disableFixPoint;
        disableFixPoint = true;
        r.run();
        disableFixPoint = oldVal;
    }

    /**
     * Post method that disables the fix point when needed
     *
     * @param constraint
     */
    private void post(CPConstraint constraint) {
        solver.post(constraint, !disableFixPoint);
    }

    /**
     * Calls the fixpoint if if is not disabled
     */
    private void fixpoint() {
        if (!disableFixPoint)
            solver.fixPoint();
    }

    public ConcreteCPModel(ModelProxy modelProxy, CPSolver solver, SymbolicModel baseNode) {
        firstConstruction = true;
        disableFixPoint = false;

        this.modelProxy = modelProxy;
        this.concretizedNode = baseNode;
        this.model = solver.getStateManager().makeStateRef(baseNode);
        this.solver = solver;
        this.intExprMapping = solver.getStateManager().makeStateMap();
        this.boolExprMapping = solver.getStateManager().makeStateMap();
        this.seqExprMapping = solver.getStateManager().makeStateMap();
        this.intervalExprMapping = solver.getStateManager().makeStateMap();
        this.cumulFunMapping = solver.getStateManager().makeStateMap();

        noFixPoint(() -> {
            EqHelper.EqSimplified eqSimplified = EqHelper.preprocess(baseNode.getConstraints(), ConcreteCPModel::isViewOf);
            for (Constraint c : eqSimplified.newConstraints())
                instantiateConstraint(c);

            Set<Constraint> ignored = eqSimplified.oldConstraints();

            for (Constraint c : baseNode.getConstraints())
                if (!ignored.contains(c))
                    instantiateConstraint(c);
        });
        solver.fixPoint();
        getStateManager().saveState();

        firstConstruction = false;
    }

    /**
     * Views are a CP concept, not existing in the modeling layer, but sometime
     *
     * @param expr
     * @return
     */
    private static IntExpression isViewOf(IntExpression expr) {
        return switch (expr) {
            case CstOffset co -> isViewOf(co.expr());
            case UnaryMinus um -> isViewOf(um.expr());
            default -> expr;
        };
    }

    public ConcreteIntVar getConcreteVar(IntExpression expr) {
        return getCPVar(expr);
    }

    public ConcreteBoolVar getConcreteVar(BoolExpression expr) {
        return getCPVar(expr);
    }

    public ConcreteSeqVar getConcreteVar(SeqExpression expr) {
        return getCPVar(expr);
    }

    public ConcreteIntervalVar getConcreteVar(IntervalExpression expr) {
        return getCPVar(expr);
    }

    public CPBoolVar getCPVar(BoolExpression v) {
        if (v instanceof CPBoolVar cpVar)
            return cpVar;

        CPBoolVar cached = boolExprMapping.get(v);
        if (cached != null)
            return cached;

        CPBoolVar newVar = switch (v) {
            case IsNodeRequired bv -> getCPVar(bv.seqVar).getNodeVar(bv.node).isRequired();
            case IntervalStatus s -> getCPVar(s.intervalVar).status();
            case BoolVarImpl bv -> CPFactory.makeBoolVar(solver, bv.contains(0), bv.contains(1));
            case Not e -> CPFactory.not(getCPVar(e.a()));
            case Eq e -> CPFactory.isEq(getCPVar(e.a()), getCPVar(e.b()));
            case NotEq e -> CPFactory.not(CPFactory.isEq(getCPVar(e.a()), getCPVar(e.b())));
            case LessOrEq e -> CPFactory.isLe(getCPVar(e.a()), getCPVar(e.b()));
            case GreaterOrEq e -> CPFactory.isGe(getCPVar(e.a()), getCPVar(e.b()));
            case And e -> {
                CPIntVar s = CPFactory.makeIntVar(solver, 0, e.exprs().size());
                post(new org.maxicp.cp.engine.constraints.Sum(e.exprs().stream().map(x -> getCPVar((IntExpression) x)).toArray(CPIntVar[]::new), s));
                yield CPFactory.isEq(s, e.exprs().size());
            }
            case Or or -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new IsOr(b, or.exprs().stream().map(this::getCPVar).toArray(CPBoolVar[]::new)));
                yield b;
            }
            case EndBeforeStart is -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new org.maxicp.cp.engine.constraints.scheduling.IsEndBeforeStart(getCPVar(is.a()), getCPVar(is.b()), b));
                yield b;
            }
            case EndBefore e -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new org.maxicp.cp.engine.constraints.scheduling.IsEndBefore(b, getCPVar(e.intervalVar()), getCPVar(e.end())));
                yield b;
            }
            case EndAfter e -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new org.maxicp.cp.engine.constraints.scheduling.IsEndAfter(b, getCPVar(e.interval()), getCPVar(e.value())));
                yield b;
            }
            case StartBefore e -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new org.maxicp.cp.engine.constraints.scheduling.IsStartBefore(b, getCPVar(e.interval()), getCPVar(e.value())));
                yield b;
            }
            case StartAfter e -> {
                CPBoolVar b = CPFactory.makeBoolVar(solver);
                post(new org.maxicp.cp.engine.constraints.scheduling.IsStartBefore(CPFactory.not(b), getCPVar(e.interval()), getCPVar(e.value())));
                yield b;
            }
            case org.maxicp.modeling.algebra.bool.Present p -> getCPVar(p.interval()).status();
            default ->
                    throw new NotYetImplementedException("Unknown expression type %s in getCPVar".formatted(v.getClass()));
        };
        boolExprMapping.put(v, newVar);
        return newVar;
    }

    public CPIntVar getCPVar(IntExpression v) {
        if (v instanceof BoolExpression be)
            return getCPVar(be);

        if (v instanceof CPIntVar cpVar)
            return cpVar;

        CPIntVar cached = intExprMapping.get(v);
        if (cached != null)
            return cached;

        CPIntVar newVar = switch (v) {
            case IntVarSetImpl iv -> CPFactory.makeIntVar(solver, iv.dom);
            case IntVarRangeImpl iv -> CPFactory.makeIntVar(solver, iv.defaultMin(), iv.defaultMax());
            case CstOffset iv -> CPFactory.plus(getCPVar(iv.expr()), iv.v());
            case CstMul iv -> CPFactory.mul(getCPVar(iv.expr()), iv.mul());
            case Abs iv -> CPFactory.abs(getCPVar(iv.expr()));
            case UnaryMinus iv -> CPFactory.minus(getCPVar(iv.expr()));
            case Sum ie -> CPFactory.sum(Arrays.stream(ie.subexprs()).map(this::getCPVar).toArray(CPIntVar[]::new));
            case WeightedSum ie ->
                    CPFactory.sum(IntStream.range(0, ie.subexprs().length).mapToObj(i -> CPFactory.mul(getCPVar(ie.subexprs()[i]), ie.weights()[i])).toArray(CPIntVar[]::new));
            case Constant c -> CPFactory.makeIntVar(solver, c.v(), c.v());
            case Min m -> CPFactory.minimum(Arrays.stream(m.exprs()).map(this::getCPVar).toArray(CPIntVar[]::new));
            case Max m -> CPFactory.max(Arrays.stream(m.exprs()).map(this::getCPVar).toArray(CPIntVar[]::new));
            case Element1D e -> CPFactory.element(e.array(), getCPVar(e.index()));
            case Element1DVar e ->
                    CPFactory.element(Arrays.stream(e.array()).map(this::getCPVar).toArray(CPIntVar[]::new), getCPVar(e.index()));
            case Element2D e -> CPFactory.element(e.array(), getCPVar(e.x()), getCPVar(e.y()));
            case IntervalEndOrValue i -> CPFactory.endOr(getCPVar(i.interval()), i.value());
            case IntervalEnd i -> CPFactory.end(getCPVar(i.interval()));
            case IntervalStartOrValue i -> CPFactory.startOr(getCPVar(i.interval()), i.value());
            case IntervalStart i -> CPFactory.start(getCPVar(i.interval()));
            case IntervalLengthOrValue i -> CPFactory.lengthOr(getCPVar(i.interval()), i.value());
            case IntervalLength i -> CPFactory.length(getCPVar(i.interval()));
            case Mul ie -> CPFactory.mul(Arrays.stream(ie.subexprs()).map(this::getCPVar).toArray(CPIntVar[]::new));
            case MulBinary ie -> {
                if (ie.b().min() == 1) // x * 1 ==> x
                    yield getCPVar(ie.x());
                else
                    yield CPFactory.mul(getCPVar(ie.x()), getCPVar(ie.b()));
            }
            default ->
                    throw new NotYetImplementedException("Unknown expression type %s in getCPVar".formatted(v.getClass()));
        };
        intExprMapping.put(v, newVar);
        return newVar;
    }

    /**
     * Enforce that v == expr, without using Equal/NotEqual if possible.
     */
    public void enforceEqualityIntExpression(IntExpression expr, CPIntVar v) {
        if ((expr instanceof BoolExpression && boolExprMapping.containsKey(expr)) || intExprMapping.containsKey(expr))
            throw new RuntimeException("enforceEqualityIntExpression cannot force equality on an already-instantiated expression");
        switch (expr) {
            case IntVarSetImpl iv -> {
                int[] content = new int[v.size()];
                v.fillArray(content);
                for (int val : content)
                    if (!iv.dom.contains(val))
                        v.remove(val);
            }
            case IntVarRangeImpl iv -> {
                v.removeBelow(iv.min());
                v.removeAbove(iv.max());
            }
            case IntVar x -> throw new RuntimeException("Unknown IntVar type %s".formatted(x.getClass()));
            case Constant c -> v.fix(c.v());
            case CstOffset co -> {
                // co.expr + c == v    <=>    co.expr == v - c
                enforceEqualityIntExpression(co.expr(), CPFactory.minus(v, co.v()));
            }
            case CstMul cm -> {
                //fallback
                post(new org.maxicp.cp.engine.constraints.Equal(CPFactory.mul(getCPVar(cm.expr()), cm.mul()), v));
            }
            case Mul m -> {
                // m.exprs[0] * m.exprs[1] * ... == v
                CPIntVar[] exprs = Arrays.stream(m.subexprs()).map(this::getCPVar).toArray(CPIntVar[]::new);
                if (exprs.length == 2) {
                    // m.exprs[0] * m.exprs[1] == v
                    post(new org.maxicp.cp.engine.constraints.MulVar(exprs[0], exprs[1], v));
                } else {
                    post(new org.maxicp.cp.engine.constraints.Equal(CPFactory.mul(exprs), v));
                }
            }
            case UnaryMinus um -> {
                // -um.expr == v   <=>  um.expr == -v
                enforceEqualityIntExpression(um.expr(), CPFactory.minus(v));
            }
            case Abs abs -> post(new org.maxicp.cp.engine.constraints.Absolute(getCPVar(abs.expr()), v));
            case Sum sum -> post(new org.maxicp.cp.engine.constraints.Sum(getCPVar(sum.subexprs()), v));
            case WeightedSum ie -> post(new org.maxicp.cp.engine.constraints.Sum(
                    IntStream.range(0, ie.subexprs().length).mapToObj(i -> CPFactory.mul(getCPVar(ie.subexprs()[i]), ie.weights()[i])).toArray(CPIntVar[]::new),
                    v
            ));
            case Min m ->
                    post(new org.maxicp.cp.engine.constraints.Maximum(Arrays.stream(getCPVar(m.exprs())).map(CPFactory::minus).toArray(CPIntVar[]::new), CPFactory.minus(v)));
            case Max m -> post(new org.maxicp.cp.engine.constraints.Maximum(getCPVar(m.exprs()), v));
            case Element1D e -> post(new org.maxicp.cp.engine.constraints.Element1D(e.array(), getCPVar(e.index()), v));
            case Element1DVar e ->
                    post(new org.maxicp.cp.engine.constraints.Element1DVar(getCPVar(e.array()), getCPVar(e.index()), v));
            case Element2D e ->
                    post(new org.maxicp.cp.engine.constraints.Element2D(e.array(), getCPVar(e.x()), getCPVar(e.y()), v));
            case Eq e ->
                    post(new org.maxicp.cp.engine.constraints.IsEqualVar(asBoolVar(v), getCPVar(e.a()), getCPVar(e.b())));
            case NotEq e ->
                    post(new org.maxicp.cp.engine.constraints.IsEqualVar(CPFactory.not(asBoolVar(v)), getCPVar(e.a()), getCPVar(e.b())));
            case LessOrEq e ->
                    post(new org.maxicp.cp.engine.constraints.IsLessOrEqualVar(asBoolVar(v), getCPVar(e.a()), getCPVar(e.b())));
            case And e -> {
                CPIntVar s = CPFactory.makeIntVar(solver, 0, e.exprs().size());
                post(new org.maxicp.cp.engine.constraints.Sum(e.exprs().stream().map(x -> getCPVar((IntExpression) x)).toArray(CPIntVar[]::new), s));
                post(new org.maxicp.cp.engine.constraints.IsEqual(asBoolVar(v), s, e.exprs().size()));
            }
            case Not e -> {
                //fallback. Note that we don't use e but expr below, so we put v Equals expr === v Equals Not(e).
                post(new org.maxicp.cp.engine.constraints.Equal(getCPVar(expr), v));
            }
            case IntervalStart ivs -> {
                post(new Equal(getCPVar(ivs),getCPVar(v)));
            }
            case IntervalEnd ive -> {
                post(new Equal(getCPVar(ive),getCPVar(v)));
            }
            case IntervalLength ivl -> {
                post(new Equal(getCPVar(ivl),getCPVar(v)));
            }
            default ->
                    throw new NotYetImplementedException("Unknown expression type %s in enforceEqualityIntExpression".formatted(v.getClass()));
        }
        intExprMapping.put(expr, v);
    }

    public CPBoolVar asBoolVar(CPIntVar v) {
        if (v instanceof CPBoolVar bv)
            return bv;
        return new CPBoolVarImpl(v);
    }

    public org.maxicp.search.Objective minimize(IntVar v) {
        return new Minimize(getCPVar(v));
    }

    public CPIntVar[] getCPVar(IntExpression[] v) {
        return Arrays.stream(v).map(this::getCPVar).toArray(CPIntVar[]::new);
    }

    public CPBoolVar[] getCPVar(BoolExpression[] v) {
        return Arrays.stream(v).map(this::getCPVar).toArray(CPBoolVar[]::new);
    }

    public CPSeqVar getCPVar(SeqExpression v) {
        if (v instanceof CPSeqVar cpv)
            return cpv;

        CPSeqVar cached = seqExprMapping.get(v);
        if (cached != null)
            return cached;
        CPSeqVar newVar = switch (v) {
            case SeqVarImpl sv -> CPFactory.makeSeqVar(solver, sv.nNode(), sv.start(), sv.end());
            default -> throw new NotImplementedException("Unknown var type %s".formatted(v.getClass()));
        };
        seqExprMapping.put(v, newVar);
        return newVar;
    }

    public CPIntervalVar[] getCPVar(IntervalExpression[] v) {
        return Arrays.stream(v).map(this::getCPVar).toArray(CPIntervalVar[]::new);
    }

    public CPIntervalVar getCPVar(IntervalExpression v) {
        if (v instanceof CPIntervalVar cpv)
            return cpv;

        CPIntervalVar cached = intervalExprMapping.get(v);
        if (cached != null)
            return cached;
        CPIntervalVar newVar = switch (v) {
            case IntervalVarImpl iv -> {
                CPIntervalVar intervalVar = CPFactory.makeIntervalVar(solver, iv.isOptional(), iv.lengthMin(), iv.lengthMax());
                intervalVar.setStartMin(iv.startMin());
                intervalVar.setStartMax(iv.startMax());
                intervalVar.setEndMin(iv.endMin());
                intervalVar.setEndMax(iv.endMax());
                yield intervalVar;
            }
            default -> throw new NotImplementedException("Unknown var type %s".formatted(v.getClass()));
        };
        intervalExprMapping.put(v, newVar);
        return newVar;
    }

    public CPCumulFunction getCumulFunction(CumulFunction f) {
        if (f instanceof CPCumulFunction cpe)
            return cpe;
        CPCumulFunction cached = cumulFunMapping.get(f);
        if (cached != null)
            return cached;
        CPCumulFunction c = switch (f) {
            case FlatCumulFunction ignored -> new CPFlatCumulFunction();
            case MinusCumulFunction minus ->
                    new CPMinusCumulFunction(getCumulFunction(minus.left()), getCumulFunction(minus.right()));
            case PlusCumulFunction plus ->
                    new CPPlusCumulFunction(getCumulFunction(plus.left()), getCumulFunction(plus.right()));
            case PulseCumulFunction pulse ->
                    new CPPulseCumulFunction(getCPVar(pulse.interval), pulse.hMin, pulse.hMax);
            case StepAtEndCumulFunction step ->
                    new CPStepAtEndCumulFunction(getCPVar(step.interval), step.hMin, step.hMax);
            case StepAtStartCumulFunction step ->
                    new CPStepAtStartCumulFunction(getCPVar(step.interval), step.hMin, step.hMax);
            case SumCumulFunction sum ->
                new CPSumCumulFunction(Arrays.stream(sum.functions).map(this::getCumulFunction).toArray(CPCumulFunction[]::new));
            default -> throw new NotImplementedException("Unknown cumul expression type %s".formatted(f.getClass()));
        };
        cumulFunMapping.put(f, c);
        return c;
    }

    public IntObjective minimize(IntExpression v) {
        return solver.minimize(getCPVar(v));
    }

    public IntObjective maximize(IntExpression v) {
        return solver.maximize(getCPVar(v));
    }

    @Override
    public void add(Constraint c, boolean enforceFixPoint) {
        if (!enforceFixPoint) {
            boolean oldVal = disableFixPoint;
            disableFixPoint = true;
            instantiateConstraint(c);
            disableFixPoint = oldVal;
        } else {
            instantiateConstraint(c);
        }

        model.setValue(model.value().add(c));
    }

    @Override
    public void jumpTo(SymbolicModel node, boolean enforceFixPoint) {
        // Find the first node in common
        HashSet<SymbolicModel> nodeCurrentlyPresent = new HashSet<>();

        SymbolicModel cur = model.value();
        while (cur != concretizedNode) {
            nodeCurrentlyPresent.add(cur);
            cur = cur.parent();
        }
        nodeCurrentlyPresent.add(cur);

        cur = node;
        while (cur != null && !nodeCurrentlyPresent.contains(cur)) {
            cur = cur.parent();
        }
        SymbolicModel firstCommonNode = cur;

        // Now list the nodes before this first common node
        nodeCurrentlyPresent.clear();
        cur = firstCommonNode;
        while (cur != null) {
            nodeCurrentlyPresent.add(cur);
            cur = cur.parent();
        }

        // Now revert the solver until we are at a node below the first common node
        boolean hasJumped = false; // set to true if the solver has actually jumped (in case jumping to the current location)
        while (!nodeCurrentlyPresent.contains(model.value())) {
            hasJumped = true;
            getStateManager().restoreState();
        }
        if (hasJumped)
            getStateManager().saveState();

        // We now just have to add constraints until we are at the right node
        ArrayDeque<Constraint> needConcretization = new ArrayDeque<>();
        cur = node;
        while (cur != model.value()) {
            needConcretization.addFirst(cur.constraint());
            cur = cur.parent();
        }

        noFixPoint(() -> {
            for (Constraint cln : needConcretization) {
                instantiateConstraint(cln);
            }
        });
        if (enforceFixPoint)
            solver.fixPoint();

        model.setValue(node);
    }

    @Override
    public void jumpToChild(SymbolicModel m, boolean enforceFixPoint) {
        SymbolicModel me = model.value();
        ArrayDeque<Constraint> todo = new ArrayDeque<>();
        SymbolicModel child = m;
        while (child != me) {
            if (child.isEmpty())
                throw new NotAChildModelException();
            todo.addLast(child.constraint());
            if (child.parent() == null)
                throw new NotAChildModelException();
            child = child.parent();
        }
        noFixPoint(() -> {
            for (Constraint cln : todo) {
                instantiateConstraint(cln);
            }
        });
        if (enforceFixPoint)
            solver.fixPoint();
    }

    @Override
    public SymbolicModel symbolicCopy() {
        return model.value();
    }

    @Override
    public Iterable<Constraint> getConstraints() {
        return model.value().getConstraints();
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }

    private void instantiateBoolExpression(BoolExpression expr) {
        switch (expr) {
            case Eq e -> {
                if (firstConstruction)
                    throw new RuntimeException("It should be impossible to post new Equal constraints while building");
                post(new org.maxicp.cp.engine.constraints.Equal(getCPVar(e.a()), getCPVar(e.b())));
            }
            case And a -> {
                for (BoolExpression e : a.exprs()) {
                    instantiateBoolExpression(e);
                }
            }
            case NotEq e -> post(new org.maxicp.cp.engine.constraints.NotEqual(getCPVar(e.a()), getCPVar(e.b())));
            case LessOrEq e -> post(new org.maxicp.cp.engine.constraints.LessOrEqual(getCPVar(e.a()), getCPVar(e.b())));
            case GreaterOrEq e ->
                    post(new org.maxicp.cp.engine.constraints.LessOrEqual(getCPVar(e.b()), getCPVar(e.a())));
            case Or e ->
                    post(new org.maxicp.cp.engine.constraints.Or(getCPVar(e.exprs().toArray(BoolExpression[]::new))));
            case InSet e -> {
                CPIntVar v = getCPVar(e.a());
                int[] values = new int[v.size()];
                v.fillArray(values);
                for (int val : values) {
                    if (!e.b().contains(val))
                        v.remove(val);
                }
            }
            case EndBeforeStart e -> {
                post(new org.maxicp.cp.engine.constraints.scheduling.EndBeforeStart(getCPVar(e.a()), getCPVar(e.b())));
            }
            case EndBefore e -> {
                post(new org.maxicp.cp.engine.constraints.scheduling.EndBefore(getCPVar(e.intervalVar()), getCPVar(e.end())));
            }
            case StartAfter s -> {
                post(new org.maxicp.cp.engine.constraints.scheduling.StartAfter(getCPVar(s.interval()), getCPVar(s.value())));
            }
            case org.maxicp.modeling.algebra.bool.Present p -> {
                getCPVar(p.interval().status()).fix(true);
                fixpoint();
            }
            default ->
                    throw new NotYetImplementedException("Unknown expression type %s in instantiateBoolExpression".formatted(expr.getClass()));
        }
    }

    private void instantiateConstraint(Constraint c) {
        switch (c) {
            case AllDifferent a -> {
                CPIntVar[] args = a.x().stream().map(this::getCPVar).toArray(CPIntVar[]::new);
                post(new org.maxicp.cp.engine.constraints.AllDifferentDC(args));
            }
            case CardinalityMin cardMin -> {
                CPIntVar[] args = Arrays.stream(cardMin.x()).map(this::getCPVar).toArray(CPIntVar[]::new);
                int [] card = cardMin.array();
                post(new org.maxicp.cp.engine.constraints.CardinalityMinFWC(args,card));
            }
            case CardinalityMax cardMax -> {
                CPIntVar[] args = Arrays.stream(cardMax.x()).map(this::getCPVar).toArray(CPIntVar[]::new);
                int [] card = cardMax.array();
                post(new org.maxicp.cp.engine.constraints.CardinalityMaxFWC(args,card));
            }
            case BinPacking binPacking -> {
                CPIntVar[] x = Arrays.stream(binPacking.x()).map(this::getCPVar).toArray(CPIntVar[]::new);
                int[] w = binPacking.weights();
                CPIntVar[] l = Arrays.stream(binPacking.loads()).map(this::getCPVar).toArray(CPIntVar[]::new);
                post(new org.maxicp.cp.engine.constraints.BinPacking(x, w, l));
            }
            case Sorted sorted -> {
                CPIntVar[] x = Arrays.stream(sorted.x()).map(this::getCPVar).toArray(CPIntVar[]::new);
                CPIntVar[] o = Arrays.stream(sorted.o()).map(this::getCPVar).toArray(CPIntVar[]::new);
                CPIntVar[] y = Arrays.stream(sorted.o()).map(this::getCPVar).toArray(CPIntVar[]::new);
                post(new org.maxicp.cp.engine.constraints.Sorted(x, o, y));
            }
            case Table t -> {
                if (t.starred().isEmpty())
                    post(new org.maxicp.cp.engine.constraints.TableCT(getCPVar(t.x()), t.array()));
                else
                    post(new org.maxicp.cp.engine.constraints.ShortTableCT(getCPVar(t.x()), t.array(), t.starred().get()));
            }
            case NegTable t -> {
                if (t.starred().isEmpty())
                    post(new org.maxicp.cp.engine.constraints.NegTableCT(getCPVar(t.x()), t.array()));
                else
                    throw new NotYetImplementedException("Negative Table with stars is available in maxicp.cp but not yet implemented");
            }
            //-----------------------------------------
            case Circuit circuit -> {
                post(new org.maxicp.cp.engine.constraints.Circuit(getCPVar(circuit.successor())));
            }
            case Require require -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Require(getCPVar(require.seqVar()), require.node()));
            }
            case Insert insert -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Insert(getCPVar(insert.seqVar()), insert.prev(), insert.node()));
            }
            case Exclude exclude -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Exclude(getCPVar(exclude.seqVar()), exclude.node()));
            }
            case Distance distance -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Distance(getCPVar(distance.seqVar), distance.distanceMatrix, getCPVar(distance.distance)));
            }
            case TransitionTimes transitionTimes -> {
                CPIntVar[] time = Arrays.stream(transitionTimes.time).map(this::getCPVar).toArray(CPIntVar[]::new);
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.TransitionTimes(getCPVar(transitionTimes.seqVar), time, transitionTimes.dist, transitionTimes.serviceTime));
            }
            case org.maxicp.modeling.constraints.seqvar.Cumulative cumu -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Cumulative(getCPVar(cumu.seqVar), cumu.starts, cumu.ends, cumu.load, cumu.capacity));
            }
            case Precedence prec -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.Precedence(getCPVar(prec.seqVar()), prec.nodes()));
            }
            case RemoveDetour rd -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.RemoveDetour(getCPVar(rd.seqVar()), rd.prev(), rd.node(), rd.after()));
            }
            case SubSequence ss -> {
                solver.post(new org.maxicp.cp.engine.constraints.seqvar.SubSequence(getCPVar(ss.main()), getCPVar(ss.sub())));
            }
            case ExpressionIsTrue eit -> instantiateBoolExpression(eit.expr());
            case EqHelper.EqInstantiateAndCache eqic -> getCPVar(eqic.expr());
            case EqHelper.EqInstantiateAndReuse eqir -> {
                switch (eqir.repr()) {
                    case BoolExpression bexpr -> enforceEqualityIntExpression(eqir.expr(), boolExprMapping.get(bexpr));
                    case IntExpression iexpr -> enforceEqualityIntExpression(eqir.expr(), intExprMapping.get(iexpr));
                }
            }
            case NoOverlap noOverlap -> {
                solver.post(new org.maxicp.cp.engine.constraints.scheduling.NoOverlap(getCPVar(noOverlap.intervals())));
            }
            case Length length -> {
                getCPVar(length.interval()).setLength(length.length());
                fixpoint();
            }
            case Present present -> {
                getCPVar(present.intervalVar()).setPresent();
                fixpoint();
            }
            case Start start -> {
                getCPVar(start.intervalVar()).setStart(start.start());
                fixpoint();
            }
            case org.maxicp.modeling.constraints.scheduling.StartAfter start -> {
                getCPVar(start.var()).setStartMin(start.start() + 1);
                fixpoint();
            }
            case org.maxicp.modeling.constraints.scheduling.LessOrEqual lessOrEqual -> {
                CPCumulFunction cpExpression = getCumulFunction(lessOrEqual.function());
                solver.post(CPFactory.le(cpExpression, lessOrEqual.limit()));
            }
            case org.maxicp.modeling.constraints.scheduling.Alternative alternative -> {
                solver.post(CPFactory.alternative(getCPVar(alternative.real()), getCPVar(alternative.alternatives()), getCPVar(alternative.n())));
            }
            case org.maxicp.modeling.constraints.scheduling.AlwaysIn alwaysIn -> {
                CPCumulFunction cpExpression = getCumulFunction(alwaysIn.expr());
                solver.post(CPFactory.alwaysIn(cpExpression, alwaysIn.minValue(), alwaysIn.maxValue()));
            }
            case CustomConstraint instantiableConstraint -> {
                Object cpConstraint = instantiableConstraint.instantiate(this);
                if (!(cpConstraint instanceof CPConstraint constraint))
                    throw new ClassCastException("The given instantiable constraint cannot be cast to a CPConstraint " + cpConstraint.getClass());
                solver.post(constraint);
            }
            case NoOpConstraint ignored -> fixpoint();
            default -> throw new NotYetImplementedException("Unexpected value: " + c);
        }
    }

    @Override
    public org.maxicp.search.Objective createObjective(Objective obj) {
        return switch (obj) {
            case Minimization m -> new Minimize(getCPVar(m.expr()));
            case Maximization m -> new Maximize(getCPVar(m.expr()));
            case SharedMaximization m -> new Maximize(getCPVar(m.expr), m.bound);
            case SharedMinimization m -> new Minimize(getCPVar(m.expr), m.bound);
            default -> throw new IllegalArgumentException("the objective is not supported");
        };
    }

    @Override
    public StateManager getStateManager() {
        return solver.getStateManager();
    }
}
