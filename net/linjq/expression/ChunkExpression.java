package net.linjq.expression;

import java.util.List;
import java.util.Objects;

public record ChunkExpression<T>(QueryExpression<T> source, int size) implements QueryExpression<List<T>> {

    public ChunkExpression { Objects.requireNonNull(source); }

    @Override public ExpressionType type() { return ExpressionType.CHUNK; }
    @Override public List<QueryExpression<?>> children() { return List.of(source); }
}
