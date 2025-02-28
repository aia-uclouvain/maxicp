package org.maxicp.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class HashMultimap<K,V> {
    private final HashMap<K, HashSet<V>> map;
    private final Set<V> emptySet = Collections.unmodifiableSet(new HashSet<>());

    public HashMultimap() {
        map = new HashMap<>();
    }

    public Set<V> get(K key) {
        if(map.containsKey(key))
            return Collections.unmodifiableSet(map.get(key));
        return emptySet;
    }

    public void put(K key, V value) {
        if(!map.containsKey(key))
            map.put(key, new HashSet<>());
        map.get(key).add(value);
    }

    public void remove(K key, V value) {
        if(map.containsKey(key))
            map.get(key).remove(value);
    }

    public void removeAll(K key) {
        map.remove(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set<K> keySet() {
        return map.keySet();
    }
}
