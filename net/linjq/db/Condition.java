package net.linjq.db;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A fluent, type-safe condition for building SQL WHERE clauses.
 * Use with static imports for natural-reading queries.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * import static net.linjq.db.Condition.*;
 *
 * people.where(Person::age, greaterThan(25))
 * people.where(Person::name, like("A%"))
 * people.where(Person::city, eq("London").or(eq("Paris")))
 * people.where(Person::age, between(18, 65))
 * people.where(Person::email, isNull())
 * people.where(Person::name, in("Alice", "Bob", "Charlie"))
 * }</pre>
 *
 * @param <T> the value type this condition operates on
 */
public sealed interface Condition<T> {

    // ─── Leaf conditions ──────────────────────────────────────────────────

    record Eq<T>(T value) implements Condition<T> {}
    record Neq<T>(T value) implements Condition<T> {}
    record Gt<T>(T value) implements Condition<T> {}
    record Gte<T>(T value) implements Condition<T> {}
    record Lt<T>(T value) implements Condition<T> {}
    record Lte<T>(T value) implements Condition<T> {}
    record Like(String pattern) implements Condition<String> {}
    record NotLike(String pattern) implements Condition<String> {}
    record Between<T>(T low, T high) implements Condition<T> {
        public Between {
            Objects.requireNonNull(low, "low must not be null");
            Objects.requireNonNull(high, "high must not be null");
        }
    }
    record In<T>(List<T> values) implements Condition<T> {
        public In {
            Objects.requireNonNull(values, "values must not be null");
            values = Collections.unmodifiableList(values);
        }
    }
    record IsNull<T>() implements Condition<T> {}
    record IsNotNull<T>() implements Condition<T> {}

    // ─── Composite conditions ─────────────────────────────────────────────

    record Or<T>(Condition<T> left, Condition<T> right) implements Condition<T> {
        public Or {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }
    }

    record And<T>(Condition<T> left, Condition<T> right) implements Condition<T> {
        public And {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }
    }

    record Not<T>(Condition<T> inner) implements Condition<T> {
        public Not {
            Objects.requireNonNull(inner, "inner must not be null");
        }
    }

    // ─── Combinators ──────────────────────────────────────────────────────

    default Condition<T> or(Condition<T> other) {
        return new Or<>(this, other);
    }

    default Condition<T> and(Condition<T> other) {
        return new And<>(this, other);
    }

    default Condition<T> not() {
        return new Not<>(this);
    }

    // ─── Static factories (import these) ──────────────────────────────────

    static <T> Condition<T> eq(T value) { return new Eq<>(value); }
    static <T> Condition<T> neq(T value) { return new Neq<>(value); }
    static <T> Condition<T> greaterThan(T value) { return new Gt<>(value); }
    static <T> Condition<T> greaterThanOrEqual(T value) { return new Gte<>(value); }
    static <T> Condition<T> lessThan(T value) { return new Lt<>(value); }
    static <T> Condition<T> lessThanOrEqual(T value) { return new Lte<>(value); }
    static Condition<String> like(String pattern) { return new Like(pattern); }
    static Condition<String> notLike(String pattern) { return new NotLike(pattern); }
    static <T> Condition<T> between(T low, T high) { return new Between<>(low, high); }
    @SafeVarargs
    static <T> Condition<T> in(T... values) { return new In<>(List.of(values)); }
    static <T> Condition<T> in(List<T> values) { return new In<>(values); }
    static <T> Condition<T> isNull() { return new IsNull<>(); }
    static <T> Condition<T> isNotNull() { return new IsNotNull<>(); }
}