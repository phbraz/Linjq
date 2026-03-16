package io.linjq.expression;

import io.linjq.core.Grouping;
import io.linjq.functional.Function;

import java.util.List;
import java.util.Objects;

/**
 * Represents a groupBy operation in the expression tree.
 *
 * @param <TSource> the input element type
 * @param <TKey> the grouping key type
 */
public record GroupByExpression<TSource, TKey>(
        QueryExpression<TSource> source,
        Function<TSource, TKey> keySelector
) implements QueryExpression<Grouping<TKey, TSource>> {

    public GroupByExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(keySelector, "keySelector must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.GROUP_BY;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
