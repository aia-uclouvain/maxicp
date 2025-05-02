package org.maxicp.xcsp3;

import org.maxicp.util.exception.NotYetImplementedException;
import org.xcsp.common.Condition;
import org.xcsp.common.Types;
import org.xcsp.common.Types.TypeVar;
import org.xcsp.common.domains.Domains;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.parser.callbacks.XCallbacks2;
import org.xcsp.parser.entries.XConstraints;
import org.xcsp.parser.entries.XConstraints.CChild;
import org.xcsp.parser.entries.XVariables;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An extension of the default XCallback parser that provides default decompositions for the most complex constraints.
 *
 * Taken from the source code of OscaR (the file having been written by the same author)
 */
public abstract class XCallbacksDecomp implements XCallbacks2 {
    protected boolean CONVERT_SYMBOLIC = true;
    protected HashMap<String, Integer> symbolicValues;
    protected ArrayList<String> symbolicValuesReversed;
    protected HashMap<String, XVariables.XVarInteger> symbolicVariables;

    protected String reservedArray = "oscarReserved";
    protected int currentReservedArrayIdx;
    public XCallbacksDecomp() {
        symbolicValues = new HashMap<>();
        symbolicValuesReversed = new ArrayList<>();
        symbolicVariables = new HashMap<>();
        currentReservedArrayIdx = 0;
    }

    /**
     * Generate a variable id that is unique and will not be sent in the solution
     */
    protected String generateReservedArrayIdx() {
        currentReservedArrayIdx++;
        return reservedArray + "[" + Integer.toString(currentReservedArrayIdx) + "]";
    }

    /**
     * Builds a new symbolic variable.
     *
     * This particular version automatically maps to buildVarInteger if CONVERT_SYMBOLIC is true
     */
    @Override
    public void buildVarSymbolic(XVariables.XVarSymbolic x, String[] values) {
        if(CONVERT_SYMBOLIC) {
            // map all symbolic values to integer values
            for(String v: values) {
                if(!symbolicValues.containsKey(v)) {
                    symbolicValuesReversed.add(v);
                    symbolicValues.put(v, symbolicValues.size());
                }
            }

            // compute the effective domain
            int[] domain = Arrays.stream(values).mapToInt(v -> symbolicValues.get(v)).toArray();

            // create a std XVarInteger
            XVariables.XVarInteger var = (XVariables.XVarInteger)XVariables.XVar.build(x.id, TypeVar.integer, new Domains.Dom(domain));
            buildVarInteger(var, domain);

            // store it for future reference
            symbolicVariables.put(x.id, var);
        }
        else {
            unimplementedCase(x.id);
        }
    }

    /**
     * All Different constraint on Symbolic variables.
     *
     * Maps to buildCtrAllDifferent with Integer variables if CONVERT_SYMBOLIC is true
     */
    @Override
    public void buildCtrAllDifferent(String id, XVariables.XVarSymbolic[] lists) {
        if(CONVERT_SYMBOLIC)
            buildCtrAllDifferent(id, Arrays.stream(lists).map(x -> symbolicVariables.get(x.id)).toArray(XVariables.XVarInteger[]::new));
        else
            unimplementedCase(id);
    }

    /**
     * Extension constraint for Symbolic Variables
     *
     * Maps to buildCtrExtension with Integer variables if CONVERT_SYMBOLIC is true
     */
    @Override
    public void buildCtrExtension(String id, XVariables.XVarSymbolic x, String[] values, boolean positive, Set<Types.TypeFlag> flags) {
        if(CONVERT_SYMBOLIC)
            buildCtrExtension(id, symbolicVariables.get(x.id), Arrays.stream(values).mapToInt(i -> symbolicValues.get(i)).toArray(), positive, flags);
        else
            unimplementedCase(id);
    }

