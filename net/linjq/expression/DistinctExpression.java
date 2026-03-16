package net.linjq.expression;

import java.util.List;
import java.util.Objects;

/**
 * Represents a distinct operation.
 */
public record DistinctExpression<T>(QueryExpression<T> source) implements QueryExpression<T> {

    public DistinctExpression {
        Objects.requireNonNull(source, "source must not be null");
    }

    @Override
    public ExpressionType type() { return ExpressionType.DISTINCT; }

    @Override
    public List<QueryExpression<?>> children() { return List.of(source); }
}
