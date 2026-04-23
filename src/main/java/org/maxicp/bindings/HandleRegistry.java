/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.bindings;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe registry mapping opaque long handles to Java objects.
 * Used by the native C API to bridge between C opaque pointers and Java objects.
 */
public final class HandleRegistry {

    private HandleRegistry() {}

    /** Global object store: handle (long) → Java object */
    private static final ConcurrentHashMap<Long, Object> REGISTRY = new ConcurrentHashMap<>();

    /** Monotonically increasing handle counter; 0 is reserved as "null handle" */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    /**
     * Store an object and return its opaque handle.
     *
     * @param obj the Java object to store (must not be null)
     * @return a positive long handle that uniquely identifies obj
     */
    public static long put(Object obj) {
        if (obj == null) throw new NullPointerException("Cannot store null in HandleRegistry");
        long id = NEXT_ID.getAndIncrement();
        REGISTRY.put(id, obj);
        return id;
    }

    /**
     * Retrieve the Java object associated with the given handle.
     *
     * @param id the handle returned by {@link #put(Object)}
     * @param <T> expected type
     * @return the stored object, or {@code null} if no such handle exists
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(long id) {
        return (T) REGISTRY.get(id);
    }

    /**
     * Remove the mapping for the given handle, freeing the associated Java object.
     *
     * @param id the handle to remove
     */
    public static void remove(long id) {
        REGISTRY.remove(id);
    }

    /**
     * Returns the number of live handles (for diagnostics / leak detection).
     */
    public static int size() {
        return REGISTRY.size();
    }
}

