# LINJQ — Language Integrated Java Query

I was a bit sick of Java and how it handles collections and how you interact with them. If you're coming from .NET, this should feel really familiar to LINQ, hence the play on the project name. With that out of the way, please feel free to use it and provide suggestions for improvements if you ever find this repo.

LINQ-style fluent query API for Java 21+. Build composable queries over any `Iterable`, execute in-memory or plug in your own backend (e.g. SQL) via the provider model.

- **In-memory** — Use `from(iterable)` (static import) or `Queryable.from(iterable)`; runs in memory with lazy iteration where possible.
- **Provider model** — `ProviderQueryable` + `QueryProvider` build expression trees; execution is delegated to a provider (e.g. `InMemoryQueryProvider` or a future SQL translator).

## Requirements

- **Java 21+**
- **Maven 3.6+** (for building)

## Installation

### Maven

```xml
<dependency>
    <groupId>net.linjq</groupId>
    <artifactId>linjq</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Build from source

```bash
git clone https://github.com/phbraz/linjq.git
cd linjq
mvn install
```

---

## Quick start

Add a **static import** so you can write `from(people).where(...)` instead of `Queryable.from(people).where(...)`:

```java
import static net.linjq.Linjq.from;
import java.util.List;

record Person(String name, int age, String city) {}

List<Person> people = List.of(
    new Person("Alice", 30, "London"),
    new Person("Bob", 25, "Paris"),
    new Person("Charlie", 35, "London")
);

List<String> names = from(people)
    .where(p -> p.age() >= 30)
    .orderBy(Person::name)
    .select(Person::name)
    .take(10)
    .toList();
// → ["Alice", "Charlie"]
```

You can also use `from(...)` directly if you prefer.

---

## API overview

### Creating a query

| Method | Description |
|--------|-------------|
| `from(Iterable<T>)` | In-memory query (use `import static net.linjq.Linjq.from;`). |
| `Queryable.from(Iterable<T>)` | Same, without static import. |
| `ProviderQueryable.from(QueryProvider, Iterable<T>)` | Provider-backed query (builds expression tree). |
| `ProviderQueryable.from(QueryProvider, Iterable<T>, String alias)` | Same with a source alias (e.g. table name). |
| `Linjq.fromDb(DataSource, Class<T>, String)` | Database-backed query via JDBC (`DbQueryable<T>`). |
| `Linjq.fromDb(Connection, Class<T>, String)` | Same, using a caller-managed JDBC connection. |

**Example — in-memory (with static import):**

```java
import static net.linjq.Linjq.from;

var query = from(List.of(1, 2, 3, 4, 5));
```

**Example — provider (expression tree):**

```java
var provider = new InMemoryQueryProvider();
var query = ProviderQueryable.from(provider, people, "people");
QueryExpression<?> expr = query.getExpression(); // inspect tree
```

---

### Database-backed queries (`DbQueryable`)

`DbQueryable<T>` translates a subset of operations to SQL and executes the rest in-memory after fetching rows.

SQL-translated operations: `where`, `orderBy` / `orderByDescending`, `thenBy` / `thenByDescending`, `take`, `skip`, `distinct`.
Client-side operations: `select`, `selectMany`, `groupBy`, joins, and all other `Queryable` methods.

#### `fromDb(...)`

```java
import static net.linjq.Linjq.fromDb;
import static net.linjq.db.Condition.*;

record Person(int id, String name, Integer age, String city, Boolean active) {}

// Use a DataSource (each execution opens/closes its own connection)
DbQueryable<Person> people = fromDb(ds, Person.class, "people");

var query =
    people
        .where(Person::age, greaterThanOrEqual(25))
        .where(Person::city, eq("London"))
        .orderBy(Person::name)
        .take(10);

// Inspect generated SQL (parameterized)
String sql = query.toSql();
List<Object> params = query.toSqlParams();

// Materialize / continue in-memory
List<String> names = query
    .select(Person::name)
    .toList();
```

#### `Condition` DSL (`where(PropertyRef, Condition)`)

`DbQueryable.where` does not take a Java `Predicate`; instead it takes:

- a `PropertyRef` (method reference) to infer the column name
- a `Condition` to build a parameterized SQL `WHERE` fragment

Conditions can be combined with `and(...)`, `or(...)`, and `not()`.

```java
var q = fromDb(ds, Person.class, "people")
    .where(Person::age, greaterThan(20).and(lessThan(40))) // (age > ? AND age < ?)
    .where(Person::city, in("London", "Paris"))            // city IN (?, ?)
    .where(Person::active, isNull());                       // active IS NULL
```

---

### Filtering

#### `where(Predicate<T>)`

Keeps only elements that satisfy the predicate.

```java
from(people)
    .where(p -> p.age() > 25)
    .toList();

