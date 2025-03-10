/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.seqvar.Distance;
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

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSPSeqVar {

    public static class TSPInstance {

        public int[][] distanceMatrix;
        public int n;
        public final int objective;

        /**
         * Read TSP Instance from xml
         * See http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/XML-TSPLIB/Description.pdf
         *
         * @param xmlPath path to the file
         */
        public TSPInstance(String xmlPath) {
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

                NodeList objlist = doc.getElementsByTagName("objective");
                if (objlist.getLength() > 0) {
                    obj = Integer.parseInt(objlist.item(0).getTextContent());
                }

                NodeList list = doc.getElementsByTagName("vertex");

                n = list.getLength();
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

            this.objective = obj;
        }


    }

    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/gr21.xml");

        // ===================== read & preprocessing =====================

        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;


        // a seqvar needs both a start and an end node
        // here the node 0 will be considered as the start, and duplicated to set the end
        // the distance matrix is thus extended
        int[][] distance = new int[n + 1][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(distanceMatrix[i], 0, distance[i], 0, n);
            distance[i][n] = distanceMatrix[i][0];
            distance[n][i] = distanceMatrix[0][i];
        }

        // ===================== decision variables =====================

        CPSolver cp = makeSolver();
        // route for the traveler
        CPSeqVar tour = makeSeqVar(cp, n + 1, 0, n);
        // distance traveled. This is the objective to minimize
        CPIntVar totLength = makeIntVar(cp, 0, 10000);

        // ===================== constraints =====================

        // all nodes must be visited in a tsp
        for (int node = 0; node < n + 1; node++) {
            tour.require(node);
        }
        // capture the distance traveled according to the distance matrix
        cp.post(new Distance(tour, distance, totLength));
        // objective consists in minimizing the traveled distance
        Objective obj = cp.minimize(totLength);

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
                    return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.removeDetour(tour, bestPred, node, succ)));
                }
        );

        // ===================== solve the problem =====================

        long init = System.currentTimeMillis();
        dfs.onSolution(() -> {
            double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
            System.out.printf("elapsed: %.3f%n", elapsedSeconds);
            System.out.println(totLength);
            System.out.println(tour);
            System.out.println("-------");
        });

        SearchStatistics stats = dfs.optimize(obj);
        double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("elapsed - total: %.3f%n", elapsedSeconds);
        System.out.println(stats);
    }
}
