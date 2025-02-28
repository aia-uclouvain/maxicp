/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Representation of a cumulated Profile data structure as a contiguous sequence of {@link Rectangle}
 * built from a set of {@link Rectangle} using a sweep-line algorithm.
 */
public class Profile {
    //TODO : class only used in some tests. Should we remove it?
    private record Entry(int key, int value) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Integer.compare(key, other.key);
        }
    }

    private final Rectangle[] profileRectangles;

    /**
     * Get the cumulated Profile from the rectangles passed in argument
     * to the constructor.
     *
     * @return the cumulated profile is a contiguous sequence of {@link Rectangle}
     */
    public Rectangle[] rectangles() {
        return profileRectangles;
    }

    public Profile(Rectangle... rectangles) {
        ArrayList<Rectangle> profile = new ArrayList<Rectangle>();
        Entry[] points = new Entry[2 * rectangles.length + 2];
        for (int i = 0; i < rectangles.length; i++) {
            Rectangle r = rectangles[i];
            points[i] = new Entry(r.start, r.height);
            points[rectangles.length + i] = new Entry(r.end, -r.height);
        }
        points[2 * rectangles.length] = new Entry(Integer.MIN_VALUE, 0);
        points[2 * rectangles.length + 1] = new Entry(Integer.MAX_VALUE, 0);

        Arrays.sort(points);

        int sweepHeight = 0;
        int sweepTime = points[0].key;
        for (Entry e : points) {
            int t = e.key;
            int h = e.value;
            if (t != sweepTime) {
                //System.out.println(sweep_t+" "+t);
                profile.add(new Rectangle(sweepTime, t, sweepHeight));
                sweepTime = t;
            }
            sweepHeight += h;
        }
        this.profileRectangles = profile.toArray(new Rectangle[0]);
    }

    /**
     * Retrieves the rectangle index of the profile that overlaps a given time.
     *
     * @param t the time at which we want to retrieve the overlapping rectangle
     * @return the rectangle index r of the profile such that {@code r.start <= t} and {@code r.end > t}
     */
    public int rectangleIndex(int t) {
        for (int i = 0; i < profileRectangles.length; i++) {
            if (profileRectangles[i].start <= t && profileRectangles[i].end > t)
                return i;
        }
        return -1;
    }

    /**
     * Return the number of rectangles in the profile.
     *
     * @return the number of rectangles in the profile
     */
    public int size() {
        return profileRectangles.length;
    }

    /**
     * @param i the rectangle index
     * @return the rectangle of the profile at index i
     * @see #rectangleIndex(int)
     */
    public Rectangle get(int i) {
        return profileRectangles[i];
    }

    @Override
    public String toString() {
        return Arrays.toString(profileRectangles);
    }

    /**
     * Represents a rectangle which is part of the {@link Profile} datastructure
     */
    public static class Rectangle {
        private final int start;
        private final long dur;
        private final int height;
        private final int end;

        Rectangle(int start, int end, int height) {
            assert (end > start);
            this.start = start;
            this.end = end;
            this.dur = ((long) end) - start;
            this.height = height;
        }

        int start() {
            return start;
        }

        long dur() {
            return dur;
        }

        final int height() {
            return height;
        }

        int end() {
            return end;
        }

        @Override
        public String toString() {
            return "[solve:" + start + " dur:" + dur + " end:" + (end) + "] h:" + height;
        }
    }
}
