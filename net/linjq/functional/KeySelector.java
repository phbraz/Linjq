package net.linjq.functional;

/**
 * Selects a comparable key from an element for ordering.
 *
 * @param <T> the element type
 * @param <K> the key type (must be comparable)
 */
@FunctionalInterface
public interface KeySelector<T, K extends Comparable<K>> {
    K select(T t) throws Exception;
}
