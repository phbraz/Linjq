package net.linjq.expression;

import net.linjq.functional.BiFunction;

import java.util.List;
import java.util.Objects;

/**
 * Represents an indexed flat-map (selectManyIndexed) operation in the expression tree.
 *
 * @param <TSource> the input element type
 * @param <TResult> the output element type
 */
public record SelectManyIndexedExpression<TSource, TResult>(
        QueryExpression<TSource> source,
        BiFunction<TSource, Integer, Iterable<TResult>> selector
) implements QueryExpression<TResult> {

    public SelectManyIndexedExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(selector, "selector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.SELECT_MANY_INDEXED;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
