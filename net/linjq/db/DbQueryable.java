package net.linjq.db;

import net.linjq.core.OrderedQueryable;
import net.linjq.core.Queryable;
import net.linjq.exceptions.LinjqException;
import net.linjq.functional.KeySelector;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A database-backed {@link Queryable} that translates operations to SQL
 * where possible and falls back to in-memory execution for the rest.
 *
 * <p>SQL-translated operations: {@code where}, {@code orderBy}, {@code orderByDescending},
 * {@code take}, {@code skip}, {@code distinct}.</p>
 *
 * <p>Client-side operations (run in-memory after fetch): {@code select}, {@code selectMany},
 * {@code groupBy}, and all other Queryable methods.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * import static net.linjq.db.Condition.*;
 *
 * var people = Linjq.fromDb(dataSource, Person.class, "people");
 *
 * List<String> names = people
 *     .where(Person::age, greaterThan(25))
 *     .where(Person::city, eq("London"))
 *     .orderBy(Person::name)
 *     .take(10)
 *     .select(Person::name)
 *     .toList();
 * }</pre>
 *
 * @param <T> the entity type (must be a record)
 */
public class DbQueryable<T extends Record> extends Queryable<T> {

    private final DbQueryBuilder builder;
    private final EntityMapper<T> mapper;
    private final ConnectionSource connectionSource;
    private final Class<T> entityClass;

    private DbQueryable(DbQueryBuilder builder, EntityMapper<T> mapper,
                        ConnectionSource connectionSource, Class<T> entityClass) {
        super(new LazyDbIterable<>(builder, mapper, connectionSource));
        this.builder = builder;
        this.mapper = mapper;
        this.connectionSource = connectionSource;
        this.entityClass = entityClass;
    }

