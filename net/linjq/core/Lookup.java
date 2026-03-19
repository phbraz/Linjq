package net.linjq.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable multi-map that maps keys to sequences of values.
 * Produced by {@link Queryable#toLookup}. Unlike {@link Queryable#groupBy},
 * a Lookup provides O(1) key access via {@link #get(Object)}.
 *
 * <p>Analogous to C# LINQ's {@code ILookup<TKey, TElement>}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var lookup = from(people).toLookup(Person::city);
 *
 * // O(1) key access
 * Queryable<Person> londoners = lookup.get("London");
 *
 * // Iterate all groups
 * for (Grouping<String, Person> group : lookup) {
 *     System.out.println(group.key() + ": " + group.count());
 * }
 *
 * // Check key existence
 * boolean hasBerlin = lookup.containsKey("Berlin");
 * }</pre>
 *
 * @param <K> the key type
 * @param <T> the element type
 */
public final class Lookup<K, T> implements Iterable<Grouping<K, T>> {

    private final Map<K, List<T>> map;
    private final List<K> keyOrder;

    private Lookup(Map<K, List<T>> map, List<K> keyOrder) {
        this.map = map;
        this.keyOrder = keyOrder;
    }

    /**
     * Creates a Lookup from the given iterable and key selector.
     * Preserves insertion order of keys (first occurrence).
     */
    public static <K, T> Lookup<K, T> create(Iterable<T> source, net.linjq.functional.Function<T, K> keySelector) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(keySelector, "keySelector must not be null");
        try {
            var map = new LinkedHashMap<K, List<T>>();
            var keyOrder = new ArrayList<K>();
            for (T item : source) {
                K key = keySelector.apply(item);
                if (!map.containsKey(key)) {
                    keyOrder.add(key);
                    map.put(key, new ArrayList<>());
                }
                map.get(key).add(item);
            }
            return new Lookup<>(map, keyOrder);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new net.linjq.exceptions.LinjqException(e);
        }
    }

    /**
     * Creates a Lookup with both a key selector and an element selector.
     */
    public static <TSource, K, T> Lookup<K, T> create(
            Iterable<TSource> source,
            net.linjq.functional.Function<TSource, K> keySelector,
            net.linjq.functional.Function<TSource, T> elementSelector) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(keySelector, "keySelector must not be null");
        Objects.requireNonNull(elementSelector, "elementSelector must not be null");
        try {
            var map = new LinkedHashMap<K, List<T>>();
            var keyOrder = new ArrayList<K>();
            for (TSource item : source) {
                K key = keySelector.apply(item);
                T element = elementSelector.apply(item);
                if (!map.containsKey(key)) {
                    keyOrder.add(key);
                    map.put(key, new ArrayList<>());
                }
                map.get(key).add(element);
            }
            return new Lookup<>(map, keyOrder);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new net.linjq.exceptions.LinjqException(e);
        }
    }

    /**
     * Returns the elements for the given key as a Queryable.
     * If the key does not exist, returns an empty Queryable.
     */
    public Queryable<T> get(K key) {
        List<T> values = map.get(key);
        if (values == null) {
            return Queryable.from(Collections.emptyList());
        }
        return Queryable.from(Collections.unmodifiableList(values));
    }

    /**
     * Returns true if the lookup contains the given key.
     */
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /**
     * Returns the number of distinct keys.
     */
    public int size() {
        return map.size();
    }

    /**
     * Returns all keys in insertion order.
     */
    public Queryable<K> keys() {
        return Queryable.from(Collections.unmodifiableList(keyOrder));
    }

    /**
     * Iterates over all groups in key insertion order.
     */
    @Override
    public Iterator<Grouping<K, T>> iterator() {
        return new Iterator<>() {
            private final Iterator<K> keyIt = keyOrder.iterator();

            @Override
            public boolean hasNext() {
                return keyIt.hasNext();
            }

            @Override
            public Grouping<K, T> next() {
                K key = keyIt.next();
                return new Grouping<>(key, Collections.unmodifiableList(map.get(key)));
            }
        };
    }
}