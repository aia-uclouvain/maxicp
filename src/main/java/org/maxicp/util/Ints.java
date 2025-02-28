package org.maxicp.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Ints {
    public static List<Integer> asList(int[] list) {
        List<Integer> resultList = new ArrayList<>(list.length);
        for (int i : list)
            resultList.add(i);
        return resultList;
    }

    public static Set<Integer> asSet(int[] list) {
        Set<Integer> resultList = new HashSet<>(list.length);
        for (int i : list)
            resultList.add(i);
        return resultList;
    }
}