from(List.of(1, 2, 3, 4, 5))
    .where(n -> n % 2 == 0)
    .toList();  // [2, 4]
```

---

### Projection

#### `select(Function<T, R>)`

Maps each element to a new value.

```java
from(people)
    .select(Person::name)
    .toList();  // ["Alice", "Bob", "Charlie", ...]

from(people)
    .select(p -> p.name() + " (" + p.age() + ")")
    .toList();
```

#### `selectMany(Function<T, Iterable<R>>)`

Maps each element to a sequence and flattens the result (flatMap).

```java
var words = List.of("hello", "world");
from(words)
    .selectMany(s -> List.of(s.split("")))
    .toList();  // ["h","e","l","l","o","w","o","r","l","d"]

from(people)
    .selectMany(p -> List.of(p.name(), p.city()))
    .toList();
```

#### `selectManyIndexed(BiFunction<T, Integer, Iterable<R>>)`

Like `selectMany`, but the selector also receives the *0-based index* of the outer element.

```java
var result = from(List.of("ab", "c"))
    .selectManyIndexed((s, i) ->
        s.chars()
         .mapToObj(ch -> (char) ch + String.valueOf(i))
         .toList())
    .toList();
// example output: ["a0","b0","c1"]
```

---

### Ordering

#### `orderBy(KeySelector<T, K>)` / `orderByDescending(KeySelector<T, K>)`

Sort by a comparable key. Return an `OrderedQueryable<T>`.

```java
from(people)
    .orderBy(Person::name)
    .select(Person::name)
    .toList();  // alphabetical by name

from(people)
    .orderByDescending(Person::age)
    .take(2)
    .select(Person::name)
    .toList();  // two oldest by age
```

#### `thenBy` / `thenByDescending` (on `OrderedQueryable`)

Secondary sort key (use after `orderBy` / `orderByDescending`).

```java
from(people)
    .orderBy(Person::city)
    .thenBy(Person::name)
    .select(p -> p.city() + " - " + p.name())
    .toList();
```

---

### Grouping

#### `groupBy(Function<T, K>)`

Groups elements by key. Returns `Queryable<Grouping<K, T>>`; each `Grouping<K, T>` has `key()` and is `Iterable<T>`.

```java
var byCity = from(people)
    .groupBy(Person::city)
    .toList();

for (var g : byCity) {
    System.out.println(g.key());       // e.g. "London"
    for (var p : g) {
        System.out.println("  " + p.name());
    }
}
```

---

### Joins

#### `join` (inner join)

Matches outer and inner by key; result selector builds each row.

```java
record Order(String customer, double amount) {}
var orders = List.of(
    new Order("Alice", 100.0),
    new Order("Bob", 200.0)
);

from(people)
    .join(orders, Person::name, Order::customer,
          (p, o) -> p.name() + " => $" + o.amount())
    .toList();
// ["Alice => $100.0", "Bob => $200.0"]
```

#### `leftJoin` (left outer join)

Like inner join, but every outer row appears once; if no match, inner is `null`.

```java
from(people)
    .leftJoin(orders, Person::name, Order::customer,
              (p, o) -> p.name() + ": " + (o != null ? o.amount() : "no orders"))
    .toList();
// Every person listed; "no orders" when no matching order
```

#### `groupJoin`

For each outer element, groups all matching inner elements into a `Queryable<TInner>` and passes it to the result selector.

```java
from(people)
    .groupJoin(orders, Person::name, Order::customer,
               (p, orderQuery) -> p.name() + " has " + orderQuery.toList().size() + " order(s)")
    .toList();
```

#### `crossJoin` (cartesian product)

Every pair (outer, inner); result selector builds each row.

```java
var a = List.of("X", "Y");
var b = List.of(1, 2);
from(a)
    .crossJoin(b, (s, n) -> s + n)
    .toList();  // ["X1", "X2", "Y1", "Y2"]
```

---

### Partitioning and limiting

#### `take(int n)`

First `n` elements.

```java
from(people).take(2).toList();
from(List.of(1, 2, 3, 4, 5)).take(3).toList();  // [1, 2, 3]
```

#### `skip(int n)`

Skips the first `n` elements.

```java
from(List.of(1, 2, 3, 4, 5)).skip(2).toList();  // [3, 4, 5]
from(people).skip(1).take(2).toList();         // pagination
```

---

### Set operations

#### `distinct()`

Removes duplicates (order preserved by insertion).

```java
from(List.of(1, 2, 2, 3, 3, 3)).distinct().toList();  // [1, 2, 3]
from(people).select(Person::city).distinct().toList();  // unique cities
```

#### `union(Iterable<T>)`

Set union: all elements from both, duplicates removed.

```java
from(List.of(1, 2, 3))
    .union(List.of(3, 4, 5))
    .toList();  // [1, 2, 3, 4, 5]