    /**
     * Creates a DbQueryable from a DataSource.
     * Each query execution obtains and closes its own connection.
     */
    public static <T extends Record> DbQueryable<T> from(DataSource dataSource, Class<T> entityClass, String tableName) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        var builder = new DbQueryBuilder(tableName);
        var mapper = EntityMapper.forRecord(entityClass);
        return new DbQueryable<>(builder, mapper, new DataSourceConnection(dataSource), entityClass);
    }

    /**
     * Creates a DbQueryable from a Connection.
     * The caller is responsible for closing the connection.
     */
    public static <T extends Record> DbQueryable<T> from(Connection connection, Class<T> entityClass, String tableName) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        var builder = new DbQueryBuilder(tableName);
        var mapper = EntityMapper.forRecord(entityClass);
        return new DbQueryable<>(builder, mapper, new SingleConnection(connection), entityClass);
    }

    // ─── SQL-translated operations ────────────────────────────────────────

    /**
     * Adds a WHERE clause. The property reference is resolved to a column name
     * and the condition is translated to parameterized SQL.
     *
     * <pre>{@code
     * query.where(Person::age, greaterThan(25))
     * // → WHERE age > ?
     * }</pre>
     */
    public <V> DbQueryable<T> where(PropertyRef<T, V> property, Condition<V> condition) {
        Objects.requireNonNull(property, "property must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        String columnName = property.propertyName();
        var newBuilder = builder.addWhere(new WhereClause(columnName, condition));
        return new DbQueryable<>(newBuilder, mapper, connectionSource, entityClass);
    }

    /**
     * Adds an ORDER BY ASC clause.
     *
     * <pre>{@code
     * query.orderBy(Person::name)
     * // → ORDER BY name ASC
     * }</pre>
     */
    public <V extends Comparable<V>> DbQueryable<T> orderBy(PropertyRef<T, V> property) {
        Objects.requireNonNull(property, "property must not be null");
        var newBuilder = builder.addOrderBy(new OrderByClause(property.propertyName(), false));
        return new DbQueryable<>(newBuilder, mapper, connectionSource, entityClass);
    }

    /**
     * Adds an ORDER BY DESC clause.
     */
    public <V extends Comparable<V>> DbQueryable<T> orderByDescending(PropertyRef<T, V> property) {
        Objects.requireNonNull(property, "property must not be null");
        var newBuilder = builder.addOrderBy(new OrderByClause(property.propertyName(), true));
        return new DbQueryable<>(newBuilder, mapper, connectionSource, entityClass);
    }

    /**
     * Adds a secondary ORDER BY ASC clause.
     */
    public <V extends Comparable<V>> DbQueryable<T> thenBy(PropertyRef<T, V> property) {
        return orderBy(property);
    }

    /**
     * Adds a secondary ORDER BY DESC clause.
     */
    public <V extends Comparable<V>> DbQueryable<T> thenByDescending(PropertyRef<T, V> property) {
        return orderByDescending(property);
    }

    @Override
    public DbQueryable<T> take(int count) {
        return new DbQueryable<>(builder.withLimit(count), mapper, connectionSource, entityClass);
    }

    @Override
    public DbQueryable<T> skip(int count) {
        return new DbQueryable<>(builder.withOffset(count), mapper, connectionSource, entityClass);
    }

    @Override
    public DbQueryable<T> distinct() {
        return new DbQueryable<>(builder.withDistinct(), mapper, connectionSource, entityClass);
    }

    // ─── SQL inspection ───────────────────────────────────────────────────

    /**
     * Returns the SQL that would be executed, without executing it.
     * Useful for debugging and logging.
     */
    public String toSql() {
        return builder.build().sql();
    }

    /**
     * Returns the SQL parameters that would be bound.
     */
    public List<Object> toSqlParams() {
        return builder.build().params();
    }

    // ─── Connection sourcing ──────────────────────────────────────────────

    sealed interface ConnectionSource {
        Connection getConnection() throws SQLException;
        void releaseConnection(Connection conn) throws SQLException;
    }

    private record DataSourceConnection(DataSource dataSource) implements ConnectionSource {
        @Override
        public Connection getConnection() throws SQLException { return dataSource.getConnection(); }
        @Override
        public void releaseConnection(Connection conn) throws SQLException { conn.close(); }
    }

    private record SingleConnection(Connection connection) implements ConnectionSource {
        @Override
        public Connection getConnection() { return connection; }
        @Override
        public void releaseConnection(Connection conn) { /* caller manages */ }
    }

    // ─── Lazy iterable that executes SQL on first iteration ───────────────

    private static class LazyDbIterable<T extends Record> implements Iterable<T> {
        private final DbQueryBuilder builder;
        private final EntityMapper<T> mapper;
        private final ConnectionSource connectionSource;

        LazyDbIterable(DbQueryBuilder builder, EntityMapper<T> mapper, ConnectionSource connectionSource) {
            this.builder = builder;
            this.mapper = mapper;
            this.connectionSource = connectionSource;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            var sqlResult = builder.build();
            try {
                Connection conn = connectionSource.getConnection();
                try {
                    PreparedStatement stmt = conn.prepareStatement(sqlResult.sql());
                    setParameters(stmt, sqlResult.params());
                    ResultSet rs = stmt.executeQuery();

                    // Materialize results so we can close resources
                    List<T> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapper.map(rs));
                    }
                    rs.close();
                    stmt.close();
                    return results.iterator();
                } finally {
                    connectionSource.releaseConnection(conn);
                }
            } catch (SQLException e) {
                throw new LinjqException("Failed to execute query: " + sqlResult.sql(), e);
            }
        }

        private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                if (value == null) {
                    stmt.setNull(i + 1, java.sql.Types.NULL);
                } else if (value instanceof String s) {
                    stmt.setString(i + 1, s);
                } else if (value instanceof Integer n) {
                    stmt.setInt(i + 1, n);
                } else if (value instanceof Long n) {
                    stmt.setLong(i + 1, n);
                } else if (value instanceof Double n) {
                    stmt.setDouble(i + 1, n);
                } else if (value instanceof Float n) {
                    stmt.setFloat(i + 1, n);
                } else if (value instanceof Boolean b) {
                    stmt.setBoolean(i + 1, b);
                } else if (value instanceof java.math.BigDecimal bd) {
                    stmt.setBigDecimal(i + 1, bd);
                } else if (value instanceof java.time.LocalDate ld) {
                    stmt.setDate(i + 1, java.sql.Date.valueOf(ld));
                } else if (value instanceof java.time.LocalDateTime ldt) {
                    stmt.setTimestamp(i + 1, java.sql.Timestamp.valueOf(ldt));
                } else {
                    stmt.setObject(i + 1, value);
                }
            }
        }
    }
}