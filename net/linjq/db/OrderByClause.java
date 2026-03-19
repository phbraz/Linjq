package net.linjq.db;

import java.util.Objects;

/**
 * Internal representation of an ORDER BY entry — a column name with a sort direction.
 */
final class OrderByClause {

    private final String columnName;
    private final boolean descending;

    OrderByClause(String columnName, boolean descending) {
        this.columnName = Objects.requireNonNull(columnName);
        this.descending = descending;
    }

    String columnName() { return columnName; }
    boolean descending() { return descending; }

    String toSql(String tableName) {
        String col = tableName != null ? tableName + "." + columnName : columnName;
        return col + (descending ? " DESC" : " ASC");
    }
}