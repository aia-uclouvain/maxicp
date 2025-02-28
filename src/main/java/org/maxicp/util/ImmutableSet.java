package org.maxicp.util;

import java.util.Arrays;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ImmutableSet<E> implements Set<E> {
    private final Set<E> delegate;

    public ImmutableSet(Collection<E> delegate) {
        this.delegate = switch (delegate) {
            case ImmutableSet<E> immutableSet -> immutableSet.delegate;
            case Collection<E> d -> Set.copyOf(d);
        };
    }

    public static ImmutableSet<Integer> of(int... ints) {
        return new ImmutableSet<>(new TrustMeBro<>(Ints.asSet(ints)));
    }

    /**
     * Private constructor that trusts that the delegate given will not be used elsewhere
     * @param tmb
     */
    private ImmutableSet(TrustMeBro<E> tmb) {
        this.delegate = tmb.delegate;
    }
    private record TrustMeBro<E>(Set<E> delegate) {}

    public ImmutableSet(E[] elems) {
        this.delegate = Set.of(elems);
    }

    public static <X> ImmutableSet<X> of(X... x) {
        return new ImmutableSet<>(x);
    }

    public static <X> ImmutableSet<X> of(Collection<X> x) {
        return new ImmutableSet<>(x);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    public static <E> Collector<E,?,ImmutableSet<E>> toImmutableSet() {
        return Collector.of(
            (Supplier<Set<E>>) HashSet::new,
            Set::add,
            (left, right) -> { left.addAll(right); return left; },
            ImmutableSet::new,
            Collector.Characteristics.UNORDERED
        );
    }

    public static class Builder<E> {
        private Set<E> delegate;
        public Builder() {
            delegate = new HashSet<>();
        }

        public ImmutableSet<E> build() {
            ImmutableSet<E> out = new ImmutableSet<>(new TrustMeBro<>(delegate));
            delegate = null;
            return out;
        }

        public Builder<E> add(E e) {
            delegate.add(e);
            return this;
        }

        public Builder<E> add(E... es) {
            delegate.addAll(Arrays.asList(es));
            return this;
        }

        public Builder<E> addAll(Collection<? extends E> c) {
            delegate.addAll(c);
            return this;
        }
    }

    public static <E> Builder<E> builder() {
        return new Builder<E>();
    }
}
