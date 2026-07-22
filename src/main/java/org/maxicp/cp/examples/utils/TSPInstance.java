package org.maxicp.cp.examples.utils;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class TSPInstance {

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