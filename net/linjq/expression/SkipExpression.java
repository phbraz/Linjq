package net.linjq.expression;

import java.util.List;
import java.util.Objects;

/**
 * Represents a skip (offset) operation.
 */
public record SkipExpression<T>(QueryExpression<T> source, int count) implements QueryExpression<T> {

    public SkipExpression {
        Objects.requireNonNull(source, "source must not be null");
    }

    @Override
    public ExpressionType type() { return ExpressionType.SKIP; }

    @Override
    public List<QueryExpression<?>> children() { return List.of(source); }
}
