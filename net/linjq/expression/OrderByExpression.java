package net.linjq.expression;

import net.linjq.functional.KeySelector;

import java.util.List;
import java.util.Objects;

/**
 * Represents an ordering operation in the expression tree.
 *
 * @param <T> the element type
 * @param <K> the key type used for ordering
 */
public record OrderByExpression<T, K extends Comparable<K>>(
        QueryExpression<T> source,
        KeySelector<T, K> keySelector,
        boolean descending
) implements QueryExpression<T> {

    public OrderByExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(keySelector, "keySelector must not be null");
    }

    @Override
    public ExpressionType type() {
        return descending ? ExpressionType.ORDER_BY_DESCENDING : ExpressionType.ORDER_BY;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
