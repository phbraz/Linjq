package net.linjq.expression;

import net.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents a flat-map (selectMany) operation in the expression tree.
 *
 * @param <TSource> the input element type
 * @param <TResult> the output element type
 */
public record SelectManyExpression<TSource, TResult>(
        QueryExpression<TSource> source,
        Function<TSource, Iterable<TResult>> selector
) implements QueryExpression<TResult> {

    public SelectManyExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(selector, "selector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.SELECT_MANY;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
