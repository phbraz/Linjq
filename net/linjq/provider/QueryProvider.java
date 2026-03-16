package net.linjq.provider;

import net.linjq.expression.QueryExpression;

/**
 * Interface for query execution backends. A QueryProvider translates
 * expression trees into executable queries and returns results.
 *
 * <p>Analogous to C# LINQ's {@code IQueryProvider}.</p>
 *
 * <h2>Implementing a Provider</h2>
 * <pre>{@code
 * public class SqlQueryProvider implements QueryProvider {
 *     private final DataSource dataSource;
 *
 *     @Override
 *     public <T> Iterable<T> execute(QueryExpression<T> expression) {
 *         String sql = new SqlTranslator().translate(expression);
 *         return executeQuery(sql);
 *     }
 *
 *     @Override
 *     public <T> ProviderQueryable<T> createQuery(QueryExpression<T> expression) {
 *         return new ProviderQueryable<>(this, expression);
 *     }
 * }
 * }</pre>
 */
public interface QueryProvider {

    /**
     * Executes the expression tree and returns the results.
     *
     * @param expression the expression tree to execute
     * @param <T> the element type of the result
     * @return the query results as an Iterable
     */
    <T> Iterable<T> execute(QueryExpression<T> expression);

    /**
     * Creates a new ProviderQueryable wrapping the given expression.
     * Used internally by ProviderQueryable to chain operations.
     *
     * @param expression the expression tree
     * @param <T> the element type
     * @return a new ProviderQueryable backed by this provider
     */
    <T> ProviderQueryable<T> createQuery(QueryExpression<T> expression);
}
