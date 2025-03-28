package org.maxicp.modeling.utils;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.ExpressionIsTrue;
import org.maxicp.modeling.symbolic.SymbolicIntVar;
import org.maxicp.util.HashMultimap;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * EqHelper preprocesses Symbolic Models and build the "Equality Graph" of all the {@link IntExpression}
 * that should be equal. The graph is composed of connected components, each of them
 * representing a set of equal expressions. EqHelper chooses one of them as a representative for
 * each connected component.
 *
 * The output is a list of {@link Constraint}, that should be managed *first*. The list is composed
 * of these types of constraints:
 * - {@link EqInstantiateAndCache} constraint that contains a representative for each connected component.
 * - {@link EqInstantiateAndReuse} constraint that contains an Expression and its representative.
 *
 * The order in which they are returned *is important* (it avoids unresolved dependencies).
 *
 * If the {@link EqInstantiateAndCache}/{@link EqInstantiateAndReuse} are processed first, 
 * the instantiation process can then safely ignore the {@code ExpressionIsTrue(Eq(...))} constraints 
 * and should never need to post any {@link Eq} constraint directly.
 */
public class EqHelper {
    public record EqSimplified(List<EqInstantiate> newConstraints, LinkedHashSet<Constraint> oldConstraints) {};

    public interface EqInstantiate extends Constraint {}
    public record EqInstantiateAndCache(IntExpression expr) implements EqInstantiate {
        @Override
        public Collection<? extends Expression> scope() {
            return List.of(expr);
        }
    }
    public record EqInstantiateAndReuse(IntExpression expr, IntExpression repr) implements EqInstantiate {
        @Override
        public Collection<? extends Expression> scope() {
            return List.of(expr, repr);
        }
    }

    public static EqSimplified preprocess(Iterable<Constraint> constraints) {
        return preprocess(constraints, (x) -> x);
    }

    public static EqSimplified preprocess(Iterable<Constraint> constraints, Function<IntExpression, IntExpression> viewOf) {
        LinkedList<EqInstantiate> output = new LinkedList<>();

        //Find all the Equalities
        LinkedHashSet<Constraint> oldConstraints = StreamSupport.stream(constraints.spliterator(), false)
                .filter(x -> (x instanceof ExpressionIsTrue) && ((ExpressionIsTrue) x).expr() instanceof Eq)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Eq[] equalities = oldConstraints.stream().map(x -> (Eq) ((ExpressionIsTrue) x).expr()).toArray(Eq[]::new);


        UnionFind<IntExpression> uf = new UnionFind<>();
        for(Eq eq: equalities)
            uf.union(eq.a(), eq.b());
        LinkedHashSet<IntExpression> allExpressions = new LinkedHashSet<>(uf.representative.keySet());

        //For all expressions, find possible "other representative" that are equivalent
        //for example, in CP, the var for -x is a view of x, so an instantiated x can be a representative
        //for a set containing -x.
        LinkedHashMap<IntExpression, IntExpression> trueRepr = new LinkedHashMap<>();
        HashMultimap<IntExpression, IntExpression> trueReprReversed = new HashMultimap<>();
        for(IntExpression expr: allExpressions) {
            IntExpression trueR = viewOf.apply(expr);
            trueRepr.put(expr, trueR);
            trueReprReversed.put(trueR, expr);
        }
        allExpressions.addAll(trueReprReversed.keySet());



        //Each connected component has a representative, that we call a UF representative.
        //We will now elect, for each connected component, another representative (the Var representative)
        //that will be the expression to be instantiated first and reused.
        //
        //In order to avoid cases with dependencies like x==Sum(x, y), where choosing Sum(x,y) as representive
        //would instantiate x and force us to post an Equal constraint, we toposort the expression and select
        //the first expression in each connected component that has no (remaining) dependencies.
        //
        //If multiple representative are equivalent in the toposort, we select the one with the smaller domain
        List<List<IntExpression>> topoSort = ExpressionsTopoSort.toposort(allExpressions, viewOf);
        //We will process each list one after the other. Let's first sort them by domain size.
        for(List<IntExpression> layer: topoSort)
            layer.sort(domainSizeComparator);

        //hashMap containing the UF representative of each connected
        //component who already has a Var representative
        LinkedHashMap<IntExpression, IntExpression> doneCC = new LinkedHashMap<>();
        HashMultimap<IntExpression, IntExpression> waiting = new HashMultimap<>();

        for(List<IntExpression> layer: topoSort) {
            //Add the Expressions from the layer in the waiting list
            //... at least, if they have no representant yet
            for(IntExpression expr: layer) {
                IntExpression ufRepr = uf.find(expr);
                IntExpression repr = doneCC.get(ufRepr);
                if(repr != null && !repr.equals(expr)) {
                    output.addLast(new EqInstantiateAndReuse(expr, doneCC.get(ufRepr)));
                    addTrueRepr(trueRepr, trueReprReversed, doneCC, uf, waiting, output, expr);
                }
                else {
                    waiting.put(ufRepr, expr);
                }
            }

            //Now we want to find new representatives and instantiate as many things as we can
            for(IntExpression expr: layer) {
                IntExpression ufRepr = uf.find(expr);
                if(doneCC.containsKey(ufRepr))
                    continue;

                doneCC.put(ufRepr, expr);
                output.addLast(new EqInstantiateAndCache(expr));
                addTrueRepr(trueRepr, trueReprReversed, doneCC, uf, waiting, output, expr);

                waiting.remove(ufRepr, expr);
                for(IntExpression w: waiting.get(ufRepr)) {
                    output.addLast(new EqInstantiateAndReuse(w, expr));
                    addTrueRepr(trueRepr, trueReprReversed, doneCC, uf, waiting, output, w);
                }
                waiting.removeAll(ufRepr);
            }
        }

        assert waiting.isEmpty();

        return new EqSimplified(output, oldConstraints);
    }

