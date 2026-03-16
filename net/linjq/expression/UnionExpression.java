package net.linjq.expression;

import java.util.List;
import java.util.Objects;

/**
 * Represents a union operation.
 */
public record UnionExpression<T>(QueryExpression<T> source, Iterable<T> other) implements QueryExpression<T> {

    public UnionExpression { Objects.requireNonNull(source); Objects.requireNonNull(other); }

    @Override public ExpressionType type() { return ExpressionType.UNION; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
