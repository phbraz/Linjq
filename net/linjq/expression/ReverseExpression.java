package net.linjq.expression;

import java.util.List;
import java.util.Objects;

public record ReverseExpression<T>(QueryExpression<T> source) implements QueryExpression<T> {

    public ReverseExpression { Objects.requireNonNull(source); }

    @Override public ExpressionType type() { return ExpressionType.REVERSE; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
