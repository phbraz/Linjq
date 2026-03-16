package net.linjq;

import net.linjq.core.Queryable;

/**
 * Entry point for LINQ-style queries. Use a static import for fluent call sites:
 *
 * <pre>{@code
 * import static net.linjq.Linjq.from;
 *
 * var names = from(people)
 *     .where(p -> p.age() > 25)
 *     .select(Person::name)
 *     .toList();
 * }</pre>
 *
 * <p>For provider-backed (expression tree) queries, use
 * {@link net.linjq.provider.ProviderQueryable#from(net.linjq.provider.QueryProvider, Iterable)}.</p>
 */
public final class Linjq {

    private Linjq() {}

    /**
     * Creates a query over the given iterable (in-memory execution).
     * Import as {@code import static net.linjq.Linjq.from;} to write {@code from(source).where(...)}.
     *
     * @param source the sequence to query
     * @param <T>    the element type
     * @return a Queryable over the source
     */
    public static <T> Queryable<T> from(Iterable<T> source) {
        return Queryable.from(source);
    }
}
