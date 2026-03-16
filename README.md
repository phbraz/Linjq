# LINJQ — Language Integrated Java Query

LINQ-style fluent query API for Java 21+. Build composable queries over any `Iterable`, execute in-memory or plug in your own backend (e.g. SQL) via the provider model.

- **In-memory** — `Queryable.from(iterable)` runs all operations in memory with lazy iteration where possible.
- **Provider model** — `ProviderQueryable` + `QueryProvider` build expression trees; execution is delegated to a provider (e.g. `InMemoryQueryProvider` or a future SQL translator).

## Requirements

- **Java 21+**
- **Maven 3.6+** (for building)

## Installation

### Maven

```xml
<dependency>
    <groupId>io.linjq</groupId>
    <artifactId>linjq</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Build from source

```bash
git clone https://github.com/YOUR_USERNAME/linjq.git
cd linjq
mvn install
```

---

## Quick start

```java
import io.linjq.core.Queryable;
import java.util.List;

record Person(String name, int age, String city) {}

List<Person> people = List.of(
    new Person("Alice", 30, "London"),
    new Person("Bob", 25, "Paris"),
    new Person("Charlie", 35, "London")
);

List<String> names = Queryable.from(people)
    .where(p -> p.age() >= 30)
    .orderBy(Person::name)
    .select(Person::name)
    .take(10)
    .toList();
// → ["Alice", "Charlie"]
```

---

## API overview

### Creating a query

| Method | Description |
|--------|-------------|
| `Queryable.from(Iterable<T>)` | Create an in-memory query over a source. |
| `ProviderQueryable.from(QueryProvider, Iterable<T>)` | Create a provider-backed query (builds expression tree). |
| `ProviderQueryable.from(QueryProvider, Iterable<T>, String alias)` | Same with a source alias (e.g. table name). |

**Example — in-memory:**

```java
var query = Queryable.from(List.of(1, 2, 3, 4, 5));
```

**Example — provider (expression tree):**

```java
var provider = new InMemoryQueryProvider();
var query = ProviderQueryable.from(provider, people, "people");
QueryExpression<?> expr = query.getExpression(); // inspect tree
```

---

### Filtering

#### `where(Predicate<T>)`

Keeps only elements that satisfy the predicate.

```java
Queryable.from(people)
    .where(p -> p.age() > 25)
    .toList();

Queryable.from(List.of(1, 2, 3, 4, 5))
    .where(n -> n % 2 == 0)
    .toList();  // [2, 4]
```

---

### Projection

#### `select(Function<T, R>)`

Maps each element to a new value.

```java
Queryable.from(people)
    .select(Person::name)
    .toList();  // ["Alice", "Bob", "Charlie", ...]

Queryable.from(people)
    .select(p -> p.name() + " (" + p.age() + ")")
    .toList();
```

#### `selectMany(Function<T, Iterable<R>>)`

Maps each element to a sequence and flattens the result (flatMap).

```java
var words = List.of("hello", "world");
Queryable.from(words)
    .selectMany(s -> List.of(s.split("")))
    .toList();  // ["h","e","l","l","o","w","o","r","l","d"]

Queryable.from(people)
    .selectMany(p -> List.of(p.name(), p.city()))
    .toList();
```

---

### Ordering

#### `orderBy(KeySelector<T, K>)` / `orderByDescending(KeySelector<T, K>)`

Sort by a comparable key. Return an `OrderedQueryable<T>`.

```java
Queryable.from(people)
    .orderBy(Person::name)
    .select(Person::name)
    .toList();  // alphabetical by name

Queryable.from(people)
    .orderByDescending(Person::age)
    .take(2)
    .select(Person::name)
    .toList();  // two oldest by age
```

#### `thenBy` / `thenByDescending` (on `OrderedQueryable`)

Secondary sort key (use after `orderBy` / `orderByDescending`).

```java
Queryable.from(people)
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
var byCity = Queryable.from(people)
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

Queryable.from(people)
    .join(orders, Person::name, Order::customer,
          (p, o) -> p.name() + " => $" + o.amount())
    .toList();
