package io.linjq.functional;

/**
 * Represents a predicate (boolean-valued function) of one argument.
 *
 * @param <T> the input type
 */
@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t);
}
