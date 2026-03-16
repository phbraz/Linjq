package net.linjq.expression;

import java.util.List;
import java.util.Objects;

public record IntersectExpression<T>(QueryExpression<T> source, Iterable<T> other) implements QueryExpression<T> {

    public IntersectExpression { Objects.requireNonNull(source); Objects.requireNonNull(other); }

    @Override public ExpressionType type() { return ExpressionType.INTERSECT; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
