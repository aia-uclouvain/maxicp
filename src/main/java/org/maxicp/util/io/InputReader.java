/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.util.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;


public class InputReader {

    private BufferedReader in;
    private StringTokenizer tokenizer;

    public InputReader(String file) {
        try {

            FileInputStream istream = new FileInputStream(file);
            in = new BufferedReader(new InputStreamReader(istream));
            tokenizer = new StringTokenizer("");
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public Integer getInt() throws RuntimeException {
        if (!tokenizer.hasMoreTokens()) {
            try {
                String line;
                do {
                    line = in.readLine();
                    if (line == null) {
                        //System.out.println("No more line to read");
                        throw new RuntimeException("End of file");
                    }
                    tokenizer = new StringTokenizer(line);
                } while (!tokenizer.hasMoreTokens());

            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
        }
        return Integer.parseInt(tokenizer.nextToken());
    }

    public double getDouble() throws RuntimeException {
        if (!tokenizer.hasMoreTokens()) {
            try {
                String line;
                do {
                    line = in.readLine();
                    if (line == null) {
                        //System.out.println("No more line to read");
                        throw new RuntimeException("End of file");
                    }
                    tokenizer = new StringTokenizer(line);
                } while (!tokenizer.hasMoreTokens());

            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
        }
        return Double.parseDouble(tokenizer.nextToken());
    }

    public double[][] getDoubleMatrix(int n, int m) throws RuntimeException {
        double[][] matrix = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                matrix[i][j] = getDouble();
            }
        }
        return matrix;
    }

    public int[][] getIntMatrix(int n, int m) throws RuntimeException {
        int[][] matrix = new int[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                matrix[i][j] = getInt();
            }
        }
        return matrix;
    }


    public Integer[] getIntLine() throws RuntimeException {
        if (!tokenizer.hasMoreTokens()) {
            try {
                String line;
                do {
                    line = in.readLine();
                    if (line == null) {
                        //System.out.println("No more line to read");
                        throw new RuntimeException("End of file");
                    }
                    tokenizer = new StringTokenizer(line);
                } while (!tokenizer.hasMoreTokens());

            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
        }
        Integer[] res = new Integer[tokenizer.countTokens()];
        for (int i = 0; i < res.length; i++) {
            res[i] = Integer.parseInt(tokenizer.nextToken());
        }
        return res;
    }

    public void skipLine() throws RuntimeException {
        try {
            String line;
            do {
                line = in.readLine();
                if (line == null) {
                    //System.out.println("No more line to read");
                    throw new RuntimeException("End of file");
                }
                tokenizer = new StringTokenizer(line);
            } while (!tokenizer.hasMoreTokens());

        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }

    public String getString() throws RuntimeException {
        if (!tokenizer.hasMoreTokens()) {
            try {
                String line;
                do {
                    line = in.readLine();
                    if (line == null) {
                        //System.out.println("No more line to read");
                        throw new RuntimeException("End of file");
                    }
                    tokenizer = new StringTokenizer(line);
                } while (!tokenizer.hasMoreTokens());

            } catch (IOException e) {
                throw new RuntimeException(e.toString());
            }
        }
        return tokenizer.nextToken();
    }

}
