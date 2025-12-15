package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class OTSPBench extends Benchmark {

    private static class OptionalTSPInstance {

        public int nNodes;
        public int start;
        public int nRequired;
        public int[][] travelCosts;

        /**
         * Read TSP Instance from xml
         * See http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/XML-TSPLIB/Description.pdf
         *
         * @param xmlPath path to the file
         */
        public OptionalTSPInstance(String xmlPath) {
            // Instantiate the Factory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {

                // optional, but recommended
                // process XML securely, avoid attacks like XML External Entities (XXE)
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

                // parse XML file
                DocumentBuilder db = dbf.newDocumentBuilder();

                Document doc = db.parse(new File(xmlPath));
                doc.getDocumentElement().normalize();

                NodeList nRequiredList = doc.getElementsByTagName("nRequired");
                nRequired = Integer.parseInt(nRequiredList.item(0).getTextContent());

                NodeList list = doc.getElementsByTagName("vertex");

                nNodes = list.getLength();
                travelCosts = new int[nNodes][nNodes];
                start = 0;

                for (int i = 0; i < nNodes; i++) {
                    NodeList edgeList = list.item(i).getChildNodes();
                    for (int v = 0; v < edgeList.getLength(); v++) {

                        Node node = edgeList.item(v);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            String cost = element.getAttribute("cost");
                            String adjacentNode = element.getTextContent();
                            int j = Integer.parseInt(adjacentNode);
                            travelCosts[i][j] = (int) Math.rint(Double.parseDouble(cost));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    OptionalTSPInstance instance;
    CPSolver cp;
    CPSeqVar tour;
    int nNodes;
    int start;
    int end;
    int nRequired;
    int[][] distance;
    CPIntVar totDistance;

    public OTSPBench(String[] args) {
        super(args);
    }

    @Override
    protected DFSearch makeDFSearch() {
        int[] nodes = new int[nNodes];
        return makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points.
                    // Ties are broken by selecting the node with smallest id
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return distance[pred][node] + distance[node][succ] - distance[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(
                            () -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));

                }
        );
    }

    /**
     * Select min with tie breaks using the smallest int value
     */
    private static<N extends Comparable<N>> OptionalInt selectMin(int[] x, int n, Predicate<Integer> p, Function<Integer, N> f) {
        return Arrays.stream(x).limit(n).filter(p::test).reduce((i, j) -> {
            int comparison = f.apply(i).compareTo(f.apply(j));
            if (comparison == 0) {
                return Math.min(i, j);
            } else {
                return comparison < 0 ? i : j;
            }
        });
    }

    @Override
    protected Objective makeObjective() {
        return cp.minimize(totDistance);
    }

    @Override
    protected double getObjectiveValue() {
        return totDistance.min();
    }

    @Override
    protected void makeModel(String instancePath) {

        // ===================== read & preprocessing =====================

        instance = new OptionalTSPInstance(instancePath);
        start = instance.start;
        nNodes = instance.nNodes;
        end = nNodes;
        nRequired = instance.nRequired;
        // a SeqVar needs both a start and an end node, duplicate the start
        distance = new int[nNodes + 1][nNodes + 1];
        int lengthUpperBound = 0;
        for (int i = 0; i < nNodes; i++) {
            System.arraycopy(instance.travelCosts[i], 0, distance[i], 0, nNodes);
            distance[i][nNodes] = instance.travelCosts[i][0];
            distance[nNodes][i] = instance.travelCosts[0][i];
            lengthUpperBound += Arrays.stream(instance.travelCosts[i]).max().getAsInt();
        }
        makeTriangularInequality(distance); // ensure triangular inequality

        // ===================== decision variables =====================

        cp = makeSolver();
        // route for the traveler
        tour = makeSeqVar(cp, nNodes + 1, start, end);
        // distance traveled
        totDistance = makeIntVar(cp, 0, lengthUpperBound);

        // ===================== constraints =====================

        // require a given number of nodes to be visited
        CPIntVar[] isNodeRequired = new CPIntVar[nNodes];
        for (int node = 0 ; node < nNodes ; node++) {
            isNodeRequired[node] = tour.isNodeRequired(node);
        }
        cp.post(sum(isNodeRequired, nRequired));

        // tracks the distance over the sequence
        addDistanceConstraint(tour, distance, totDistance);
    }

    /**
     * Example of usage:
     * -f "data/OTSP/instance_30_30.xml" -m original
     * @param args
     */
    public static void main(String[] args) {
        new OTSPBench(args).solve();
    }
}
