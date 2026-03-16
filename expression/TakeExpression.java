package io.linjq.expression;

import java.util.List;
import java.util.Objects;

/**
 * Represents a take (limit) operation.
 */
public record TakeExpression<T>(QueryExpression<T> source, int count) implements QueryExpression<T> {

    public TakeExpression {
        Objects.requireNonNull(source, "source must not be null");
    }

    @Override
    public ExpressionType type() { return ExpressionType.TAKE; }

    @Override
    public List<QueryExpression<?>> children() { return List.of(source); }
}
