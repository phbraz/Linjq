package io.linjq.expression;

import java.util.List;
import java.util.Objects;

public record ConcatExpression<T>(QueryExpression<T> source, Iterable<T> other) implements QueryExpression<T> {

    public ConcatExpression { Objects.requireNonNull(source); Objects.requireNonNull(other); }

    @Override public ExpressionType type() { return ExpressionType.CONCAT; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
