package io.linjq.expression;

import io.linjq.functional.BiFunction;

import java.util.List;
import java.util.Objects;

/**
 * Represents a cross join (Cartesian product) in the expression tree.
 */
public record CrossJoinExpression<TOuter, TInner, TResult>(
        QueryExpression<TOuter> outer,
        Iterable<TInner> inner,
        BiFunction<TOuter, TInner, TResult> resultSelector
) implements QueryExpression<TResult> {

    public CrossJoinExpression {
        Objects.requireNonNull(outer, "outer must not be null");
        Objects.requireNonNull(inner, "inner must not be null");
        Objects.requireNonNull(resultSelector, "resultSelector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.CROSS_JOIN;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(outer);
    }
}
