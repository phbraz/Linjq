package net.linjq.db;

import net.linjq.Linjq;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static net.linjq.db.Condition.*;
import static org.junit.jupiter.api.Assertions.*;

class DbQueryableDbTest {

    record Person(int id, String name, Integer age, String city, Boolean active) {}

    // Disambiguate between Queryable.orderBy(KeySelector) and DbQueryable.orderBy(PropertyRef).
    private static final PropertyRef<Person, String> NAME = Person::name;
    private static final PropertyRef<Person, Integer> AGE = Person::age;
    private static final PropertyRef<Person, Integer> ID = Person::id;

    private static DataSource dataSource(String dbName) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static void setupSchemaAndData(DataSource ds, List<Person> people) throws SQLException {
        try (Connection conn = ds.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS people");
            stmt.execute("""
                    CREATE TABLE people (
                        id INT,
                        name VARCHAR(100),
                        age INT,
                        city VARCHAR(100),
                        active BOOLEAN
                    )
                    """);
        }

        String insertSql = "INSERT INTO people (id, name, age, city, active) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (Person p : people) {
                ps.setInt(1, p.id());
                ps.setString(2, p.name());
                if (p.age() != null) {
                    ps.setInt(3, p.age());
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                if (p.city() != null) {
                    ps.setString(4, p.city());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                if (p.active() != null) {
                    ps.setBoolean(5, p.active());
                } else {
                    ps.setNull(5, Types.BOOLEAN);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Test
    void toSql_buildsWhereAndParams() {
        DataSource ds = dataSource("toSql_buildsWhereAndParams");

        var q = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::age, greaterThan(25))
                .where(Person::city, eq("London"));

        assertEquals("SELECT * FROM people WHERE age > ? AND city = ?", q.toSql());
        assertEquals(List.of(25, "London"), q.toSqlParams());
    }

    @Test
    void toSql_buildsCompositeAnd() {
        DataSource ds = dataSource("toSql_buildsCompositeAnd");

        var q = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::age, greaterThan(20).and(lessThan(40)));

        assertEquals("SELECT * FROM people WHERE (age > ? AND age < ?)", q.toSql());
        assertEquals(List.of(20, 40), q.toSqlParams());
    }

    @Test
    void toSql_buildsInClause() {
        DataSource ds = dataSource("toSql_buildsInClause");

        var q = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::city, in("London", "Paris"));

        assertEquals("SELECT * FROM people WHERE city IN (?, ?)", q.toSql());
        assertEquals(List.of("London", "Paris"), q.toSqlParams());
    }

    @Test
    void queryExecutesWhereOrderSkipTake_select() throws SQLException {
        DataSource ds = dataSource("queryExecutesWhereOrderSkipTake_select");
        setupSchemaAndData(ds, List.of(
                new Person(1, "Alice", 30, "London", true),
                new Person(2, "Bob", 25, "Paris", false),
                new Person(3, "Charlie", 35, "London", true),
                new Person(4, "Diana", 28, "Berlin", null),
                new Person(5, "Eve", 25, "Paris", false),
                new Person(6, "Frank", null, null, true),
                new Person(7, "Grace", 40, null, null)
        ));

        var names = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::age, greaterThanOrEqual(25))
                .orderBy(NAME)
                .skip(1)
                .take(3)
                .select(Person::name)
                .toList();

        assertEquals(List.of("Bob", "Charlie", "Diana"), names);
    }

    @Test
    void queryExecutesBetween_orderByThenBy() throws SQLException {
        DataSource ds = dataSource("queryExecutesBetween_orderByThenBy");
        setupSchemaAndData(ds, List.of(
                new Person(1, "Alice", 30, "London", true),
                new Person(2, "Bob", 25, "Paris", false),
                new Person(3, "Charlie", 35, "London", true),
                new Person(4, "Diana", 28, "Berlin", null),
                new Person(5, "Eve", 25, "Paris", false),
                new Person(6, "Frank", null, null, true),
                new Person(7, "Grace", 40, null, null)
        ));

        var names = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::age, between(25, 35))
                .orderByDescending(AGE)
                .thenBy(NAME)
                .select(Person::name)
                .toList();

        assertEquals(List.of("Charlie", "Alice", "Diana", "Bob", "Eve"), names);
    }

    @Test
    void queryExecutesNullPredicates() throws SQLException {
        DataSource ds = dataSource("queryExecutesNullPredicates");
        setupSchemaAndData(ds, List.of(
                new Person(1, "Alice", 30, "London", true),
                new Person(2, "Bob", 25, "Paris", false),
                new Person(3, "Charlie", 35, "London", true),
                new Person(4, "Diana", 28, "Berlin", null),
                new Person(5, "Eve", 25, "Paris", false),
                new Person(6, "Frank", null, null, true),
                new Person(7, "Grace", 40, null, null)
        ));

        var cityIsNull = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::city, isNull())
                .orderBy(ID)
                .select(Person::name)
                .toList();

        assertEquals(List.of("Frank", "Grace"), cityIsNull);

        var activeIsNotNull = Linjq.fromDb(ds, Person.class, "people")
                .where(Person::active, isNotNull())
                .orderBy(NAME)
                .select(Person::name)
                .toList();

        assertEquals(List.of("Alice", "Bob", "Charlie", "Eve", "Frank"), activeIsNotNull);
    }

    @Test
    void queryExecutesDistinct() throws SQLException {
        DataSource ds = dataSource("queryExecutesDistinct");
        setupSchemaAndData(ds, List.of(
                new Person(1, "Alice", 30, "London", true),
                new Person(1, "Alice", 30, "London", true), // exact duplicate row
                new Person(2, "Bob", 25, "Paris", false),
                new Person(3, "Eve", 25, "Paris", false)
        ));

        var names = Linjq.fromDb(ds, Person.class, "people")
                .distinct()
                .orderBy(ID)
                .select(Person::name)
                .toList();

        assertEquals(List.of("Alice", "Bob", "Eve"), names);
    }
}

