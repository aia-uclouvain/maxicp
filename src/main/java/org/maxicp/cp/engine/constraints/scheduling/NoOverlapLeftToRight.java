/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import java.util.Arrays;
import java.util.Comparator;

public class NoOverlapLeftToRight {

    public enum Outcome {
        NO_CHANGE, CHANGE, INCONSISTENCY
    }

    public final int[] startMin, endMax;
    private final int[] startMinNew, startMax, duration, endMin;
    int n;
    private final Integer[] permEst, rankEst, permLct, permLst, permEct;

    private final boolean[] inserted;

    private final ThetaTree thetaTree;

    public NoOverlapLeftToRight(int nMax) {
        startMin = new int[nMax];
        startMinNew = new int[nMax];
        startMax = new int[nMax];
        duration = new int[nMax];
        endMin = new int[nMax];
        endMax = new int[nMax];

        permEst = new Integer[nMax];
        rankEst = new Integer[nMax];
        permLct = new Integer[nMax];
        permLst = new Integer[nMax];
        permEct = new Integer[nMax];
        inserted = new boolean[nMax];

        thetaTree = new ThetaTree(nMax);
    }

    /**
     * Applies all the filtering algorithms Overload Checker, Not-Last, Detectable Precedence
     * in a loop until a fix point is reached, or an inconsistency is detected.
     * Those algorithms are the ones of Peter Vilim's thesis.
     *
     * @param startMin the minimum start time of each activity
     * @param duration the duration of each activity
     * @param endMax   the maximum end time of each activity
     * @param n        a number between 0 and startMin.length-1, is the number of activities to consider (prefix),
     *                 The other ones are just ignored
     * @return the outcome of the filtering, either NO_CHANGE, CHANGE or INCONSISTENCY.
     * If a change is detected, the time windows (startMin and endMax) are reduced.
     */
    public Outcome filter(int[] startMin, int[] duration, int[] endMax, int n) {
        update(startMin, duration, endMax, n);
        return fixPoint();
    }


    /**
     * @return false if an inconsistency is detected, true otherwise
     */
    private Outcome fixPoint() {
        boolean fixed = false;
        boolean changed = false;
        while (!fixed) {
            fixed = true;
            if (!overLoadChecker()) return Outcome.INCONSISTENCY;
            fixed = !detectablePrecedence();
            if (inconsistency()) return Outcome.INCONSISTENCY;
            fixed = fixed & !notLast();
            if (inconsistency()) return Outcome.INCONSISTENCY;
            if (!fixed) changed = true;
        }
        if (changed) return Outcome.CHANGE;
        else return Outcome.NO_CHANGE;
    }

    protected void update(int[] startMin, int[] duration, int[] endMax, int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            this.startMin[i] = startMin[i];
            this.startMinNew[i] = startMin[i];
            this.startMax[i] = endMax[i] - duration[i];
            this.duration[i] = duration[i];
            this.endMin[i] = startMin[i] + duration[i];
            this.endMax[i] = endMax[i];

            this.permEst[i] = i;
            this.permLct[i] = i;
            this.permLst[i] = i;
            this.permEct[i] = i;
        }
        Arrays.sort(permEst, 0, n, Comparator.comparingInt(i -> startMin[i]));
        for (int i = 0; i < n; i++) {
            rankEst[permEst[i]] = i;
        }
    }

    /**
     * @return false if the overload checker detects an overload, true if no overload is detected
     */
    protected boolean overLoadChecker() {
        update(startMin, duration, endMax, n); // useless ?
        Arrays.sort(permLct, 0, n, Comparator.comparingInt(i -> endMax[i]));
        thetaTree.reset();
        for (int i = 0; i < n; i++) {
            int activity = permLct[i];
            thetaTree.insert(rankEst[activity], endMin[activity], duration[activity]);
            if (thetaTree.getECT() > endMax[activity]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if one domain was changed by the detectable precedence algo
     */
    protected boolean detectablePrecedence() {
        update(startMin, duration, endMax, n);
        Arrays.sort(permLst, 0, n, Comparator.comparingInt(i -> endMax[i] - duration[i]));
        Arrays.sort(permEct, 0, n, Comparator.comparingInt(i -> startMin[i] + duration[i]));
        Arrays.fill(inserted, 0, n, false);
        int idxj = 0; // j = permLst[idxj];
        thetaTree.reset();
        for (int i = 0; i < n; i++) {
            int acti = permEct[i];
            while (idxj < n && endMin[acti] > startMax[permLst[idxj]]) {
                int j = permLst[idxj];
                inserted[j] = true;
                thetaTree.insert(rankEst[j], endMin[j], duration[j]);
                idxj++;
            }
            if (inserted[acti]) {
                thetaTree.remove(rankEst[acti]);
                startMinNew[acti] = Math.max(startMin[acti], thetaTree.getECT());
                thetaTree.insert(rankEst[acti], endMin[acti], duration[acti]);
            } else {
                startMinNew[acti] = Math.max(startMin[acti], thetaTree.getECT());
            }
        }

        boolean changed = false;
        for (int i = 0; i < n; i++) {
            changed |= startMinNew[i] > startMin[i];
            startMin[i] = startMinNew[i];
        }
        return changed;
    }

    /**
     * @return true if one domain was changed by the not last algo
     */
    protected boolean notLast() {
        update(startMin, duration, endMax, n);
        boolean changed = false;
        Arrays.sort(permLst, 0, n, Comparator.comparingInt(i -> startMax[i]));
        Arrays.sort(permLct, 0, n, Comparator.comparingInt(i -> endMax[i]));
        Arrays.fill(inserted, 0, n, false);
        int idxj = 0;
        int j = permLst[idxj];
        thetaTree.reset();
        for (int i = 0; i < n; i++) {
            int acti = permLct[i];
            while (idxj < n && endMax[acti] > startMax[permLst[idxj]]) {
                j = permLst[idxj];
                inserted[j] = true;
                thetaTree.insert(rankEst[j], endMin[j], duration[j]);
                idxj++;
            }
            if (inserted[acti]) {
                thetaTree.remove(rankEst[acti]);
                if (thetaTree.getECT() > startMax[acti]) {
                    if (startMax[j] < endMax[acti]) {
                        endMax[acti] = startMax[j];
                        changed = true;
                    }
                    endMax[acti] = startMax[j];
                }
                thetaTree.insert(rankEst[acti], endMin[acti], duration[acti]);
            } else {
                if (thetaTree.getECT() > startMax[acti]) {
                    if (startMax[j] < endMax[acti]) {
                        endMax[acti] = startMax[j];
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean changed() {
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            changed |= startMinNew[i] > startMin[i];
            startMinNew[i] = startMin[i];
        }
        return false;
    }

    private boolean inconsistency() {
        for (int i = 0; i < n; i++) {
            if (startMin[i] > endMax[i]) return true;
        }
        return false;
    }
}