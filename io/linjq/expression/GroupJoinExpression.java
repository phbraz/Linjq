package io.linjq.expression;

import io.linjq.core.Queryable;
import io.linjq.functional.BiFunction;
import io.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents a group join in the expression tree.
 */
public record GroupJoinExpression<TOuter, TInner, TKey, TResult>(
        QueryExpression<TOuter> outer,
        Iterable<TInner> inner,
        Function<TOuter, TKey> outerKeySelector,
        Function<TInner, TKey> innerKeySelector,
        BiFunction<TOuter, Queryable<TInner>, TResult> resultSelector
) implements QueryExpression<TResult> {

    public GroupJoinExpression {
        Objects.requireNonNull(outer, "outer must not be null");
        Objects.requireNonNull(inner, "inner must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.GROUP_JOIN;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(outer);
    }
}