    protected XNodeLeaf<XVariables.XVarInteger> convertSymbolicTreeLeaf(XNodeLeaf<XVariables.XVarSymbolic> leaf) {
        if(leaf.type == Types.TypeExpr.VAR && leaf.value instanceof XVariables.XVarSymbolic)
            return new XNodeLeaf<>(Types.TypeExpr.VAR, symbolicVariables.get(((XVariables.XVarSymbolic)leaf.value).id));
        if(leaf.type == Types.TypeExpr.SYMBOL)
            return new XNodeLeaf<>(Types.TypeExpr.LONG, (long)symbolicValues.get((String)leaf.value));
        return new XNodeLeaf<>(leaf.getType(), leaf.value);
    }

    @SuppressWarnings("unchecked")
    protected XNodeParent<XVariables.XVarInteger> convertSymbolicTree(XNodeParent<XVariables.XVarSymbolic> syntaxTreeRoot) {

        return new XNodeParent<>(syntaxTreeRoot.type, Arrays.stream(syntaxTreeRoot.sons).map(x -> {
            if(x instanceof XNodeParent)
                return convertSymbolicTree((XNodeParent<XVariables.XVarSymbolic>)x);
            else
                return convertSymbolicTreeLeaf((XNodeLeaf<XVariables.XVarSymbolic>)x);
        }).toArray(XNode[]::new));
    }

    /**
     * Intension constraint for Symbolic Variables
     *
     * Maps to buildCtrIntension with Integer variables if CONVERT_SYMBOLIC is true
     */
    @Override
    public void buildCtrIntension(String id, XVariables.XVarSymbolic[] scope, XNodeParent<XVariables.XVarSymbolic> syntaxTreeRoot) {
        if(CONVERT_SYMBOLIC)
            buildCtrIntension(id, Arrays.stream(scope).map(x -> symbolicVariables.get(x.id)).toArray(XVariables.XVarInteger[]::new), convertSymbolicTree(syntaxTreeRoot));
        else
            unimplementedCase(id);
    }

    /**
     * Extension constraint for Symbolic Variables
     *
     * Maps to buildCtrExtension with Integer variables if CONVERT_SYMBOLIC is true
     */
    @Override
    public void buildCtrExtension(String id, XVariables.XVarSymbolic[] list, String[][] tuples, boolean positive, Set<Types.TypeFlag> flags) {
        buildCtrExtension(id,
                Arrays.stream(list).map(x -> symbolicVariables.get(x.id)).toArray(XVariables.XVarInteger[]::new),
                Arrays.stream(tuples).map(x -> Arrays.stream(x).mapToInt(y -> symbolicValues.get(y)).toArray()).toArray(int[][]::new),
                positive, flags);
    }

    /**
     * All Diff Matrix.
     * This particular version maps all the line/columns to buildCtrAllDifferent.
     * @param id
     * @param matrix
     */
    @Override
    public void buildCtrAllDifferentMatrix(String id, XVariables.XVarInteger[][] matrix) {
        for(XVariables.XVarInteger[] line: matrix)
            this.buildCtrAllDifferent(id, line);

        IntStream.range(0, matrix[0].length).forEach(i -> buildCtrAllDifferent(id, Arrays.stream(matrix).map(l -> l[i]).toArray(XVariables.XVarInteger[]::new)));
    }

    /**
     * Compute possible ends for given job starts and lenghts
     */
    protected XVariables.XVarInteger[] buildEndsFromStartAndLength(XVariables.XVarInteger[] starts, XVariables.XVarInteger[] lengths) {
        XVariables.XVarInteger[] output = new XVariables.XVarInteger[starts.length];
        for(int i = 0; i < starts.length; i++) {
            int min = (int)(((Domains.Dom)starts[i].dom).firstValue() + ((Domains.Dom)lengths[i].dom).firstValue());
            int max = (int)(((Domains.Dom)starts[i].dom).lastValue() + ((Domains.Dom)lengths[i].dom).lastValue());
            output[i] = (XVariables.XVarInteger)XVariables.XVar.build(generateReservedArrayIdx(), TypeVar.integer, new Domains.Dom(min, max));
            buildVarInteger(output[i], min, max);
        }
        return output;
    }

