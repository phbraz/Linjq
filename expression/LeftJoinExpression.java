package io.linjq.expression;

import io.linjq.functional.BiFunction;
import io.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents a left outer join in the expression tree.
 *
 * @param <TOuter> the outer element type
 * @param <TInner> the inner element type
 * @param <TKey> the join key type
 * @param <TResult> the result element type
 */
public record LeftJoinExpression<TOuter, TInner, TKey, TResult>(
        QueryExpression<TOuter> outer,
        Iterable<TInner> inner,
        Function<TOuter, TKey> outerKeySelector,
        Function<TInner, TKey> innerKeySelector,
        BiFunction<TOuter, TInner, TResult> resultSelector
) implements QueryExpression<TResult> {

    public LeftJoinExpression {
        Objects.requireNonNull(outer, "outer must not be null");
        Objects.requireNonNull(inner, "inner must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.LEFT_JOIN;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(outer);
    }
}
