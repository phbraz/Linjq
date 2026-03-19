package net.linjq.core;

import net.linjq.exceptions.LinjqException;
import net.linjq.functional.KeySelector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Queryable} with a primary (and optionally secondary) ordering.
 * Produced by {@link Queryable#orderBy} and {@link Queryable#orderByDescending};
 * use {@link #thenBy} / {@link #thenByDescending} for secondary sort keys.
 *
 * @param <T> the element type
 */
public class OrderedQueryable<T> extends Queryable<T> {

    private final Queryable<T> source;
    private final Comparator<T> comparator;

    public OrderedQueryable(Queryable<T> source, Comparator<T> comparator) {
        super(materializeAndSort(source, comparator));
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.comparator = Objects.requireNonNull(comparator, "comparator must not be null");
    }

    private static <T> Iterable<T> materializeAndSort(Queryable<T> source, Comparator<T> comparator) {
        List<T> list = source.toList();
        list.sort(comparator);
        return list;
    }

    private static RuntimeException propagate(Exception e) {
        if (e instanceof RuntimeException re) return re;
        return new LinjqException(e);
    }

    public <K extends Comparable<K>> OrderedQueryable<T> thenBy(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        Comparator<T> then = (a, b) -> {
            try { return keySelector.select(a).compareTo(keySelector.select(b)); }
            catch (Exception e) { throw propagate(e); }
        };
        return new OrderedQueryable<>(source, comparator.thenComparing(then));
    }

    public <K extends Comparable<K>> OrderedQueryable<T> thenByDescending(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        Comparator<T> then = (a, b) -> {
            try { return keySelector.select(b).compareTo(keySelector.select(a)); }
            catch (Exception e) { throw propagate(e); }
        };
        return new OrderedQueryable<>(source, comparator.thenComparing(then));
    }
}