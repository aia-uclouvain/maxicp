package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.makeIntVarArray;
import static org.maxicp.cp.CPFactory.mul;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Tour Must See Problem.
 * <p>
 * An orienteering problem where the goal is to maximize the reward associated to the visit of points of interests by a
 * tourist.
 * Each node has an associated reward. Some nodes are marked as mandatory from the start and the other are optional.
 * The goal is to find the path maximizing the reward while keeping the traveled distance below a given threshold.
 * <p>
 * From Taylor, K., Lim, K. H., & Chan, J. (2018, April).
 * Travel itinerary recommendations with must-see points-of-interest.
 * In Companion proceedings of the the web conference 2018 (pp. 1198-1205).
 */
public class TMSPBench extends Benchmark {

    int n;
    int start;
    int end;
    int[][] distance;
    CPSolver cp;
    CPSeqVar tour;
    CPIntVar totalReward;
    CPIntVar sumDist;
    CPBoolVar[] required;
    CPIntVar[] reward;

    Instance instance;

    public TMSPBench(String[] args) {
        super(args);
    }


    static class Instance {

        public int n; // number of nodes
        public List<Integer> mandatory; // nodes that are mandatory in any solution
        public int start; // start node
        public int end; // end node
        public int[][] travelTime; // travel time between nodes
        public int[] duration; // service duration at a node
        public int maxTime; // maximum time for the whole trip (including service time)
        public int[] reward; // reward of every node


