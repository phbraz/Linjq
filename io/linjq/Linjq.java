package io.linjq;

import io.linjq.core.Queryable;

/**
 * Entry point for LINQ-style queries. Use a static import for fluent call sites:
 *
 * <pre>{@code
 * import static io.linjq.Linjq.from;
 *
 * var names = from(people)
 *     .where(p -> p.age() > 25)
 *     .select(Person::name)
 *     .toList();
 * }</pre>
 *
 * <p>For provider-backed (expression tree) queries, use
 * {@link io.linjq.provider.ProviderQueryable#from(io.linjq.provider.QueryProvider, Iterable)}.</p>
 */
public final class Linjq {

    private Linjq() {}

    /**
     * Creates a query over the given iterable (in-memory execution).
     * Import as {@code import static io.linjq.Linjq.from;} to write {@code from(source).where(...)}.
     *
     * @param source the sequence to query
     * @param <T>    the element type
     * @return a Queryable over the source
     */
    public static <T> Queryable<T> from(Iterable<T> source) {
        return Queryable.from(source);
    }
}
