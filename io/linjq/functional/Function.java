package io.linjq.functional;

/**
 * Represents a function that accepts one argument and produces a result.
 *
 * @param <T> the input type
 * @param <R> the result type
 */
@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);
}