```

#### `intersect(Iterable<T>)`

Set intersection: elements that appear in both.

```java
from(List.of(1, 2, 3))
    .intersect(List.of(2, 3, 4))
    .toList();  // [2, 3]
```

#### `except(Iterable<T>)`

Set difference: elements in the first sequence but not in the second.

```java
from(List.of(1, 2, 3, 4))
    .except(List.of(2, 4))
    .toList();  // [1, 3]
```

#### `concat(Iterable<T>)`

Concatenates two sequences (keeps duplicates, order preserved).

```java
from(List.of(1, 2))
    .concat(List.of(3, 4))
    .toList();  // [1, 2, 3, 4]
```

---

### Other operations

#### `chunk(int size)`

Splits the sequence into chunks of the given size. Returns `Queryable<List<T>>`.

```java
from(List.of(1, 2, 3, 4, 5))
    .chunk(2)
    .toList();
// [[1, 2], [3, 4], [5]]
```

#### `reverse()`

Reverses the order of elements.

```java
from(List.of(1, 2, 3)).reverse().toList();  // [3, 2, 1]
```

---

### Terminal operations

| Method | Description |
|--------|-------------|
| `toList()` | Materializes the query into a `List<T>`. |
| `first()` | Returns the first element; throws `NoSuchElementException` if empty. |
| `firstOrDefault()` | Returns `Optional<T>`: first element if present, otherwise empty. |
| `single()` | Returns the only element; throws if empty or if more than one element exists. |
| `singleOrDefault()` | Returns `Optional<T>`: the only element if present, otherwise empty (throws if more than one). |
| `count()` | Counts elements in the sequence. |
| `any()` | Returns `true` if the sequence has at least one element. |
| `any(Predicate<T>)` | Returns `true` if at least one element matches the predicate. |
| `all(Predicate<T>)` | Returns `true` if all elements match the predicate (vacuously `true` for empty sequences). |
| `contains(T)` | Returns `true` if the sequence contains an element equal to the given value (`equals`). |
| `minBy(Function<T, R>)` | Returns the element with the minimum key (`R` must be `Comparable`), or empty if the sequence is empty. |
| `maxBy(Function<T, R>)` | Returns the element with the maximum key (`R` must be `Comparable`), or empty if the sequence is empty. |
| `aggregate(R seed, BiFunction<R, T, R>)` | Folds the sequence starting from `seed` using an accumulator function. |
| `toLookup(Function<T, K>)` | Groups elements by key into a `Lookup<K, T>` (insertion-order keys). |
| `toLookup(Function<T, K>, Function<T, V>)` | Same, but transforms elements as they are added to each group. |
| `iterator()` | Returns an iterator (e.g. for `for (T x : query)`). |

```java
List<Person> list = from(people).where(p -> p.age() > 30).toList();
Person first = from(people).orderBy(Person::name).first();
for (Person p : from(people).take(5)) {
    // ...
}

var firstOrDefault = from(people).where(p -> p.age() > 100).firstOrDefault();
int count = from(people).where(p -> p.city().equals("London")).count();

var lookup = from(people).toLookup(Person::city);
List<Person> londoners = lookup.get("London").toList();
```

---

## Provider model and expression trees

When you use `ProviderQueryable`, each call (e.g. `where`, `select`, `take`) does **not** run the query; it adds a node to an **expression tree**. Execution happens only when you iterate or call a terminal method (e.g. `toList()`, `first()`, `single()`, `count()`, etc.), and the **QueryProvider** is responsible for running the tree.

### InMemoryQueryProvider

Runs the expression tree in memory by interpreting it (same semantics as `from(iterable)`).

```java
import net.linjq.provider.InMemoryQueryProvider;
import net.linjq.provider.ProviderQueryable;

var provider = new InMemoryQueryProvider();
var query = ProviderQueryable.from(provider, people)
    .where(p -> p.age() > 25)
    .orderBy(Person::name)
    .take(10);

// Inspect the tree
QueryExpression<?> expr = query.getExpression();

// Execute via the provider
List<Person> results = query.toList();
```

### Implementing your own provider

Implement `QueryProvider`: translate the expression tree (e.g. to SQL) in `execute`, and wrap expressions in `ProviderQueryable` in `createQuery`.

```java
public class SqlQueryProvider implements QueryProvider {
    @Override
    public <T> Iterable<T> execute(QueryExpression<T> expression) {
        String sql = new SqlTranslator().translate(expression);
        return runQuery(sql);
    }

    @Override
    public <T> ProviderQueryable<T> createQuery(QueryExpression<T> expression) {
        return new ProviderQueryable<>(this, expression);
    }
}
```

Use `ExpressionVisitor` to walk the tree and produce SQL (or another representation).

---
## License

MIT
