package net.linjq.provider;

import net.linjq.core.Grouping;
import net.linjq.core.OrderedQueryable;
import net.linjq.core.Queryable;
import net.linjq.expression.*;
import net.linjq.functional.BiFunction;
import net.linjq.functional.Function;
import net.linjq.functional.KeySelector;
import net.linjq.functional.Predicate;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A Queryable that builds expression trees instead of executing immediately.
 * Terminal operations delegate to the {@link QueryProvider} for execution.
 *
 * <p>Analogous to C# LINQ's {@code IQueryable<T>}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a provider-backed queryable
 * var provider = new MyDatabaseProvider(connection);
 * var query = ProviderQueryable.from(provider, tableName);
 *
 * // Build up the query — nothing executes yet
 * var filtered = query
 *     .where(p -> p.age() > 18)
 *     .orderBy(Person::name)
 *     .take(10);
 *
 * // Inspect the expression tree
 * QueryExpression<?> expr = filtered.getExpression();
 *
 * // Execute — provider translates and runs the query
 * List<Person> results = filtered.toList();
 * }</pre>
 *
 * @param <T> the element type
 */
public class ProviderQueryable<T> extends Queryable<T> {

    private final QueryProvider provider;
    private final QueryExpression<T> expression;

    public ProviderQueryable(QueryProvider provider, QueryExpression<T> expression) {
        // Pass a lazy iterable that executes via the provider when iterated
        super(new LazyProviderIterable<>(provider, expression));
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.expression = Objects.requireNonNull(expression, "expression must not be null");
    }

    /**
     * Creates a ProviderQueryable from a source iterable.
     */
    public static <T> ProviderQueryable<T> from(QueryProvider provider, Iterable<T> source) {
        return new ProviderQueryable<>(provider, new SourceExpression<>(source));
    }

    /**
     * Creates a ProviderQueryable from a source with an alias (e.g., table name).
     */
    public static <T> ProviderQueryable<T> from(QueryProvider provider, Iterable<T> source, String alias) {
        return new ProviderQueryable<>(provider, new SourceExpression<>(source, alias));
    }

    /**
     * Returns the expression tree built so far.
     */
    public QueryExpression<T> getExpression() {
        return expression;
    }

    /**
     * Returns the provider backing this queryable.
     */
    public QueryProvider getProvider() {
        return provider;
    }

    // ──────────────────────────────────────────────
    //  Overridden operators — build expression tree
    // ──────────────────────────────────────────────

    @Override
    public ProviderQueryable<T> where(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        return provider.createQuery(new WhereExpression<>(expression, predicate));
    }

    @Override
    public <R> ProviderQueryable<R> select(Function<T, R> selector) {
        Objects.requireNonNull(selector);
        return provider.createQuery(new SelectExpression<>(expression, selector));
    }

    @Override
    public <R> ProviderQueryable<R> selectMany(Function<T, Iterable<R>> selector) {
        Objects.requireNonNull(selector);
        return provider.createQuery(new SelectManyExpression<>(expression, selector));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K extends Comparable<K>> OrderedQueryable<T> orderBy(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        var providerQuery = provider.createQuery(new OrderByExpression<>(expression, keySelector, false));
        return new OrderedQueryable<>(providerQuery, (a, b) -> {
            try { return keySelector.select(a).compareTo(keySelector.select(b)); }
            catch (Exception e) { throw new net.linjq.exceptions.LinjqException("Error in orderBy", e); }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K extends Comparable<K>> OrderedQueryable<T> orderByDescending(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        var providerQuery = provider.createQuery(new OrderByExpression<>(expression, keySelector, true));
        return new OrderedQueryable<>(providerQuery, (a, b) -> {
            try { return keySelector.select(b).compareTo(keySelector.select(a)); }
            catch (Exception e) { throw new net.linjq.exceptions.LinjqException("Error in orderByDescending", e); }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K> ProviderQueryable<Grouping<K, T>> groupBy(Function<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        return provider.createQuery(
                (QueryExpression<Grouping<K, T>>) (QueryExpression<?>) new GroupByExpression<>(expression, keySelector)
        );
    }

    @Override
    public <TInner, TKey, TResult> ProviderQueryable<TResult> join(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, TInner, TResult> resultSelector) {
        return provider.createQuery(new JoinExpression<>(
                expression, inner, outerKeySelector, innerKeySelector, resultSelector));
    }

    @Override
    public <TInner, TKey, TResult> ProviderQueryable<TResult> leftJoin(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, TInner, TResult> resultSelector) {
        return provider.createQuery(new LeftJoinExpression<>(
                expression, inner, outerKeySelector, innerKeySelector, resultSelector));
    }

    @Override
    public <TInner, TKey, TResult> ProviderQueryable<TResult> groupJoin(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, Queryable<TInner>, TResult> resultSelector) {
        return provider.createQuery(new GroupJoinExpression<>(
                expression, inner, outerKeySelector, innerKeySelector, resultSelector));
    }

    @Override
    public <TInner, TResult> ProviderQueryable<TResult> crossJoin(
            Iterable<TInner> inner,
            BiFunction<T, TInner, TResult> resultSelector) {
        return provider.createQuery(new CrossJoinExpression<>(expression, inner, resultSelector));
    }

    @Override
    public ProviderQueryable<T> take(int count) {
        return provider.createQuery(new TakeExpression<>(expression, count));
    }

    @Override
    public ProviderQueryable<T> skip(int count) {
        return provider.createQuery(new SkipExpression<>(expression, count));
    }

    @Override
    public ProviderQueryable<T> distinct() {
        return provider.createQuery(new DistinctExpression<>(expression));
    }

    @Override
    public ProviderQueryable<T> union(Iterable<T> other) {
        return provider.createQuery(new UnionExpression<>(expression, other));
    }

    @Override
    public ProviderQueryable<T> intersect(Iterable<T> other) {
        return provider.createQuery(new IntersectExpression<>(expression, other));
    }

    @Override
    public ProviderQueryable<T> except(Iterable<T> other) {
        return provider.createQuery(new ExceptExpression<>(expression, other));
    }

    @Override
    public ProviderQueryable<T> concat(Iterable<T> other) {
        return provider.createQuery(new ConcatExpression<>(expression, other));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Queryable<List<T>> chunk(int size) {
        return provider.createQuery(
                (QueryExpression<List<T>>) (QueryExpression<?>) new ChunkExpression<>(expression, size)
        );
    }

    @Override
    public ProviderQueryable<T> reverse() {
        return provider.createQuery(new ReverseExpression<>(expression));
    }

    @Override
    public String toString() {
        return "ProviderQueryable[" + expression.type() + "]";
    }

    // ──────────────────────────────────────────────
    //  Lazy iterable that defers to the provider
    // ──────────────────────────────────────────────

    private static class LazyProviderIterable<T> implements Iterable<T> {
        private final QueryProvider provider;
        private final QueryExpression<T> expression;

        LazyProviderIterable(QueryProvider provider, QueryExpression<T> expression) {
            this.provider = provider;
            this.expression = expression;
        }

        @Override
        public Iterator<T> iterator() {
            return provider.execute(expression).iterator();
        }
    }
}
