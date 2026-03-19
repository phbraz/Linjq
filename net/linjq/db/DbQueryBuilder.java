package net.linjq.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable builder that accumulates SQL clauses as the user chains
 * {@link DbQueryable} methods. Each mutation returns a new builder.
 */
final class DbQueryBuilder {

    private final String tableName;
    private final List<WhereClause> whereClauses;
    private final List<OrderByClause> orderByClauses;
    private final int limit;
    private final int offset;
    private final boolean distinct;

    DbQueryBuilder(String tableName) {
        this(tableName, List.of(), List.of(), -1, -1, false);
    }

    private DbQueryBuilder(String tableName, List<WhereClause> whereClauses,
                           List<OrderByClause> orderByClauses,
                           int limit, int offset, boolean distinct) {
        this.tableName = tableName;
        this.whereClauses = whereClauses;
        this.orderByClauses = orderByClauses;
        this.limit = limit;
        this.offset = offset;
        this.distinct = distinct;
    }

    DbQueryBuilder addWhere(WhereClause clause) {
        var newClauses = new ArrayList<>(whereClauses);
        newClauses.add(clause);
        return new DbQueryBuilder(tableName, Collections.unmodifiableList(newClauses),
                orderByClauses, limit, offset, distinct);
    }

    DbQueryBuilder addOrderBy(OrderByClause clause) {
        var newClauses = new ArrayList<>(orderByClauses);
        newClauses.add(clause);
        return new DbQueryBuilder(tableName, whereClauses,
                Collections.unmodifiableList(newClauses), limit, offset, distinct);
    }

    DbQueryBuilder withLimit(int limit) {
        return new DbQueryBuilder(tableName, whereClauses, orderByClauses, limit, offset, distinct);
    }

    DbQueryBuilder withOffset(int offset) {
        return new DbQueryBuilder(tableName, whereClauses, orderByClauses, limit, offset, distinct);
    }

    DbQueryBuilder withDistinct() {
        return new DbQueryBuilder(tableName, whereClauses, orderByClauses, limit, offset, true);
    }

    /**
     * Builds the parameterized SQL query.
     */
    SqlResult build() {
        var params = new ArrayList<Object>();
        var sb = new StringBuilder();

        // SELECT
        sb.append("SELECT ");
        if (distinct) sb.append("DISTINCT ");
        sb.append("*");

        // FROM
        sb.append(" FROM ").append(tableName);

        // WHERE
        if (!whereClauses.isEmpty()) {
            sb.append(" WHERE ");
            var parts = new ArrayList<String>();
            for (var clause : whereClauses) {
                parts.add(clause.toSql(null, params));
            }
            sb.append(String.join(" AND ", parts));
        }

        // ORDER BY
        if (!orderByClauses.isEmpty()) {
            sb.append(" ORDER BY ");
            var parts = new ArrayList<String>();
            for (var clause : orderByClauses) {
                parts.add(clause.toSql(null));
            }
            sb.append(String.join(", ", parts));
        }

        // LIMIT / OFFSET
        if (limit >= 0) sb.append(" LIMIT ").append(limit);
        if (offset >= 0) sb.append(" OFFSET ").append(offset);

        return new SqlResult(sb.toString(), Collections.unmodifiableList(params));
    }

    String tableName() { return tableName; }
    int limit() { return limit; }
    int offset() { return offset; }

    record SqlResult(String sql, List<Object> params) {}
}