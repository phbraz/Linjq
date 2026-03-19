package net.linjq.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Internal representation of a WHERE clause entry — a column name paired with a condition.
 * {@link DbQueryable} accumulates these and passes them to {@link DbQueryProvider} for translation.
 */
final class WhereClause {

    private final String columnName;
    private final Condition<?> condition;

    WhereClause(String columnName, Condition<?> condition) {
        this.columnName = Objects.requireNonNull(columnName);
        this.condition = Objects.requireNonNull(condition);
    }

    String columnName() { return columnName; }
    Condition<?> condition() { return condition; }

    /**
     * Translates this clause to a SQL fragment, appending parameter values to the params list.
     */
    String toSql(String tableName, List<Object> params) {
        String col = tableName != null ? tableName + "." + columnName : columnName;
        return conditionToSql(col, condition, params);
    }

    @SuppressWarnings("unchecked")
    private String conditionToSql(String col, Condition<?> cond, List<Object> params) {
        return switch (cond) {
            case Condition.Eq<?> c -> { params.add(c.value()); yield col + " = ?"; }
            case Condition.Neq<?> c -> { params.add(c.value()); yield col + " <> ?"; }
            case Condition.Gt<?> c -> { params.add(c.value()); yield col + " > ?"; }
            case Condition.Gte<?> c -> { params.add(c.value()); yield col + " >= ?"; }
            case Condition.Lt<?> c -> { params.add(c.value()); yield col + " < ?"; }
            case Condition.Lte<?> c -> { params.add(c.value()); yield col + " <= ?"; }
            case Condition.Like c -> { params.add(c.pattern()); yield col + " LIKE ?"; }
            case Condition.NotLike c -> { params.add(c.pattern()); yield col + " NOT LIKE ?"; }
            case Condition.Between<?> c -> {
                params.add(c.low());
                params.add(c.high());
                yield col + " BETWEEN ? AND ?";
            }
            case Condition.In<?> c -> {
                var placeholders = new ArrayList<String>();
                for (var v : c.values()) {
                    params.add(v);
                    placeholders.add("?");
                }
                yield col + " IN (" + String.join(", ", placeholders) + ")";
            }
            case Condition.IsNull<?> c -> col + " IS NULL";
            case Condition.IsNotNull<?> c -> col + " IS NOT NULL";
            case Condition.And<?> c -> {
                String left = conditionToSql(col, c.left(), params);
                String right = conditionToSql(col, c.right(), params);
                yield "(" + left + " AND " + right + ")";
            }
            case Condition.Or<?> c -> {
                String left = conditionToSql(col, c.left(), params);
                String right = conditionToSql(col, c.right(), params);
                yield "(" + left + " OR " + right + ")";
            }
            case Condition.Not<?> c -> {
                String inner = conditionToSql(col, c.inner(), params);
                yield "NOT (" + inner + ")";
            }
        };
    }
}