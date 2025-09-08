package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.DistanceNew;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Random;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.makeDfs;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class PCTSP {

    public static void main(String[] args) {

        PCTSP.PCTSPInstance instance = new PCTSP.PCTSPInstance("data/TSP/gr21.xml", 42);

        // ===================== read & preprocessing =====================

        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        // a seqvar needs both a start and an end node
        // we add two dumy nodes n and n+1 for the start and end node
        int[][] distance = new int[n + 2][n + 2];
        for (int i = 0; i < n; i++) {
            System.arraycopy(distanceMatrix[i], 0, distance[i], 0, n);
            distance[i][n] = 0;
            distance[n][i] = 0;
            distance[i][n+1] = 0;
            distance[n+1][i] = 0;
        }

        // ===================== decision variables =====================

        CPSolver cp = makeSolver();
        // route for the traveler
        CPSeqVar tour = makeSeqVar(cp, n + 2, n, n + 1);
        // distance traveled. This is the objective to minimize
        CPIntVar totLength = makeIntVar(cp, 0, 10000);

        // ===================== constraints =====================

        CPBoolVar [] required = makeBoolVarArray(n, node -> tour.isNodeRequired(node));

        CPIntVar [] collectedPrice = makeIntVarArray(n, node -> mul(required[node], instance.price[node]));

        CPIntVar totPrice = sum(collectedPrice);


        for (int node = 0; node < n; node++) {
            //cp.post(eq(required[node], 0));
        }

        // capture the distance traveled according to the distance matrix
        //cp.post(new Distance(tour, distance, totLength));
        cp.post(new DistanceNew(tour, distance, totLength));


        CPIntVar objVar = sum(totPrice, minus(totLength));

        // maximize collected price - distance
        Objective obj = cp.maximize(objVar);

        // ===================== search =====================

        int[] nodes = new int[n];
        DFSearch dfs = makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return distance[pred][node] + distance[node][succ] - distance[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(
                            () -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)),
                            () -> cp.post(eq(required[node], 0)));
                }
        );

        // ===================== solve the problem =====================

        long init = System.currentTimeMillis();
        dfs.onSolution(() -> {
            System.out.println("objective:"+objVar);
            System.out.println(String.format("length: %d price: %d", totLength.min(), totPrice.min()));
            double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
            System.out.println(tour);
            System.out.println("number of nodes:" + tour.nNode());
            System.out.printf("elapsed: %.3f%n", elapsedSeconds);
            System.out.println("-------");
        });

        SearchStatistics stats = dfs.optimize(obj);
        double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("elapsed - total: %.3f%n", elapsedSeconds);
        System.out.println(stats);
    }


    public static class PCTSPInstance {

        public int n; // number of nodes
        int [] price;
        public int[][] distanceMatrix;

        /**
         * Read TSP Instance from xml
         * See http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/XML-TSPLIB/Description.pdf
         *
         * @param xmlPath path to the file
         * @param seed random seed for generating prices
         */
        public PCTSPInstance(String xmlPath, int seed) {
            // Instantiate the Factory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            int obj = -1;
            try {

                // optional, but recommended
                // process XML securely, avoid attacks like XML External Entities (XXE)
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

                // parse XML file
                DocumentBuilder db = dbf.newDocumentBuilder();

                Document doc = db.parse(new File(xmlPath));
                doc.getDocumentElement().normalize();

                NodeList list = doc.getElementsByTagName("vertex");

                n = list.getLength();

                Random rand = new Random(seed);

                price = new int[n];
                for (int i = 0; i < n; i++) {
                    price[i] = 80 + rand.nextInt(80);
                }

                distanceMatrix = new int[n][n];

                for (int i = 0; i < n; i++) {
                    NodeList edgeList = list.item(i).getChildNodes();
                    for (int v = 0; v < edgeList.getLength(); v++) {

                        Node node = edgeList.item(v);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            String cost = element.getAttribute("cost");
                            String adjacentNode = element.getTextContent();
                            int j = Integer.parseInt(adjacentNode);
                            distanceMatrix[i][j] = (int) Math.rint(Double.parseDouble(cost));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
