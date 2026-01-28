package org.maxicp.modeling.utils;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.util.HashMultimap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ExpressionsTopoSort {
    public static <T extends Expression> List<List<T>> toposort(Set<T> entries) {
        return toposort(entries, (x) -> x);
    }

    public static <T extends Expression> List<List<T>> toposort(Set<T> entries, Function<T, T> viewOf) {
        LinkedHashMap<T, T> graphRepr = new LinkedHashMap<T, T>();
        HashMultimap<T, T> graphReprReverse = new HashMultimap<>();
        for(T expr: entries) {
            T repr = viewOf.apply(expr);
            graphRepr.put(expr, repr);
            if(repr != expr)
                graphReprReverse.put(repr, expr);
        }

        HashMultimap<T, T> graph = new HashMultimap<>();
        for(T t: entries)
            populateRecursively(graphRepr, graph, entries, t, t);

        Set<T> allEntries = new LinkedHashSet<T>();
        allEntries.addAll(graph.keySet());
        allEntries.addAll(graphRepr.values());

        //computeLowerBound number of inbound edges for each node
        LinkedHashMap<T, Integer> inSize = new LinkedHashMap<>();
        for(T entry: allEntries)
            for(T nei: graph.get(entry))
                inSize.put(nei, inSize.getOrDefault(nei, 0)+1);

        //toposort (remove each time the layer of nodes without inbound edges)
        LinkedList<T> noDependence = new LinkedList<>();
        for(T entry: allEntries)
            if(inSize.getOrDefault(entry, 0) == 0)
                noDependence.add(entry);

        LinkedList<List<T>> output = new LinkedList<>();
        while (!noDependence.isEmpty()) {
            //Build output List
            LinkedList<T> layer = new LinkedList<>();
            for(T expr: noDependence) {
                layer.addLast(expr);
                for(T other: graphReprReverse.get(expr))
                    layer.addLast(other);
            }
            output.addLast(layer);

            //Find next layer
            LinkedList<T> next = new LinkedList<>();
            for(T entry: noDependence) {
                for(T nei: graph.get(entry)) {
                    int newVal = inSize.get(nei) - 1;
                    if(newVal == 0)
                        next.add(nei);
                    inSize.put(nei, newVal);
                }
            }
            noDependence = next;
        }

        for(T entry: allEntries)
            if(inSize.getOrDefault(entry, 0) != 0)
                throw new RuntimeException("Cyclic reference");

        return output;
    }

    private static <T extends Expression> void populateRecursively(LinkedHashMap<T, T> graphRepr, HashMultimap<T, T> graph, Set<T> entries, T base, Expression current) {
        for(Expression nei: current.subexpressions()) {
            if(entries.contains(nei)) {
                T a = graphRepr.get((T) nei);
                T b = graphRepr.get(base);
                if(!a.equals(b))
                    graph.put(a, b); //base is dependent on nei, so should be instantiated last
            }
            else
                populateRecursively(graphRepr, graph, entries, base, nei);
        }
    }
}
