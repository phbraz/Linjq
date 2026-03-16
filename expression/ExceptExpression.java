package io.linjq.expression;

import java.util.List;
import java.util.Objects;

public record ExceptExpression<T>(QueryExpression<T> source, Iterable<T> other) implements QueryExpression<T> {

    public ExceptExpression { Objects.requireNonNull(source); Objects.requireNonNull(other); }

    @Override public ExpressionType type() { return ExpressionType.EXCEPT; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
