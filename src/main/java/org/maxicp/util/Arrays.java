/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

public class Arrays {

    public static int argMax(int [] values) {
        int max = Integer.MIN_VALUE;
        int argMax = -1;
        for(int i = 0; i < values.length; ++i) {
            if(values[i] > max) {
                max = values[i];
                argMax = i;
            }
        }
        return argMax;
    }

    public static int argMin(int [] values) {
        int min = Integer.MAX_VALUE;
        int argMin = -1;
        for(int i = 0; i < values.length; ++i) {
            if(values[i] < min) {
                min = values[i];
                argMin = i;
            }
        }
        return argMin;
    }

    public static int argProb(double [] proba) {
        double r = Math.random();
        double sum = 0;
        for(int i = 0; i < proba.length; i++) {
            sum += proba[i];
            if (sum >= r) {
                return i;
            }
        }
        return proba.length - 1;
    }

    public static double [] softMax(int [] values) {
        double [] res = new double[values.length];
        double sum = 0;
        for(int i = 0; i < values.length; ++i) {
            res[i] = Math.exp(values[i]);
            sum += res[i];
        }
        for(int i = 0; i < values.length; ++i) {
            res[i] /= sum;
        }
        return res;
    }

    public static int argSoftMax(int [] values) {
        double [] proba = softMax(values);
        return argProb(proba);
    }

    /**
     *
     * @param a
     * @return the maximum value in a
     */
    public static int max(int ... a) {
        int v = Integer.MIN_VALUE;
        for (int i = 0; i < a.length; i++) {
            v = Math.max(v, a[i]);
        }
        return v;
    }

    /**
     *
     * @param a
     * @return the maximum value in a
     */
    public static int max(int [][] a) {
        int v = Integer.MIN_VALUE;
        for (int i = 0; i < a.length; i++) {
            v = Math.max(v, max(a[i]));
        }
        return v;
    }

    /**
     *
     * @param a
     * @return the minimum value in a
     */
    public static int min(int ... a) {
        int v = Integer.MAX_VALUE;
        for (int i = 0; i < a.length; i++) {
            v = Math.min(v, a[i]);
        }
        return v;
    }

    /**
     *
     * @param a
     * @return  the minimum value in a
     */
    public static int min(int [][] a) {
        int v = Integer.MAX_VALUE;
        for (int i = 0; i < a.length; i++) {
            v = Math.min(v, min(a[i]));
        }
        return v;
    }

    /**
     *
     * @param a
     * @return the sum of the values in a
     */
    public static int sum(int [] a) {
        int v = 0;
        for (int i: a) {
            v += i;
        }
        return v;
    }

    /**
     *
     * @param a
     * @return the sum of the values in a
     */
    public static double sum(double [] a) {
        double v = 0;
        for (double i: a) {
            v += i;
        }
        return v;
    }

    /**
     *
     * @param a
     * @return the sum of the values in a
     */
    public static int sum(int [][] a) {
        int s = 0;
        for (int i = 0; i < a.length; i++) {
            s += sum(a[i]);
        }
        return s;
    }

    /**
     *
     * @param a
     * @return the product of values in a
     */
    public static int prod(int [] a) {
        int v = 1;
        for (int i: a) {
            v *= i;
        }
        return v;
    }


    /**
     *
     * @param a matrix array
     * @return  the column c that is [a[0][c] , ... , a[n-1][c]]
     */
    public static int[] getSlice(int[][] a, int c) {
        assert(c > 0);
        int [] res = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            assert(c < a[i].length);
            res[i] = a[i][c];
        }
        return res;
    }

    /**
     *
     * @param v
     * @param n
     * @return an array of length n each entry with value v
     */
    public static int[] replicate(int v, int n) {
        int [] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = v;
        }
        return res;
    }

    /**
     *
     * @param a
     * @param <E>
     * @return a flattened array list of all elements in a
     */
    public static<E> ArrayList<E> flatten(E [][] a) {
        ArrayList<E> res = new ArrayList<E>();
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                res.add(a[i][j]);
            }
        }
        return res;

    }


    /**
     * Generates a random permutation
     * @param n
     * @param seed for the random number generator
     * @return  a random permutation from 0 to n-1
     */
    public static int[] getRandomPermutation(int n, int seed) {
        int [] perm = new int[n];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = i;
        }
        Random rand = new Random(seed);
        for (int i = 0; i < perm.length; i++) {
            int ind1 = rand.nextInt(n);
            int ind2 = rand.nextInt(n);
            int temp = perm[ind1];
            perm[ind1] = perm[ind2];
            perm[ind2] = temp;
        }
        return perm;
    }

    /**
     *
     * @param x
     * @param permutation a valid permutation of x (all numbers from 0 to x.length-1 present), <br>
     *        permutation[i] represents the index of the entry of x that must come in position i in the permuted array
     * @param <E>
     */
    public static <E> void  applyPermutation(E [] x, int [] permutation) {
        assert (x.length  == permutation.length);
        Object [] objs = new Object[x.length];
        int sum = 0;
        for (int i = 0; i < permutation.length; i++) {
            sum += permutation[i];
            objs[i] = x[i];
        }
        assert (sum == (x.length-1)*(x.length-2)/2); // check the permutation is valid
        for (int i = 0; i < permutation.length; i++) {
            x[i] = (E) objs[permutation[i]];
        }
        objs = null;
    }

    /**
     * permutes x according to the permutation
     * @param x the array to permute
     * @param permutation
     */
    public static void  applyPermutation(int [] x, int [] permutation) {
        int [] xcopy = java.util.Arrays.copyOf(x,x.length);
        for (int i = 0; i < permutation.length; i++) {
            x[i] = xcopy[permutation[i]];
        }
    }

    /**
     *
     * @param w
     * @return the sorting permutation perm of w, i.e. w[perm[0]],w[perm[1]],...,w[perm[w.length-1]] is sorted increasingly
     */
    public static int []  sortPerm(final int [] w) {
        Integer [] perm = new Integer[w.length];
        for (int i = 0; i < perm.length; i++) {
            perm[i] = i;
        }
        java.util.Arrays.sort(perm,new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2) {
                return w[o1]-w[o2];
            }
        });
        int [] res = new int[w.length];
        for (int i = 0; i < perm.length; i++) {
            res[i] = perm[i];
        }
        return res;
    }




    /**
     * sorts x increasingly according to the weights w
     * @param x
     * @param w
     */
    public static <E> void  sort(E [] x, int [] w) {
        assert (x.length == w.length);
        applyPermutation(x,sortPerm(w));
    }

}
