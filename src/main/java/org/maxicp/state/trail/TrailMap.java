/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateEntry;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateMap;
import org.maxicp.util.exception.NotImplementedException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link StateMap} with trail strategy
 * @see Trailer
 * @see StateManager#makeStateMap()
 */
public class TrailMap<K, V> implements StateMap<K, V> {

    private Trailer trail;
    private Map<K, V> map = new HashMap<>();

    protected TrailMap(Trailer trail) {
        this.trail = trail;
    }

    @Override
    public V put(K k, V v) {
        if (!map.containsKey(k)) {
            trail.pushState(new StateEntry() {
                @Override
                public void restore() {
                    map.remove(k);
                }
            });
            map.put(k, v);
            return null;
        } else {
            final V vOld = map.get(k);
            trail.pushState(new StateEntry() {
                @Override
                public void restore() {
                    map.put(k, vOld);
                }
            });
            map.put(k, v);
            return vOld;
        }
    }

    @Override
    public V remove(Object k) {
        if (!map.containsKey(k)) {
            return null;
        } else {
            final V v = map.remove(k);
            trail.pushState(new StateEntry() {
                @Override
                public void restore() {
                    map.put((K) k, v);
                }
            });
            return v;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new NotImplementedException("TrailMap does not support putAll");
    }

    @Override
    public void clear() {
        throw new NotImplementedException("TrailMap does not support clear");
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


}
