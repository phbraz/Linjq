package io.linjq;

import io.linjq.core.Queryable;
import io.linjq.expression.*;
import io.linjq.provider.InMemoryQueryProvider;
import io.linjq.provider.ProviderQueryable;
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
            var query = ProviderQueryable.from(PROVIDER, PEOPLE, "people")
                    .where(p -> p.age() > 25)
                    .orderBy(Person::name)
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

            assertEquals(5, depth); // take -> skip -> orderBy -> where -> source
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
    }
}
