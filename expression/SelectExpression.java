package io.linjq.expression;

import io.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents a projection (select) operation in the expression tree.
 *
 * @param <TSource> the input element type
 * @param <TResult> the output element type
 */
public record SelectExpression<TSource, TResult>(
        QueryExpression<TSource> source,
        Function<TSource, TResult> selector
) implements QueryExpression<TResult> {

    public SelectExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(selector, "selector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.SELECT;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
