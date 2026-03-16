package io.linjq.expression;

import io.linjq.functional.BiFunction;
import io.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents an inner join operation in the expression tree.
 *
 * @param <TOuter> the outer element type
 * @param <TInner> the inner element type
 * @param <TKey> the join key type
 * @param <TResult> the result element type
 */
public record JoinExpression<TOuter, TInner, TKey, TResult>(
        QueryExpression<TOuter> outer,
        Iterable<TInner> inner,
        Function<TOuter, TKey> outerKeySelector,
        Function<TInner, TKey> innerKeySelector,
        BiFunction<TOuter, TInner, TResult> resultSelector
) implements QueryExpression<TResult> {

    public JoinExpression {
        Objects.requireNonNull(outer, "outer must not be null");
        Objects.requireNonNull(inner, "inner must not be null");
        Objects.requireNonNull(outerKeySelector, "outerKeySelector must not be null");
        Objects.requireNonNull(innerKeySelector, "innerKeySelector must not be null");
        Objects.requireNonNull(resultSelector, "resultSelector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.JOIN;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(outer);
    }
}
