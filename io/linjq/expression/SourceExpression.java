package io.linjq.expression;

import java.util.List;
import java.util.Objects;

/**
 * Represents the root data source of a query.
 * This is always a leaf node in the expression tree.
 *
 * @param <T> the element type of the source
 */
public record SourceExpression<T>(Iterable<T> source, String alias) implements QueryExpression<T> {

    public SourceExpression {
        Objects.requireNonNull(source, "source must not be null");
    }

    public SourceExpression(Iterable<T> source) {
        this(source, null);
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.SOURCE;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of();
    }
}
