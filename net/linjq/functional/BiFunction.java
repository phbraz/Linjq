package net.linjq.functional;

/**
 * Represents a function that accepts two arguments and produces a result.
 *
 * @param <T> the first argument type
 * @param <U> the second argument type
 * @param <R> the result type
 */
@FunctionalInterface
public interface BiFunction<T, U, R> {
    R apply(T t, U u) throws Exception;
}
