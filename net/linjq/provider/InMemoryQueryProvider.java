package net.linjq.provider;

import net.linjq.core.Queryable;
import net.linjq.expression.*;

/**
 * A reference {@link QueryProvider} implementation that executes expression trees
 * in-memory using the existing Queryable engine.
 *
 * <p>This serves two purposes:</p>
 * <ul>
 *   <li>Proves the expression tree / provider architecture works end-to-end</li>
 *   <li>Provides a working baseline for providers to reference</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var provider = new InMemoryQueryProvider();
 * var query = ProviderQueryable.from(provider, people)
 *     .where(p -> p.age() > 18)
 *     .orderBy(Person::name)
 *     .take(10);
 *
 * // Inspect the expression tree
 * var expr = query.getExpression();
 *
 * // Execute via the provider
 * var results = query.toList();
 * }</pre>
 */
public class InMemoryQueryProvider implements QueryProvider {

    @Override
    public <T> Iterable<T> execute(QueryExpression<T> expression) {
        return evaluate(expression);
    }

    @Override
    public <T> ProviderQueryable<T> createQuery(QueryExpression<T> expression) {
        return new ProviderQueryable<>(this, expression);
    }

    /**
     * Recursively evaluates an expression tree using Queryable operations.
     */
    @SuppressWarnings("unchecked")
    private <T> Iterable<T> evaluate(QueryExpression<T> expression) {
        return switch (expression) {
            case SourceExpression<T> e -> e.source();

            case WhereExpression<T> e ->
                    Queryable.from(evaluate(e.source())).where(e.predicate());

            case SelectExpression<?, ?> e -> {
                var se = (SelectExpression<Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(se.source())).select(se.selector());
            }

            case SelectManyExpression<?, ?> e -> {
                var sme = (SelectManyExpression<Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(sme.source())).selectMany(sme.selector());
            }

            case OrderByExpression<?, ?> e -> {
                var oe = (OrderByExpression<T, ?>) e;
                yield evaluateOrderBy(oe);
            }

            case ThenByExpression<?, ?> e -> {
                var te = (ThenByExpression<T, ?>) e;
                yield evaluateThenBy(te);
            }

            case GroupByExpression<?, ?> e -> {
                var ge = (GroupByExpression<Object, Object>) e;
                yield (Iterable<T>) Queryable.from((Iterable<Object>) evaluate(ge.source())).groupBy(ge.keySelector());
            }

            case JoinExpression<?, ?, ?, ?> e -> {
                var je = (JoinExpression<Object, Object, Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(je.outer()))
                        .join(je.inner(), je.outerKeySelector(), je.innerKeySelector(), je.resultSelector());
            }

            case LeftJoinExpression<?, ?, ?, ?> e -> {
                var le = (LeftJoinExpression<Object, Object, Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(le.outer()))
                        .leftJoin(le.inner(), le.outerKeySelector(), le.innerKeySelector(), le.resultSelector());
            }

            case GroupJoinExpression<?, ?, ?, ?> e -> {
                var gje = (GroupJoinExpression<Object, Object, Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(gje.outer()))
                        .groupJoin(gje.inner(), gje.outerKeySelector(), gje.innerKeySelector(), gje.resultSelector());
            }

            case CrossJoinExpression<?, ?, ?> e -> {
                var cje = (CrossJoinExpression<Object, Object, T>) e;
                yield Queryable.from((Iterable<Object>) evaluate(cje.outer()))
                        .crossJoin(cje.inner(), cje.resultSelector());
            }

            case TakeExpression<T> e ->
                    Queryable.from(evaluate(e.source())).take(e.count());

            case SkipExpression<T> e ->
                    Queryable.from(evaluate(e.source())).skip(e.count());

            case DistinctExpression<T> e ->
                    Queryable.from(evaluate(e.source())).distinct();

            case UnionExpression<T> e ->
                    Queryable.from(evaluate(e.source())).union(e.other());

            case IntersectExpression<T> e ->
                    Queryable.from(evaluate(e.source())).intersect(e.other());

            case ExceptExpression<T> e ->
                    Queryable.from(evaluate(e.source())).except(e.other());

            case ConcatExpression<T> e ->
                    Queryable.from(evaluate(e.source())).concat(e.other());

            case ChunkExpression<?> e -> {
                var ce = (ChunkExpression<Object>) e;
                yield (Iterable<T>) Queryable.from((Iterable<Object>) evaluate(ce.source())).chunk(ce.size());
            }

            case ReverseExpression<T> e ->
                    Queryable.from(evaluate(e.source())).reverse();
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Iterable<T> evaluateOrderBy(OrderByExpression<T, ?> expr) {
        var source = Queryable.from(evaluate(expr.source()));
        if (expr.descending()) {
            return source.orderByDescending((net.linjq.functional.KeySelector) expr.keySelector());
        } else {
            return source.orderBy((net.linjq.functional.KeySelector) expr.keySelector());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Iterable<T> evaluateThenBy(ThenByExpression<T, ?> expr) {
        // The source of a ThenBy must be an OrderBy or another ThenBy
        var parentResult = evaluate(expr.source());
        // Since we evaluated the parent, it's already sorted — we need to re-sort with compound key
        // For in-memory, we delegate to the parent Queryable which handles this
        var parentQueryable = Queryable.from(parentResult);
        if (expr.descending()) {
            return parentQueryable.orderByDescending((net.linjq.functional.KeySelector) expr.keySelector());
        } else {
            return parentQueryable.orderBy((net.linjq.functional.KeySelector) expr.keySelector());
        }
    }
}
