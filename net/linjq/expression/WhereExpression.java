package net.linjq.expression;

import net.linjq.functional.Predicate;

import java.util.List;
import java.util.Objects;

/**
 * Represents a filter (where) operation in the expression tree.
 *
 * @param <T> the element type
 */
public record WhereExpression<T>(
        QueryExpression<T> source,
        Predicate<T> predicate
) implements QueryExpression<T> {

    public WhereExpression {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
    }

    @Override
    public ExpressionType type() {
        return ExpressionType.WHERE;
    }

    @Override
    public List<QueryExpression<?>> children() {
        return List.of(source);
    }
}