// ["Alice => $100.0", "Bob => $200.0"]
```

#### `leftJoin` (left outer join)

Like inner join, but every outer row appears once; if no match, inner is `null`.

```java
Queryable.from(people)
    .leftJoin(orders, Person::name, Order::customer,
              (p, o) -> p.name() + ": " + (o != null ? o.amount() : "no orders"))
    .toList();
// Every person listed; "no orders" when no matching order
```

#### `groupJoin`

For each outer element, groups all matching inner elements into a `Queryable<TInner>` and passes it to the result selector.

```java
Queryable.from(people)
    .groupJoin(orders, Person::name, Order::customer,
               (p, orderQuery) -> p.name() + " has " + orderQuery.toList().size() + " order(s)")
    .toList();
```

#### `crossJoin` (cartesian product)

Every pair (outer, inner); result selector builds each row.

```java
var a = List.of("X", "Y");
var b = List.of(1, 2);
Queryable.from(a)
    .crossJoin(b, (s, n) -> s + n)
    .toList();  // ["X1", "X2", "Y1", "Y2"]
```

---

### Partitioning and limiting

#### `take(int n)`

First `n` elements.

```java
Queryable.from(people).take(2).toList();
Queryable.from(List.of(1, 2, 3, 4, 5)).take(3).toList();  // [1, 2, 3]
```

#### `skip(int n)`

Skips the first `n` elements.

```java
Queryable.from(List.of(1, 2, 3, 4, 5)).skip(2).toList();  // [3, 4, 5]
Queryable.from(people).skip(1).take(2).toList();         // pagination
```

---

### Set operations

#### `distinct()`

Removes duplicates (order preserved by insertion).

```java
Queryable.from(List.of(1, 2, 2, 3, 3, 3)).distinct().toList();  // [1, 2, 3]
Queryable.from(people).select(Person::city).distinct().toList();  // unique cities
```

#### `union(Iterable<T>)`

Set union: all elements from both, duplicates removed.

```java
Queryable.from(List.of(1, 2, 3))
    .union(List.of(3, 4, 5))
    .toList();  // [1, 2, 3, 4, 5]
```

#### `intersect(Iterable<T>)`

Set intersection: elements that appear in both.

```java
Queryable.from(List.of(1, 2, 3))
    .intersect(List.of(2, 3, 4))
    .toList();  // [2, 3]
```

#### `except(Iterable<T>)`

Set difference: elements in the first sequence but not in the second.

```java
Queryable.from(List.of(1, 2, 3, 4))
    .except(List.of(2, 4))
    .toList();  // [1, 3]
```

#### `concat(Iterable<T>)`

Concatenates two sequences (keeps duplicates, order preserved).

```java
Queryable.from(List.of(1, 2))
    .concat(List.of(3, 4))
    .toList();  // [1, 2, 3, 4]
```

---

### Other operations

#### `chunk(int size)`

Splits the sequence into chunks of the given size. Returns `Queryable<List<T>>`.

```java
Queryable.from(List.of(1, 2, 3, 4, 5))
    .chunk(2)
    .toList();
// [[1, 2], [3, 4], [5]]
```

#### `reverse()`

Reverses the order of elements.

```java
Queryable.from(List.of(1, 2, 3)).reverse().toList();  // [3, 2, 1]
```

---

### Terminal operations

| Method | Description |
|--------|-------------|
| `toList()` | Materializes the query into a `List<T>`. |
| `first()` | Returns the first element; throws `NoSuchElementException` if empty. |
| `iterator()` | Returns an iterator (e.g. for `for (T x : query)`). |

```java
List<Person> list = Queryable.from(people).where(p -> p.age() > 30).toList();
Person first = Queryable.from(people).orderBy(Person::name).first();
for (Person p : Queryable.from(people).take(5)) {
    // ...
}
```

---

## Provider model and expression trees

When you use `ProviderQueryable`, each call (e.g. `where`, `select`, `take`) does **not** run the query; it adds a node to an **expression tree**. Execution happens only when you iterate or call a terminal method (`toList()`, `first()`, etc.), and the **QueryProvider** is responsible for running the tree.

### InMemoryQueryProvider

Runs the expression tree in memory by interpreting it (same semantics as `Queryable.from(...)`).

```java
import io.linjq.provider.InMemoryQueryProvider;
import io.linjq.provider.ProviderQueryable;

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

MIT License — see [LICENSE](LICENSE) or the project root.
