package net.linjq.provider;

import net.linjq.core.OrderedQueryable;
import net.linjq.core.Queryable;
import net.linjq.exceptions.LinjqException;
import net.linjq.expression.*;

import java.util.Comparator;

/**
 * A reference {@link QueryProvider} implementation that executes expression trees
 * in-memory using the existing Queryable engine.
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

    private static RuntimeException propagate(Exception e) {
        if (e instanceof RuntimeException re) return re;
        return new LinjqException(e);
    }

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
        var keySelector = expr.keySelector();
        if (expr.descending()) {
            return source.orderByDescending((net.linjq.functional.KeySelector) keySelector);
        } else {
            return source.orderBy((net.linjq.functional.KeySelector) keySelector);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Iterable<T> evaluateThenBy(ThenByExpression<T, ?> expr) {
        // Build the full comparator chain by walking up the tree
        Comparator<T> comparator = buildComparator(expr);
        // Evaluate the deepest source (before any ordering) and sort with the composed comparator
        QueryExpression<T> deepest = findPreOrderSource(expr);
        var source = Queryable.from(evaluate(deepest));
        return new OrderedQueryable<>(source, comparator);
    }

    /**
     * Walks up the chain of OrderBy/ThenBy expressions and composes a single Comparator.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Comparator<T> buildComparator(QueryExpression<T> expr) {
        return switch (expr) {
            case OrderByExpression<T, ?> e -> {
                var ks = e.keySelector();
                Comparator<T> cmp = (a, b) -> {
                    try { return ((Comparable) ks.select(a)).compareTo(ks.select(b)); }
                    catch (Exception ex) { throw propagate(ex); }
                };
                yield e.descending() ? cmp.reversed() : cmp;
            }
            case ThenByExpression<T, ?> e -> {
                Comparator<T> parent = buildComparator((QueryExpression<T>) e.source());
                var ks = e.keySelector();
                Comparator<T> secondary = (a, b) -> {
                    try { return ((Comparable) ks.select(a)).compareTo(ks.select(b)); }
                    catch (Exception ex) { throw propagate(ex); }
                };
                yield parent.thenComparing(e.descending() ? secondary.reversed() : secondary);
            }
            default -> throw new LinjqException("Expected OrderBy or ThenBy expression, got: " + expr.type());
        };
    }

    /**
     * Finds the source expression before any OrderBy/ThenBy chain.
     */
    @SuppressWarnings("unchecked")
    private <T> QueryExpression<T> findPreOrderSource(QueryExpression<T> expr) {
        return switch (expr) {
            case OrderByExpression<T, ?> e -> (QueryExpression<T>) e.source();
            case ThenByExpression<T, ?> e -> findPreOrderSource((QueryExpression<T>) e.source());
            default -> expr;
        };
    }
}