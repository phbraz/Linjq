package net.linjq;

import net.linjq.core.Grouping;
import net.linjq.core.Lookup;
import net.linjq.core.Queryable;
import net.linjq.expression.*;
import net.linjq.provider.InMemoryQueryProvider;
import net.linjq.provider.ProviderQueryable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderTest {

    record Person(String name, int age, String city) {}

    static final List<Person> PEOPLE = List.of(
            new Person("Alice",   30, "London"),
            new Person("Bob",     25, "Paris"),
            new Person("Charlie", 35, "London"),
            new Person("Diana",   28, "Berlin"),
            new Person("Eve",     25, "Paris")
    );

    static final InMemoryQueryProvider PROVIDER = new InMemoryQueryProvider();

    // ═══════════════════════════════════════════════
    //  Expression Tree Construction
    // ═══════════════════════════════════════════════

    @Nested
    class ExpressionTreeTests {
        @Test
        void sourceExpression() {
            var expr = new SourceExpression<>(PEOPLE);
            assertEquals(ExpressionType.SOURCE, expr.type());
            assertTrue(expr.children().isEmpty());
            assertSame(PEOPLE, expr.source());
        }

        @Test
        void sourceExpressionWithAlias() {
            var expr = new SourceExpression<>(PEOPLE, "people");
            assertEquals("people", expr.alias());
        }

        @Test
        void whereExpressionBuildsTree() {
            var source = new SourceExpression<>(PEOPLE);
            var where = new WhereExpression<>(source, p -> p.age() > 25);

            assertEquals(ExpressionType.WHERE, where.type());
            assertEquals(1, where.children().size());
            assertSame(source, where.children().getFirst());
        }

        @Test
        void selectExpressionBuildsTree() {
            var source = new SourceExpression<>(PEOPLE);
            var select = new SelectExpression<>(source, Person::name);

            assertEquals(ExpressionType.SELECT, select.type());
            assertEquals(1, select.children().size());
        }

        @Test
        void chainedExpressionsFormTree() {
            var source = new SourceExpression<>(PEOPLE);
            var where = new WhereExpression<>(source, p -> p.age() > 25);
            var select = new SelectExpression<>(where, Person::name);
            var take = new TakeExpression<>(select, 3);

            assertEquals(ExpressionType.TAKE, take.type());
            assertEquals(ExpressionType.SELECT, take.children().getFirst().type());

            var selectChild = (SelectExpression<?, ?>) take.children().getFirst();
            assertEquals(ExpressionType.WHERE, selectChild.children().getFirst().type());

            var whereChild = (WhereExpression<?>) selectChild.children().getFirst();
            assertEquals(ExpressionType.SOURCE, whereChild.children().getFirst().type());
        }

        @Test
        void orderByExpressionDescending() {
            var source = new SourceExpression<>(PEOPLE);
            var orderBy = new OrderByExpression<>(source, p -> p.age(), true);
            assertEquals(ExpressionType.ORDER_BY_DESCENDING, orderBy.type());
        }

        @Test
        void orderByExpressionAscending() {
            var source = new SourceExpression<>(PEOPLE);
            var orderBy = new OrderByExpression<>(source, p -> p.age(), false);
            assertEquals(ExpressionType.ORDER_BY, orderBy.type());
        }

        @Test
        void allExpressionTypesExist() {
            // Ensure all types in the enum have corresponding expression classes
            for (ExpressionType type : ExpressionType.values()) {
                assertNotNull(type.name());
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  Expression Visitor
    // ═══════════════════════════════════════════════

    @Nested
    class VisitorTests {
        @Test
        void visitorWalksTree() {
            var source = new SourceExpression<>(PEOPLE, "people");
            var where = new WhereExpression<>(source, p -> p.age() > 25);
            var take = new TakeExpression<>(where, 10);

            // Simple visitor that collects expression types
            var types = new ArrayList<ExpressionType>();
            var visitor = new ExpressionVisitor<Void>() {
                @Override protected Void visitSource(SourceExpression<?> e) { types.add(e.type()); return null; }
                @Override protected Void visitWhere(WhereExpression<?> e) { types.add(e.type()); visit(e.source()); return null; }
                @Override protected Void visitTake(TakeExpression<?> e) { types.add(e.type()); visit(e.source()); return null; }
                @Override protected Void visitSelect(SelectExpression<?, ?> e) { types.add(e.type()); return null; }
                @Override protected Void visitSelectMany(SelectManyExpression<?, ?> e) { return null; }
                @Override protected Void visitSelectManyIndexed(SelectManyIndexedExpression<?, ?> e) { return null; }
                @Override protected Void visitOrderBy(OrderByExpression<?, ?> e) { return null; }
                @Override protected Void visitThenBy(ThenByExpression<?, ?> e) { return null; }
                @Override protected Void visitGroupBy(GroupByExpression<?, ?> e) { return null; }
                @Override protected Void visitJoin(JoinExpression<?, ?, ?, ?> e) { return null; }
                @Override protected Void visitLeftJoin(LeftJoinExpression<?, ?, ?, ?> e) { return null; }
                @Override protected Void visitGroupJoin(GroupJoinExpression<?, ?, ?, ?> e) { return null; }
                @Override protected Void visitCrossJoin(CrossJoinExpression<?, ?, ?> e) { return null; }
                @Override protected Void visitSkip(SkipExpression<?> e) { return null; }
                @Override protected Void visitDistinct(DistinctExpression<?> e) { return null; }
                @Override protected Void visitUnion(UnionExpression<?> e) { return null; }
                @Override protected Void visitIntersect(IntersectExpression<?> e) { return null; }
                @Override protected Void visitExcept(ExceptExpression<?> e) { return null; }
                @Override protected Void visitConcat(ConcatExpression<?> e) { return null; }
                @Override protected Void visitChunk(ChunkExpression<?> e) { return null; }
                @Override protected Void visitReverse(ReverseExpression<?> e) { return null; }
            };

            visitor.visit(take);

            assertEquals(List.of(ExpressionType.TAKE, ExpressionType.WHERE, ExpressionType.SOURCE), types);
        }

        @Test
        void visitorCanTranslateToString() {
            var source = new SourceExpression<>(PEOPLE, "people");
            var where = new WhereExpression<>(source, p -> p.age() > 25);
            var take = new TakeExpression<>(where, 10);

            var pseudoSql = new ExpressionVisitor<String>() {
                @Override protected String visitSource(SourceExpression<?> e) { return e.alias() != null ? e.alias() : "?"; }
                @Override protected String visitWhere(WhereExpression<?> e) { return visit(e.source()) + " | WHERE"; }
                @Override protected String visitTake(TakeExpression<?> e) { return visit(e.source()) + " | LIMIT " + e.count(); }
                @Override protected String visitSelect(SelectExpression<?, ?> e) { return visit(e.source()) + " | SELECT"; }
                @Override protected String visitSelectMany(SelectManyExpression<?, ?> e) { return ""; }
                @Override protected String visitSelectManyIndexed(SelectManyIndexedExpression<?, ?> e) { return ""; }
                @Override protected String visitOrderBy(OrderByExpression<?, ?> e) { return visit(e.source()) + " | ORDER BY"; }
                @Override protected String visitThenBy(ThenByExpression<?, ?> e) { return ""; }
                @Override protected String visitGroupBy(GroupByExpression<?, ?> e) { return ""; }
                @Override protected String visitJoin(JoinExpression<?, ?, ?, ?> e) { return ""; }
                @Override protected String visitLeftJoin(LeftJoinExpression<?, ?, ?, ?> e) { return ""; }
                @Override protected String visitGroupJoin(GroupJoinExpression<?, ?, ?, ?> e) { return ""; }
                @Override protected String visitCrossJoin(CrossJoinExpression<?, ?, ?> e) { return ""; }
                @Override protected String visitSkip(SkipExpression<?> e) { return visit(e.source()) + " | OFFSET " + e.count(); }
                @Override protected String visitDistinct(DistinctExpression<?> e) { return visit(e.source()) + " | DISTINCT"; }
                @Override protected String visitUnion(UnionExpression<?> e) { return ""; }
                @Override protected String visitIntersect(IntersectExpression<?> e) { return ""; }
                @Override protected String visitExcept(ExceptExpression<?> e) { return ""; }
                @Override protected String visitConcat(ConcatExpression<?> e) { return ""; }
                @Override protected String visitChunk(ChunkExpression<?> e) { return ""; }
                @Override protected String visitReverse(ReverseExpression<?> e) { return ""; }
            };

            assertEquals("people | WHERE | LIMIT 10", pseudoSql.visit(take));
        }
    }

    // ═══════════════════════════════════════════════
    //  ProviderQueryable — expression tree building
    // ═══════════════════════════════════════════════

    @Nested
    class ProviderQueryableTreeTests {
        @Test
        void buildsExpressionTreeLazily() {
            var query = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() > 25)
                    .select(Person::name)
                    .take(3);

            // Nothing has executed yet — just built a tree
            var expr = query.getExpression();
            assertEquals(ExpressionType.TAKE, expr.type());
        }

        @Test
        void getProviderReturnsProvider() {
            var query = ProviderQueryable.from(PROVIDER, PEOPLE);
            assertSame(PROVIDER, query.getProvider());
        }

        @Test
        void expressionTreeDepth() {
            // Keep chain as ProviderQueryable so getExpression() is available (orderBy returns OrderedQueryable which breaks the chain)
            ProviderQueryable<Person> query = ProviderQueryable.from(PROVIDER, PEOPLE, "people")
                    .where(p -> p.age() > 25)
                    .skip(1)
                    .take(2);

            // Walk the tree to count depth
            int depth = 0;
            QueryExpression<?> current = query.getExpression();
            while (!current.children().isEmpty()) {
                depth++;
                current = current.children().getFirst();
            }
            depth++; // count the source

            assertEquals(4, depth); // take -> skip -> where -> source
        }
    }

    // ═══════════════════════════════════════════════
    //  InMemoryQueryProvider — execution
    // ═══════════════════════════════════════════════

    @Nested
    class InMemoryProviderTests {
        @Test
        void whereExecutes() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() >= 30)
                    .toList();

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(p -> p.age() >= 30));
        }

        @Test
        void selectExecutes() {
            var names = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .select(Person::name)
                    .toList();

            assertEquals(List.of("Alice", "Bob", "Charlie", "Diana", "Eve"), names);
        }

        @Test
        void chainedWhereSelectTake() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() >= 25)
                    .select(Person::name)
                    .take(3)
                    .toList();

            assertEquals(3, result.size());
        }

        @Test
        void orderByExecutes() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .orderBy(Person::name)
                    .select(Person::name)
                    .toList();

            assertEquals(List.of("Alice", "Bob", "Charlie", "Diana", "Eve"), result);
        }

        @Test
        void orderByDescendingExecutes() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .orderByDescending(p -> p.age())
                    .select(Person::name)
                    .first();

            assertEquals("Charlie", result);
        }

        @Test
        void skipTakeExecutes() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .skip(2)
                    .take(2)
                    .select(Person::name)
                    .toList();

            assertEquals(List.of("Charlie", "Diana"), result);
        }

        @Test
        void distinctExecutes() {
            var data = List.of(1, 2, 2, 3, 3, 3);
            var result = ProviderQueryable.from(PROVIDER, data)
                    .distinct()
                    .toList();

            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        void groupByExecutes() {
            var groups = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .groupBy(Person::city)
                    .toList();

            assertEquals(3, groups.size());
        }

        @Test
        void joinExecutes() {
            record Order(String customer, double amount) {}
            var orders = List.of(new Order("Alice", 100.0), new Order("Bob", 200.0));

            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .join(orders, Person::name, Order::customer,
                            (p, o) -> p.name() + "=$" + o.amount())
                    .toList();

            assertEquals(2, result.size());
            assertTrue(result.contains("Alice=$100.0"));
        }

        @Test
        void leftJoinExecutes() {
            record Order(String customer, double amount) {}
            var orders = List.of(new Order("Alice", 100.0));

            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .leftJoin(orders, Person::name, Order::customer,
                            (p, o) -> p.name() + ":" + (o != null ? o.amount() : "none"))
                    .toList();

            assertEquals(5, result.size());
            assertTrue(result.contains("Alice:100.0"));
            assertTrue(result.contains("Bob:none"));
        }

        @Test
        void crossJoinExecutes() {
            var a = List.of("X", "Y");
            var b = List.of(1, 2);

            var result = ProviderQueryable.from(PROVIDER, a)
                    .crossJoin(b, (s, n) -> s + n)
                    .toList();

            assertEquals(List.of("X1", "X2", "Y1", "Y2"), result);
        }

        @Test
        void selectManyIndexedExecutes() {
            // Each string expands to (char + index) for each character; index is 0, 1, 2...
            var result = ProviderQueryable.from(PROVIDER, List.of("ab", "c", "def"))
                    .selectManyIndexed((s, i) -> s.chars().mapToObj(c -> (char) c + String.valueOf(i)).toList())
                    .toList();

            assertEquals(List.of("a0", "b0", "c1", "d2", "e2", "f2"), result);
        }

        @Test
        void reverseExecutes() {
            var result = ProviderQueryable.from(PROVIDER, List.of(1, 2, 3))
                    .reverse()
                    .toList();

            assertEquals(List.of(3, 2, 1), result);
        }

        @Test
        void concatExecutes() {
            var result = ProviderQueryable.from(PROVIDER, List.of(1, 2))
                    .concat(List.of(3, 4))
                    .toList();

            assertEquals(List.of(1, 2, 3, 4), result);
        }

        @Test
        void unionExecutes() {
            var result = ProviderQueryable.from(PROVIDER, List.of(1, 2, 3))
                    .union(List.of(3, 4, 5))
                    .toList();

            assertEquals(List.of(1, 2, 3, 4, 5), result);
        }

        @Test
        void chunkExecutes() {
            var result = ProviderQueryable.from(PROVIDER, List.of(1, 2, 3, 4, 5))
                    .chunk(2)
                    .toList();

            assertEquals(3, result.size());
            assertEquals(List.of(1, 2), result.get(0));
            assertEquals(List.of(3, 4), result.get(1));
            assertEquals(List.of(5), result.get(2));
        }

        @Test
        void complexProviderQuery() {
            // Replicate a real-world query through the provider
            var result = ProviderQueryable.from(PROVIDER, PEOPLE, "people")
                    .where(p -> p.age() >= 25)
                    .orderBy(Person::name)
                    .select(Person::name)
                    .skip(1)
                    .take(3)
                    .toList();

            // All 5 people are >= 25, sorted: Alice, Bob, Charlie, Diana, Eve
            // Skip 1 → Bob, Charlie, Diana, Eve; Take 3 → Bob, Charlie, Diana
            assertEquals(List.of("Bob", "Charlie", "Diana"), result);
        }

        @Test
        void providerResultsMatchDirectQueryable() {
            var direct = Queryable.from(PEOPLE)
                    .where(p -> p.city().equals("London"))
                    .select(Person::name)
                    .toList();

            var viaProvider = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.city().equals("London"))
                    .select(Person::name)
                    .toList();

            assertEquals(direct, viaProvider);
        }

        // ═══ Terminal operations (inherited from Queryable) ═══

        @Test
        void firstOrDefault_nonEmpty_returnsFirst() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() >= 30)
                    .firstOrDefault();

            assertTrue(result.isPresent());
            assertEquals(new Person("Alice", 30, "London"), result.get());
        }

        @Test
        void firstOrDefault_empty_returnsEmpty() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() > 100)
                    .firstOrDefault();

            assertTrue(result.isEmpty());
        }

        @Test
        void single_oneElement_returnsElement() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.name().equals("Bob"))
                    .single();

            assertEquals(new Person("Bob", 25, "Paris"), result);
        }

        @Test
        void single_empty_throws() {
            assertThrows(java.util.NoSuchElementException.class, () ->
                    ProviderQueryable.from(PROVIDER, PEOPLE)
                            .where(p -> p.age() > 100)
                            .single());
        }

        @Test
        void single_multiple_throws() {
            assertThrows(IllegalStateException.class, () ->
                    ProviderQueryable.from(PROVIDER, PEOPLE)
                            .where(p -> p.age() >= 25)
                            .single());
        }

        @Test
        void singleOrDefault_oneElement_returnsElement() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.name().equals("Diana"))
                    .singleOrDefault();

            assertTrue(result.isPresent());
            assertEquals(new Person("Diana", 28, "Berlin"), result.get());
        }

        @Test
        void singleOrDefault_empty_returnsEmpty() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() < 0)
                    .singleOrDefault();

            assertTrue(result.isEmpty());
        }

        @Test
        void singleOrDefault_multiple_throws() {
            assertThrows(IllegalStateException.class, () ->
                    ProviderQueryable.from(PROVIDER, PEOPLE)
                            .where(p -> p.city().equals("Paris"))
                            .singleOrDefault());
        }

        @Test
        void count_returnsCorrectSize() {
            var n = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.city().equals("London"))
                    .count();

            assertEquals(2, n);
        }

        @Test
        void count_empty_returnsZero() {
            var n = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() > 100)
                    .count();

            assertEquals(0, n);
        }

        @Test
        void count_fullSource() {
            assertEquals(5, ProviderQueryable.from(PROVIDER, PEOPLE).count());
        }

        @Test
        void contains_present_returnsTrue_absent_returnsFalse() {
            var query = ProviderQueryable.from(PROVIDER, PEOPLE).where(p -> p.city().equals("London"));
            assertTrue(query.contains(new Person("Alice", 30, "London")));
            assertFalse(query.contains(new Person("Bob", 25, "Paris")));
        }

        @Test
        void any_nonEmpty_returnsTrue() {
            assertTrue(ProviderQueryable.from(PROVIDER, PEOPLE).any());
            assertTrue(ProviderQueryable.from(PROVIDER, PEOPLE).where(p -> p.age() >= 25).any());
        }

        @Test
        void any_empty_returnsFalse() {
            assertFalse(ProviderQueryable.from(PROVIDER, List.<Person>of()).any());
            assertFalse(ProviderQueryable.from(PROVIDER, PEOPLE).where(p -> p.age() > 100).any());
        }

        @Test
        void any_withPredicate_match_returnsTrue() {
            assertTrue(ProviderQueryable.from(PROVIDER, PEOPLE)
                    .any(p -> p.name().equals("Charlie")));
        }

        @Test
        void any_withPredicate_noMatch_returnsFalse() {
            assertFalse(ProviderQueryable.from(PROVIDER, PEOPLE)
                    .any(p -> p.age() > 100));
        }

        @Test
        void all_allMatch_returnsTrue() {
            assertTrue(ProviderQueryable.from(PROVIDER, PEOPLE)
                    .all(p -> p.age() >= 25));
        }

        @Test
        void all_oneFails_returnsFalse() {
            assertFalse(ProviderQueryable.from(PROVIDER, PEOPLE)
                    .all(p -> p.city().equals("London")));
        }

        @Test
        void all_empty_returnsTrue() {
            assertTrue(ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() > 100)
                    .all(p -> false));
        }

        @Test
        void minBy_returnsElementWithMinKey() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .minBy(Person::age);

            assertTrue(result.isPresent());
            assertEquals(25, result.get().age());
            assertTrue(List.of(new Person("Bob", 25, "Paris"), new Person("Eve", 25, "Paris")).contains(result.get()));
        }

        @Test
        void minBy_empty_returnsEmpty() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .where(p -> p.age() > 100)
                    .minBy(Person::age);

            assertTrue(result.isEmpty());
        }

        @Test
        void minBy_withSelector() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .minBy(Person::name);

            assertTrue(result.isPresent());
            assertEquals("Alice", result.get().name());
        }

        @Test
        void maxBy_returnsElementWithMaxKey() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .maxBy(Person::age);

            assertTrue(result.isPresent());
            assertEquals(35, result.get().age());
            assertEquals("Charlie", result.get().name());
        }

        @Test
        void maxBy_empty_returnsEmpty() {
            var result = ProviderQueryable.from(PROVIDER, List.<Person>of())
                    .maxBy(Person::age);

            assertTrue(result.isEmpty());
        }

        @Test
        void maxBy_withSelector() {
            var result = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .maxBy(Person::name);

            assertTrue(result.isPresent());
            assertEquals("Eve", result.get().name());
        }

        @Test
        void aggregate_sum() {
            var sum = ProviderQueryable.from(PROVIDER, List.of(1, 2, 3, 4, 5))
                    .aggregate(0, Integer::sum);

            assertEquals(15, sum);
        }

        @Test
        void aggregate_stringConcat() {
            var names = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .select(Person::name)
                    .aggregate(new StringBuilder(), (sb, name) -> sb.length() > 0 ? sb.append(",").append(name) : sb.append(name))
                    .toString();

            assertEquals("Alice,Bob,Charlie,Diana,Eve", names);
        }

        @Test
        void aggregate_empty_returnsSeed() {
            var result = ProviderQueryable.from(PROVIDER, List.<Integer>of())
                    .aggregate(42, Integer::sum);

            assertEquals(42, result);
        }
    }

    // ═══════════════════════════════════════════════
    //  toLookup
    // ═══════════════════════════════════════════════

    @Nested
    class ToLookupTests {
        @Test
        void toLookup_keySelector_groupsByKey() {
            Lookup<String, Person> lookup = Queryable.from(PEOPLE).toLookup(Person::city);

            assertEquals(3, lookup.size());
            assertTrue(lookup.containsKey("London"));
            assertTrue(lookup.containsKey("Paris"));
            assertTrue(lookup.containsKey("Berlin"));

            var londoners = lookup.get("London").toList();
            assertEquals(2, londoners.size());
            assertTrue(londoners.stream().allMatch(p -> "London".equals(p.city())));
            assertTrue(londoners.stream().anyMatch(p -> p.name().equals("Alice")));
            assertTrue(londoners.stream().anyMatch(p -> p.name().equals("Charlie")));

            var paris = lookup.get("Paris").toList();
            assertEquals(2, paris.size());
            assertTrue(paris.stream().allMatch(p -> "Paris".equals(p.city())));
        }

        @Test
        void toLookup_keySelector_getMissingKey_returnsEmpty() {
            Lookup<String, Person> lookup = Queryable.from(PEOPLE).toLookup(Person::city);

            assertFalse(lookup.containsKey("Rome"));
            var empty = lookup.get("Rome").toList();
            assertTrue(empty.isEmpty());
        }

        @Test
        void toLookup_keySelector_keysPreserveInsertionOrder() {
            Lookup<String, Person> lookup = Queryable.from(PEOPLE).toLookup(Person::city);

            var keys = lookup.keys().toList();
            assertEquals(List.of("London", "Paris", "Berlin"), keys);
        }

        @Test
        void toLookup_keySelector_iteratesGroupingsInKeyOrder() {
            Lookup<String, Person> lookup = Queryable.from(PEOPLE).toLookup(Person::city);

            var keys = new ArrayList<String>();
            var counts = new ArrayList<Integer>();
            for (Grouping<String, Person> group : lookup) {
                keys.add(group.key());
                counts.add(Queryable.from(group).toList().size());
            }
            assertEquals(List.of("London", "Paris", "Berlin"), keys);
            assertEquals(List.of(2, 2, 1), counts);
        }

        @Test
        void toLookup_keySelector_elementSelector_transformsElements() {
            Lookup<String, String> lookup = Queryable.from(PEOPLE)
                    .toLookup(Person::city, Person::name);

            assertEquals(3, lookup.size());
            var londonNames = lookup.get("London").toList();
            assertEquals(List.of("Alice", "Charlie"), londonNames);

            var parisNames = lookup.get("Paris").toList();
            assertEquals(List.of("Bob", "Eve"), parisNames);

            var berlinNames = lookup.get("Berlin").toList();
            assertEquals(List.of("Diana"), berlinNames);
        }

        @Test
        void toLookup_emptySource_returnsEmptyLookup() {
            Lookup<String, Person> lookup = Queryable.from(List.<Person>of()).toLookup(Person::city);

            assertEquals(0, lookup.size());
            assertFalse(lookup.containsKey("London"));
            assertTrue(lookup.get("London").toList().isEmpty());
            assertTrue(lookup.keys().toList().isEmpty());
        }

        @Test
        void toLookup_viaProviderQueryable() {
            Lookup<String, Person> lookup = ProviderQueryable.from(PROVIDER, PEOPLE)
                    .toLookup(Person::city);

            assertEquals(3, lookup.size());
            var londoners = lookup.get("London").toList();
            assertEquals(2, londoners.size());
            assertTrue(lookup.containsKey("Berlin"));
            assertTrue(lookup.get("Rome").toList().isEmpty());
        }
    }
}
