package net.linjq.expression;

/**
 * Visitor pattern for walking expression trees.
 * Query providers extend this to translate or optimize expressions.
 *
 * <p>Each visit method receives the expression node and returns a result.
 * The default implementation visits children recursively.</p>
 *
 * <h2>Example: SQL Translation</h2>
 * <pre>{@code
 * public class SqlVisitor extends ExpressionVisitor<String> {
 *     @Override
 *     public String visitSource(SourceExpression<?> expr) {
 *         return expr.alias() != null ? expr.alias() : "source";
 *     }
 *
 *     @Override
 *     public String visitWhere(WhereExpression<?> expr) {
 *         return visit(expr.source()) + " WHERE ...";
 *     }
 *
 *     @Override
 *     public String visitTake(TakeExpression<?> expr) {
 *         return visit(expr.source()) + " LIMIT " + expr.count();
 *     }
 * }
 * }</pre>
 *
 * @param <R> the result type produced by visiting each node
 */
public abstract class ExpressionVisitor<R> {

    /**
     * Dispatches to the appropriate visit method based on expression type.
     */
    public R visit(QueryExpression<?> expression) {
        return switch (expression) {
            case SourceExpression<?> e -> visitSource(e);
            case WhereExpression<?> e -> visitWhere(e);
            case SelectExpression<?, ?> e -> visitSelect(e);
            case SelectManyExpression<?, ?> e -> visitSelectMany(e);
            case SelectManyIndexedExpression<?, ?> e -> visitSelectManyIndexed(e);
            case OrderByExpression<?, ?> e -> visitOrderBy(e);
            case ThenByExpression<?, ?> e -> visitThenBy(e);
            case GroupByExpression<?, ?> e -> visitGroupBy(e);
            case JoinExpression<?, ?, ?, ?> e -> visitJoin(e);
            case LeftJoinExpression<?, ?, ?, ?> e -> visitLeftJoin(e);
            case GroupJoinExpression<?, ?, ?, ?> e -> visitGroupJoin(e);
            case CrossJoinExpression<?, ?, ?> e -> visitCrossJoin(e);
            case TakeExpression<?> e -> visitTake(e);
            case SkipExpression<?> e -> visitSkip(e);
            case DistinctExpression<?> e -> visitDistinct(e);
            case UnionExpression<?> e -> visitUnion(e);
            case IntersectExpression<?> e -> visitIntersect(e);
            case ExceptExpression<?> e -> visitExcept(e);
            case ConcatExpression<?> e -> visitConcat(e);
            case ChunkExpression<?> e -> visitChunk(e);
            case ReverseExpression<?> e -> visitReverse(e);
        };
    }

    // ── Override these in your provider ────────────

    protected abstract R visitSource(SourceExpression<?> expr);
    protected abstract R visitWhere(WhereExpression<?> expr);
    protected abstract R visitSelect(SelectExpression<?, ?> expr);
    protected abstract R visitSelectMany(SelectManyExpression<?, ?> expr);
    protected abstract R visitSelectManyIndexed(SelectManyIndexedExpression<?, ?> expr);
    protected abstract R visitOrderBy(OrderByExpression<?, ?> expr);
    protected abstract R visitThenBy(ThenByExpression<?, ?> expr);
    protected abstract R visitGroupBy(GroupByExpression<?, ?> expr);
    protected abstract R visitJoin(JoinExpression<?, ?, ?, ?> expr);
    protected abstract R visitLeftJoin(LeftJoinExpression<?, ?, ?, ?> expr);
    protected abstract R visitGroupJoin(GroupJoinExpression<?, ?, ?, ?> expr);
    protected abstract R visitCrossJoin(CrossJoinExpression<?, ?, ?> expr);
    protected abstract R visitTake(TakeExpression<?> expr);
    protected abstract R visitSkip(SkipExpression<?> expr);
    protected abstract R visitDistinct(DistinctExpression<?> expr);
    protected abstract R visitUnion(UnionExpression<?> expr);
    protected abstract R visitIntersect(IntersectExpression<?> expr);
    protected abstract R visitExcept(ExceptExpression<?> expr);
    protected abstract R visitConcat(ConcatExpression<?> expr);
    protected abstract R visitChunk(ChunkExpression<?> expr);
    protected abstract R visitReverse(ReverseExpression<?> expr);
}
