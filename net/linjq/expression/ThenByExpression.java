package net.linjq.expression;

import net.linjq.functional.KeySelector;

import java.util.List;
import java.util.Objects;

/**
 * Represents a secondary ordering (thenBy/thenByDescending) in the expression tree.
 *
 * @param <T> the element type
 * @param <K> the key type used for secondary ordering
 */
public record ThenByExpression<T, K extends Comparable<K>>(
        QueryExpression<T> source,
        KeySelector<T, K> keySelector,
        boolean descending
) implements QueryExpression<T> {

    public ThenByExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(keySelector, "keySelector must not be null");
    }

    @Override
    public ExpressionType type() {
        return descending ? ExpressionType.THEN_BY_DESCENDING : ExpressionType.THEN_BY;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
