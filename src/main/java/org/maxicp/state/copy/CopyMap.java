/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.StateEntry;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateMap;
import org.maxicp.state.Storage;

import java.util.*;

/**
 * Implementation of {@link StateMap} with copy strategy
 * @see Copier
 * @see StateManager#makeStateMap()
 */
public class CopyMap<K, V> implements Storage, StateMap<K, V> {

    class CopyMapStateEntry implements StateEntry {
        private final Map<K, V> map;

        CopyMapStateEntry(Map<K, V> map) {
            this.map = map;
        }

        public void restore() {
            CopyMap.this.map = map;
        }
    }

    private Map<K, V> map;

    protected CopyMap() {
        map = new IdentityHashMap<>();
    }

    protected CopyMap(Map<K, V> m) {
        map = new HashMap<>();
        for (Map.Entry<K, V> me : m.entrySet())
            m.put(me.getKey(), me.getValue());
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V put(K k, V v) {
        return map.put(k, v);
    }

    @Override
    public V remove(Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public V get(Object k) {
        return map.get(k);
    }

    @Override
    public StateEntry save() {
        Map<K, V> mapCopy = new IdentityHashMap<>();
        for (Map.Entry<K, V> me : map.entrySet())
            mapCopy.put(me.getKey(), me.getValue());
        return new CopyMapStateEntry(mapCopy);
    }

}