    private static void addTrueRepr(LinkedHashMap<IntExpression, IntExpression> trueRepr,
                                    HashMultimap<IntExpression, IntExpression> trueReprReversed,
                                    LinkedHashMap<IntExpression, IntExpression> doneCC,
                                    UnionFind<IntExpression> uf,
                                    HashMultimap<IntExpression, IntExpression> waiting,
                                    LinkedList<EqInstantiate> output,
                                    IntExpression origExpr) {
        IntExpression repr = trueRepr.getOrDefault(origExpr, origExpr);
        for(IntExpression other: trueReprReversed.get(repr)) {
            IntExpression otherUfRepr = uf.find(other);
            if(!doneCC.containsKey(otherUfRepr)) {
                doneCC.put(otherUfRepr, other);
                output.addLast(new EqInstantiateAndCache(other));

                waiting.remove(otherUfRepr, other);
                for(IntExpression w: waiting.get(otherUfRepr)) {
                    output.addLast(new EqInstantiateAndReuse(w, other));
                    addTrueRepr(trueRepr, trueReprReversed, doneCC, uf, waiting, output, w);
                }
                waiting.removeAll(otherUfRepr);
            }
        }
    }

    private static final Comparator<IntExpression> domainSizeComparator = new Comparator<IntExpression>() {
        @Override
        public int compare(IntExpression o1, IntExpression o2) {
            int a = getSize(o1);
            int b = getSize(o2);
            if(a == b)
                return 0;
            if(a == Integer.MAX_VALUE)
                return Integer.MAX_VALUE;
            if(b == Integer.MAX_VALUE)
                return Integer.MIN_VALUE;
            return a-b;
        }

        private int getSize(IntExpression o) {
            //This is a heuristic. We want to prioritize selecting Constant as representatives,
            //so we put their "size" at 0 rather than 1.
            return switch (o) {
                case Constant c -> 0;
                case SymbolicIntVar iv -> iv.defaultSize();
                default -> Integer.MAX_VALUE - 10;
            };
        }
    };

    /**
     * A very simple UnionFind implementation based on HashMap (so we can discover new nodes "on the fly")
     * It implements path-compression but merges randomly (it does not attempt to minimize the size of the resulting tree).
     * @param <T>
     */
    private static class UnionFind<T> {
        public LinkedHashMap<T, T> representative;
        public UnionFind() {
            representative = new LinkedHashMap<>();
        }

        public void union(T a, T b) {
            a = find(a);
            b = find(b);
            if(a.equals(b))
                return;
            representative.put(a, b);
        }

        public T find(T elem) {
            T repr = representative.get(elem);
            if(repr == null) {
                representative.put(elem, elem);
                return elem;
            }
            if(repr.equals(elem))
                return elem;

            T repr2 = find(repr);
            if(!repr2.equals(repr))
                representative.put(elem, repr2);
            return repr2;
        }

        public LinkedHashSet<T>[] sets() {
            HashMultimap<T, T> map = new HashMultimap<>();
            for(T expr: representative.keySet())
                map.put(find(expr), expr);
            return map.keySet().stream().map(x -> map.get(x)).toArray(LinkedHashSet[]::new);
        }
    }
}