        public Instance(String filename) {
            try {
                // Read entire file as String
                String content = new String(Files.readAllBytes(Paths.get(filename)));

                // Parse JSON
                JSONObject json = new JSONObject(content);

                // Basic fields
                this.n = json.getInt("n_nodes");
                this.start = json.getInt("start");
                this.end = json.getInt("end");
                this.maxTime = json.getInt("time_budget");

                // Mandatory nodes
                this.mandatory = new ArrayList<>();
                JSONArray mandatoryJson = json.getJSONArray("mandatory");
                for (int i = 0; i < mandatoryJson.length(); i++) {
                    this.mandatory.add(mandatoryJson.getInt(i));
                }

                // Rewards
                this.reward = new int[n];
                JSONArray rewardsJson = json.getJSONArray("rewards");
                for (int i = 0; i < n; i++) {
                    this.reward[i] = rewardsJson.getInt(i);
                }

                // Service durations
                this.duration = new int[n];
                JSONArray durationJson = json.getJSONArray("service_time_min");
                for (int i = 0; i < n; i++) {
                    this.duration[i] = durationJson.getInt(i);
                }

                // Travel time matrix
                this.travelTime = new int[n][n];
                JSONArray travelJson = json.getJSONArray("travel_time_min");
                for (int i = 0; i < n; i++) {
                    JSONArray row = travelJson.getJSONArray(i);
                    for (int j = 0; j < n; j++) {
                        this.travelTime[i][j] = row.getInt(j);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException("Could not read instance file: " + filename, e);
            } catch (Exception e) {
                throw new RuntimeException("Error while parsing instance JSON: " + filename, e);
            }
        }

    }

    protected DFSearch makeDFSBinaryBranching() {
        int[] nodes = new int[instance.n];
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

    @Override
    protected DFSearch makeDFSearch() {
        return makeDFSBinaryBranching();
    }

    @Override
    protected Objective makeObjective() {
        return cp.maximize(totalReward);
    }

    @Override
    protected double getObjectiveValue() {
        return totalReward.max();
    }

    @Override
    protected CPSeqVar getSeqVar() {
        return tour;
    }

    @Override
    protected void makeModel(String instancePath) {
        instance = new Instance(instancePath);

        // ===================== read & preprocessing =====================
        n = instance.n;
        start = instance.start;
        end = instance.end;
        // distance takes into account the service duration of the origin
        distance = new int[n][n];
        for (int i = 0 ; i < n ; i++) {
            for (int j = 0 ; j < n ; j++) {
                distance[i][j] = instance.travelTime[i][j] + instance.duration[i];
            }
        }
        // takes into account the visit of the end node
        int maxTime = instance.maxTime - instance.duration[end];

        // ===================== decision variables =====================

        cp = makeSolver();
        // sequence variable representing the path from the start to the end
        tour = CPFactory.makeSeqVar(cp, n, start, end);
        // add the mandatory nodes
        for (int mandatory: instance.mandatory)
            tour.require(mandatory);
        // distance traveled
        sumDist = makeIntVar(cp, 0, maxTime);

        // ===================== auxiliary variables =====================

        required = makeBoolVarArray(n, node -> tour.isNodeRequired(node));

        // multiplication over required node: the reward associated to the visit of a node (= {0, reward})
        reward = makeIntVarArray(n, node -> mul(required[node], instance.reward[node]));

        // ===================== constraints =====================
        // total reward is the sum of individual visits
        totalReward = sum(reward);
        // constraint the maximum travel time that can be used (including service duration, which is in the matrix)
        addDistanceConstraint(tour, distance, sumDist);
    }

    // quick instance to test (about 4s with original filtering):
    // -f data/TMSP/Buda_20_4_0_5_loose_1140356424.json -m ORIGINAL
    public static void main(String[] args) {
        new TMSPBench(args).solve();
    }

}

// TourMustSeeProblemBench | data/TMSP/Buda_20_4_0_5_loose_1140356424.json | ORIGINAL | 8954.000 | 0 7 17 2 8 18 11 13 15 12 14 19 5 | 60.000 | 3.529 | 437048 | 218504 | 21 | true | [(t=0.057; nodes=23; fails=8; obj=5888.000) (t=0.065; nodes=27; fails=9; obj=6323.000) (t=0.079; nodes=145; fails=68; obj=6347.000) (t=0.110; nodes=633; fails=310; obj=6588.000) (t=0.141; nodes=1563; fails=774; obj=6753.000) (t=0.147; nodes=1761; fails=872; obj=6930.000) (t=0.148; nodes=1790; fails=886; obj=6945.000) (t=0.181; nodes=3122; fails=1551; obj=7040.000) (t=0.219; nodes=4568; fails=2273; obj=7088.000) (t=0.220; nodes=4604; fails=2290; obj=7638.000) (t=0.433; nodes=22891; fails=11432; obj=7648.000) (t=0.483; nodes=28867; fails=14419; obj=7873.000) (t=0.547; nodes=35306; fails=17637; obj=8090.000) (t=0.834; nodes=72885; fails=36426; obj=8117.000) (t=0.840; nodes=73337; fails=36651; obj=8285.000) (t=0.931; nodes=83998; fails=41980; obj=8450.000) (t=0.938; nodes=85051; fails=42506; obj=8642.000) (t=1.473; nodes=151475; fails=75718; obj=8645.000) (t=1.562; nodes=163292; fails=81624; obj=8675.000) (t=1.703; nodes=181132; fails=90543; obj=8902.000) (t=2.895; nodes=344467; fails=172211; obj=8954.000)] | -f data/TMSP/Buda_20_4_0_5_loose_1140356424.json -m ORIGINAL
// TourMustSeeProblemBench | data/TMSP/Buda_20_4_0_5_loose_1140356424.json | MIN_DETOUR | 8954.000 | 0 7 17 2 8 18 11 13 15 12 14 19 5 | 60.000 | 0.362 | 11746 | 5850 | 24 | true | [(t=0.055; nodes=5; fails=1; obj=4805.000) (t=0.063; nodes=9; fails=2; obj=4997.000) (t=0.064; nodes=13; fails=2; obj=5888.000) (t=0.065; nodes=18; fails=3; obj=6323.000) (t=0.070; nodes=48; fails=18; obj=6347.000) (t=0.072; nodes=57; fails=21; obj=6588.000) (t=0.077; nodes=97; fails=40; obj=6753.000) (t=0.078; nodes=107; fails=43; obj=6930.000) (t=0.082; nodes=138; fails=59; obj=6945.000) (t=0.095; nodes=289; fails=133; obj=7040.000) (t=0.100; nodes=371; fails=173; obj=7088.000) (t=0.143; nodes=1347; fails=660; obj=7305.000) (t=0.145; nodes=1369; fails=670; obj=7538.000) (t=0.152; nodes=1539; fails=754; obj=7648.000) (t=0.154; nodes=1587; fails=777; obj=7706.000) (t=0.154; nodes=1590; fails=778; obj=7873.000) (t=0.164; nodes=1894; fails=928; obj=8090.000) (t=0.189; nodes=2628; fails=1294; obj=8285.000) (t=0.210; nodes=3312; fails=1634; obj=8450.000) (t=0.214; nodes=3442; fails=1700; obj=8642.000) (t=0.258; nodes=5622; fails=2789; obj=8645.000) (t=0.263; nodes=5886; fails=2920; obj=8675.000) (t=0.274; nodes=6347; fails=3149; obj=8902.000) (t=0.343; nodes=10626; fails=5289; obj=8954.000)] | -f data/TMSP/Buda_20_4_0_5_loose_1140356424.json -m MIN_DETOUR
// TourMustSeeProblemBench | data/TMSP/Buda_20_4_0_5_loose_1140356424.json | MATCHING_SUCCESSOR | 8954.000 | 0 7 17 2 8 18 11 13 15 12 14 19 5 | 60.000 | 11.619 | 60310 | 30135 | 21 | true | [(t=0.089; nodes=23; fails=8; obj=5888.000) (t=0.097; nodes=27; fails=9; obj=6323.000) (t=0.126; nodes=121; fails=56; obj=6347.000) (t=0.218; nodes=435; fails=211; obj=6588.000) (t=0.331; nodes=943; fails=464; obj=6753.000) (t=0.360; nodes=1061; fails=522; obj=6930.000) (t=0.365; nodes=1082; fails=532; obj=6945.000) (t=0.526; nodes=1976; fails=978; obj=7040.000) (t=0.683; nodes=2724; fails=1351; obj=7088.000) (t=0.688; nodes=2752; fails=1364; obj=7638.000) (t=1.955; nodes=8883; fails=4428; obj=7648.000) (t=2.419; nodes=11367; fails=5669; obj=7873.000) (t=2.883; nodes=13558; fails=6763; obj=8090.000) (t=4.389; nodes=20517; fails=10242; obj=8117.000) (t=4.415; nodes=20679; fails=10322; obj=8285.000) (t=4.909; nodes=23384; fails=11673; obj=8450.000) (t=5.004; nodes=23997; fails=11979; obj=8642.000) (t=7.086; nodes=35613; fails=17788; obj=8645.000) (t=7.270; nodes=36707; fails=18332; obj=8675.000) (t=7.645; nodes=38884; fails=19419; obj=8902.000) (t=11.065; nodes=57624; fails=28790; obj=8954.000)] | -f data/TMSP/Buda_20_4_0_5_loose_1140356424.json -m MATCHING_SUCCESSOR