    /**
     * Compute possible ends for given job starts and lenghts
     */
    protected XVariables.XVarInteger[] buildEndsFromStartAndLength(XVariables.XVarInteger[] starts, int[] lengths) {
        XVariables.XVarInteger[] output = new XVariables.XVarInteger[starts.length];
        for(int i = 0; i < starts.length; i++) {
            int min = (int)(((Domains.Dom)starts[i].dom).firstValue())+lengths[i];
            int max = (int)(((Domains.Dom)starts[i].dom).lastValue())+lengths[i];
            output[i] = (XVariables.XVarInteger)XVariables.XVar.build(generateReservedArrayIdx(), TypeVar.integer, new Domains.Dom(min, max));
            buildVarInteger(output[i], min, max);
        }
        return output;
    }

    /**
     * Cumulative constraint. Infer the parameters ends from origins and length
     */
    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, int[] heights, Condition condition) {
        buildCtrCumulative(id, origins, lengths, buildEndsFromStartAndLength(origins, lengths), heights, condition);
    }

    /**
     * Cumulative constraint. Infer the parameters ends from origins and length
     */
    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, int[] lengths, XVariables.XVarInteger[] heights, Condition condition) {
        buildCtrCumulative(id, origins, lengths, buildEndsFromStartAndLength(origins, lengths), heights, condition);
    }

    /**
     * Cumulative constraint. Infer the parameters ends from origins and length
     */
    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, int[] heights, Condition condition) {
        buildCtrCumulative(id, origins, lengths, buildEndsFromStartAndLength(origins, lengths), heights, condition);
    }

    /**
     * Cumulative constraint. Infer the parameters ends from origins and length
     */
    @Override
    public void buildCtrCumulative(String id, XVariables.XVarInteger[] origins, XVariables.XVarInteger[] lengths, XVariables.XVarInteger[] heights, Condition condition) {
        buildCtrCumulative(id, origins, lengths, buildEndsFromStartAndLength(origins, lengths), heights, condition);
    }

    /**
     * Lex-matrix constraint.
     *
     * Map to the std. Lex constraint, on the columns and on the rows
     *
     * @param id
     * @param matrix
     * @param operator
     */
    @Override
    public void buildCtrLexMatrix(String id, XVariables.XVarInteger[][] matrix, Types.TypeOperatorRel operator) {
        buildCtrLex(id, matrix, operator);

        XVariables.XVarInteger[][] mt = new XVariables.XVarInteger[matrix[0].length][matrix.length];
        for (int r = 0; r < matrix.length; r++)
            for (int c = 0; c < matrix[0].length; c++)
                mt[c][r] = matrix[r][c];

        buildCtrLex(id, mt, operator);
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, int[][] lengths, boolean zeroIgnored) {
        //TODO semantics of zeroIgnored ???
        if(origins[0].length == 2) //2D case
            buildCtrNoOverlap2D(id,
                    Arrays.stream(origins).map(e -> e[0]).toArray(XVariables.XVarInteger[]::new),
                    Arrays.stream(lengths).mapToInt(e -> e[0]).toArray(),
                    Arrays.stream(origins).map(e -> e[1]).toArray(XVariables.XVarInteger[]::new),
                    Arrays.stream(lengths).mapToInt(e -> e[1]).toArray());
        else
            unimplementedCase();
    }

    @Override
    public void buildCtrNoOverlap(String id, XVariables.XVarInteger[][] origins, XVariables.XVarInteger[][] lengths, boolean zeroIgnored) {
        if(origins[0].length == 2) //2D case
            buildCtrNoOverlap2D(id,
                    Arrays.stream(origins).map(e -> e[0]).toArray(XVariables.XVarInteger[]::new),
                    Arrays.stream(lengths).map(e -> e[0]).toArray(XVariables.XVarInteger[]::new),
                    Arrays.stream(origins).map(e -> e[1]).toArray(XVariables.XVarInteger[]::new),
                    Arrays.stream(lengths).map(e -> e[1]).toArray(XVariables.XVarInteger[]::new));
        else
            unimplementedCase();
    }

    public void buildCtrNoOverlap2D(String id, XVariables.XVarInteger[] x, XVariables.XVarInteger[] dx, XVariables.XVarInteger[] y, XVariables.XVarInteger[] dy) {
        unimplementedCase();
    }

    public void buildCtrNoOverlap2D(String id, XVariables.XVarInteger[] x, int[] dx, XVariables.XVarInteger[] y, int[] dy) {
        unimplementedCase();
    }

    /**
     * AllDifferentList constraint.
     *
     * For each pair of tuple (x,y), maps to
     * Or(ne(x[0],y[0]), ne(x[1],y[1]), ne(x[2],y[2]), ...)
     * @param id
     * @param lists
     */
    @Override
    public void buildCtrAllDifferentList(String id, XVariables.XVarInteger[][] lists) {
        for(int i = 0; i < lists.length; i++) {
            XVariables.XVarInteger[] tuple1 = lists[i];
            for(int j = i+1; j < lists.length; j++) {
                XVariables.XVarInteger[] tuple2 = lists[j];

                XVariables.XVarInteger[] scope = new XVariables.XVarInteger[tuple1.length*2];
                System.arraycopy(tuple1, 0, scope, 0, tuple1.length);
                System.arraycopy(tuple2, 0, scope, tuple1.length, tuple1.length);


                XNodeParent<XVariables.XVarInteger>[] orVars = new XNodeParent[tuple1.length];

                for(int k = 0; k < tuple1.length; k++) {
                    XNodeLeaf<XVariables.XVar> v1 = new XNodeLeaf<>(Types.TypeExpr.VAR, tuple1[k]);
                    XNodeLeaf<XVariables.XVar> v2 = new XNodeLeaf<>(Types.TypeExpr.VAR, tuple2[k]);
                    orVars[k] = new XNodeParent<>(Types.TypeExpr.NE, new XNode[]{v1, v2});
                }
                XNodeParent<XVariables.XVarInteger> or = new XNodeParent<>(Types.TypeExpr.OR, orVars);

                CChild[] childs = new CChild[] { new CChild(Types.TypeChild.function,or)};
                implem().ctrLoaderInteger.load(new XConstraints.XCtr(Types.TypeCtr.intension,childs));
                // todo: should be a buildCtrClause instead of or
            }
        }
    }

    /**
     * Helper for buildCtrAllDifferentList.
     *
     * Build all possibles values that can be taken by a tuple of vars, and creates a new table linking all the instances to a given id (stores the relation tuple -> id in instanceToId)
     * For simplicity of implem, keys of instances are string, constructed while iterating.
     */
    /*protected void generateAllInstanceFromTuple(XVariables.XVarInteger[] tuple, int[] currentContent, int posInTuple, String accumulator,
                                                HashMap<String, Integer> instanceToId, ArrayList<int[]> table) {
        if(posInTuple == tuple.length) {
            int id = instanceToId.size();
            if(instanceToId.containsKey(accumulator))
                id = instanceToId.get(accumulator);
            else
                instanceToId.put(accumulator, id);
            //currentContent is of size tuple.length+1, so it's ok
            currentContent[posInTuple] = id;
            int[] copy = new int[currentContent.length];
            System.arraycopy(currentContent, 0, copy, 0, currentContent.length);
            table.add(copy);
            return;
        }
        XVariables.XVarInteger v = tuple[posInTuple];
        XDomains.XDomInteger domain = (XDomains.XDomInteger)v.dom;
        int[] intDomain = XValues.IntegerEntity.toIntArray((XValues.IntegerEntity[])domain.values, Integer.MAX_VALUE);
        for(int x: intDomain) {
            currentContent[posInTuple] = x;
            generateAllInstanceFromTuple(tuple, currentContent, posInTuple+1, accumulator+","+Integer.toString(x), instanceToId, table);
        }
    }*/

    /**
     * AllDifferentList constraint.
     *
     * Map to Extension constraint (each "tuple instance" points to an id) and AllDifferent (on ids)
     *
     * Too hungry in memory...
     */
    /*@Override
    public void buildCtrAllDifferentList(String id, XVariables.XVarInteger[][] lists) {
        HashMap<String, Integer> instanceToId = new HashMap<>();
        ArrayList<int[]>[] tables = new ArrayList[lists.length];
        for(int i = 0; i < lists.length; i++) {
            XVariables.XVarInteger[] tuple = lists[i];
            tables[i] = new ArrayList<>();
            int[] currentContent = new int[tuple.length+1];
            generateAllInstanceFromTuple(tuple, currentContent, 0, "", instanceToId, tables[i]);
        }

        // We now have all our tables, let's create the needed variables
        XVariables.XVarInteger[] allDiffVars = new XVariables.XVarInteger[lists.length];
        for(int i = 0; i < lists.length; i++) {
            allDiffVars[i] = (XVariables.XVarInteger) XVariables.XVar.build(generateReservedArrayIdx(), XVariables.TypeVar.integer, new XDomains.XDomInteger(0, instanceToId.size()));
            buildVarInteger(allDiffVars[i], 0, instanceToId.size());
        }

        // Let's create the allDifferent
        buildCtrAllDifferent(id, allDiffVars);

        // Now we finalize the tables and post them
        for(int i = 0; i < lists.length; i++) {
            XVariables.XVarInteger[] tuple = lists[i];

            // Create a new tuple with the "id"
            XVariables.XVarInteger[] completeTuple = new XVariables.XVarInteger[tuple.length+1];
            System.arraycopy(tuple, 0, completeTuple, 0, tuple.length);
            completeTuple[tuple.length] = allDiffVars[i];

            // Generate the table
            int[][] table = new int[tables[i].size()][tuple.length+1];
            table = tables[i].toArray(table);

            // Post the table
            buildCtrExtension(id, completeTuple, table, true, new HashSet<>());
        }
    }*/

    /**
     * Generate an instantiation constraint that is the solution given in values.
     *
     * The main purpose of this function is to parse back XVarInteger to XVarSymbolic if it was previously converted
     *
     * @param allVariables list of variables in the solution, given by their id
     * @param allValues values[i] is the value assigned to variables[i]
     */
    public String generateInstantiationWithSymbolic(String[] allVariables, int[] allValues) {
        // Remove variables that have been generated
        ArrayList<String> variables = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        for(int i = 0; i < allVariables.length; i++) {
            if(!allVariables[i].startsWith(reservedArray)) {
                variables.add(allVariables[i]);
                values.add(allValues[i]);
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<instantiation>\n\t<list>\n\t\t");
        builder.append(String.join(" ", variables));
        builder.append("\n\t</list>\n\t<values>\n\t\t");
        for(int i = 0; i < values.size(); i++) {
            if(symbolicVariables.containsKey(variables.get(i)))
                builder.append(symbolicValuesReversed.get(values.get(i)));
            else
                builder.append(values.get(i));
            builder.append(' ');
        }
        builder.append("\n\t</values>\n</instantiation>");
        return builder.toString();
    }

    @Override
    public Object unimplementedCase(Object... objects) {
        System.out.println("\n\n**********************");
        System.out.println("Missing Implementation");
        StackTraceElement[] t = Thread.currentThread().getStackTrace();
        System.out.println("  Method " + t[2].getMethodName());
        System.out.println("  Class " + t[2].getClassName());
        System.out.println("  Line " + t[2].getLineNumber());
        System.out.println("**********************");
        System.out.println("Context:" + (String) Stream.of(objects).filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining("\n")));
        throw new NotYetImplementedException();
    }
}