/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.util;

import java.util.ArrayDeque;
import java.util.Queue;

public class PQueue<E> {

    final Queue<E> [] queues;

    public PQueue(int nPriorities) {
        queues = new Queue[nPriorities];
        for (int i = 0; i < nPriorities; i++) {
            queues[i] = new ArrayDeque<>();
        }
    }

    public void add(E e, int priority) {
        queues[priority].add(e);
    }

    public E poll() {
        for (int i = 0; i < queues.length; i++) {
            if (!queues[i].isEmpty()) {
                return queues[i].remove();
            }
        }
        return null;
    }


    public boolean isEmpty() {
        for (int i = 0; i < queues.length; i++) {
            if (!queues[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        for (int i = 0; i < queues.length; i++) {
            queues[i].clear();
        }
    }

    public int size() {
        int size = 0;
        for (int i = 0; i < queues.length; i++) {
            size += queues[i].size();
        }
        return size;
    }






}
