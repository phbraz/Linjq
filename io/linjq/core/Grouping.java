package io.linjq.core;

import java.util.Iterator;
import java.util.Objects;

/**
 * Represents a group of elements with a common key, as produced by {@link Queryable#groupBy}.
 *
 * @param <K> the key type
 * @param <T> the element type
 */
public final class Grouping<K, T> implements Iterable<T> {

    private final K key;
    private final Iterable<T> elements;

    public Grouping(K key, Iterable<T> elements) {
        this.key = key;
        this.elements = Objects.requireNonNull(elements, "elements must not be null");
    }

    public K key() {
        return key;
    }

    @Override
    public Iterator<T> iterator() {
        return elements.iterator();
    }
}
