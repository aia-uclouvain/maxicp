package org.maxicp.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class HashMultimap<K,V> {
    private final LinkedHashMap<K, LinkedHashSet<V>> map;
    private final Set<V> emptySet = Collections.unmodifiableSet(new LinkedHashSet<>());

    public HashMultimap() {
        map = new LinkedHashMap<>();
    }

    public Set<V> get(K key) {
        if(map.containsKey(key))
            return Collections.unmodifiableSet(map.get(key));
        return emptySet;
    }

    public void put(K key, V value) {
        if(!map.containsKey(key))
            map.put(key, new LinkedHashSet<>());
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
