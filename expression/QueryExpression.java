package io.linjq.expression;

import io.linjq.functional.BiFunction;
import io.linjq.functional.Function;
import io.linjq.functional.KeySelector;
import io.linjq.functional.Predicate;

import java.util.List;
import java.util.Objects;

/**
 * Represents a query operation as an immutable data structure (expression tree).
 * This is the foundation of LINJQ's provider model — instead of executing
 * immediately, queries can be captured as expression trees, inspected,
 * optimized, and translated to different backends (SQL, JSON, XML, etc.).
 *
 * <p>Analogous to C# LINQ's {@code System.Linq.Expressions.Expression}.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // This expression tree represents: source.where(age > 18).select(name).take(10)
 * var expr = new TakeExpression<>(
 *     new SelectExpression<>(
 *         new WhereExpression<>(
 *             new SourceExpression<>(people),
 *             p -> p.age() > 18
 *         ),
 *         Person::name
 *     ),
 *     10
 * );
 *
 * // A provider can walk this tree and translate it
 * String sql = sqlProvider.translate(expr);
 * // → "SELECT name FROM people WHERE age > 18 LIMIT 10"
 * }</pre>
 *
 * @param <T> the element type that this expression produces
 */
public sealed interface QueryExpression<T>
        permits SourceExpression, WhereExpression, SelectExpression, SelectManyExpression,
                OrderByExpression, ThenByExpression, GroupByExpression,
                JoinExpression, LeftJoinExpression, GroupJoinExpression, CrossJoinExpression,
                TakeExpression, SkipExpression, DistinctExpression,
                UnionExpression, IntersectExpression, ExceptExpression, ConcatExpression,
                ChunkExpression, ReverseExpression {

    /**
     * Returns the type of this expression node.
     */
    ExpressionType type();

    /**
     * Returns the child expression(s) that this expression depends on.
     * Source expressions return an empty list.
     */
    List<QueryExpression<?>> children();
}
