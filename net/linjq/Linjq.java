package net.linjq;

import net.linjq.core.Queryable;
import net.linjq.db.DbQueryable;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Entry point for LINQ-style queries. Use a static import for fluent call sites:
 *
 * <pre>{@code
 * import static net.linjq.Linjq.from;
 * import static net.linjq.db.Condition.*;
 *
 * // In-memory
 * var names = from(people)
 *     .where(p -> p.age() > 25)
 *     .select(Person::name)
 *     .toList();
 *
 * // Database
 * var dbNames = fromDb(dataSource, Person.class, "people")
 *     .where(Person::age, greaterThan(25))
 *     .select(Person::name)
 *     .toList();
 * }</pre>
 */
public final class Linjq {

    private Linjq() {}

    /**
     * Creates an in-memory query over the given iterable.
     */
    public static <T> Queryable<T> from(Iterable<T> source) {
        return Queryable.from(source);
    }

    /**
     * Creates a database-backed query using a DataSource.
     * Each query execution obtains and closes its own connection.
     *
     * @param dataSource  the JDBC data source
     * @param entityClass the record class to map results to
     * @param tableName   the database table name
     * @param <T>         the entity type (must be a record)
     * @return a DbQueryable that translates operations to SQL
     */
    public static <T extends Record> DbQueryable<T> fromDb(DataSource dataSource, Class<T> entityClass, String tableName) {
        return DbQueryable.from(dataSource, entityClass, tableName);
    }

    /**
     * Creates a database-backed query using an existing Connection.
     * The caller is responsible for closing the connection.
     *
     * @param connection  the JDBC connection
     * @param entityClass the record class to map results to
     * @param tableName   the database table name
     * @param <T>         the entity type (must be a record)
     * @return a DbQueryable that translates operations to SQL
     */
    public static <T extends Record> DbQueryable<T> fromDb(Connection connection, Class<T> entityClass, String tableName) {
        return DbQueryable.from(connection, entityClass, tableName);
    }
}