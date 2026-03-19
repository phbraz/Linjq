package net.linjq.core;

import net.linjq.exceptions.LinjqException;
import net.linjq.functional.BiFunction;
import net.linjq.functional.Function;
import net.linjq.functional.KeySelector;
import net.linjq.functional.Predicate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fluent, LINQ-style query API over an {@link Iterable}. Operations are executed in-memory.
 * Subclasses (e.g. {@link net.linjq.provider.ProviderQueryable}) can override to build expression trees instead.
 *
 * @param <T> the element type
 */
public class Queryable<T> implements Iterable<T> {

    private final Iterable<T> source;

    public Queryable(Iterable<T> source) {
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    public static <T> Queryable<T> from(Iterable<T> source) {
        return new Queryable<>(source);
    }

    @Override
    public Iterator<T> iterator() {
        return source.iterator();
    }

    protected <R> Queryable<R> fromIterable(Iterable<R> iterable) {
        return new Queryable<>(iterable);
    }

    // ─── Helper to wrap checked exceptions ────────────────────────────────

    private static RuntimeException propagate(Exception e) {
        if (e instanceof RuntimeException re) return re;
        return new LinjqException(e);
    }

    // ─── Terminal operations ─────────────────────────────────────────────

    public List<T> toList() {
        List<T> list = new ArrayList<>();
        for (T t : source) {
            list.add(t);
        }
        return list;
    }

    public T first() {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException("sequence is empty");
        }
        return it.next();
    }

    public Optional<T> firstOrDefault() {
        Iterator<T> it = source.iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }

    public T single() {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) throw new NoSuchElementException("sequence is empty");
        T value = it.next();
        if (it.hasNext()) throw new IllegalStateException("sequence contains more than one element");
        return value;
    }

    public Optional<T> singleOrDefault() {
        Iterator<T> it = source.iterator();
        if (!it.hasNext()) return Optional.empty();
        T value = it.next();
        if (it.hasNext()) throw new IllegalStateException("sequence contains more than one element");
        return Optional.of(value);
    }

    public int count() {
        int n = 0;
        for (T ignored : source) n++;
        return n;
    }

    public boolean any() {
        return source.iterator().hasNext();
    }

    public boolean any(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        try {
            for (T t : source) {
                if (predicate.test(t)) return true;
            }
            return false;
        } catch (Exception e) { throw propagate(e); }
    }

    public boolean contains(T element) {
        for (T t : source) {
            if (Objects.equals(t, element)) return true;
        }
        return false;
    }

    public boolean all(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        try {
            for (T t : source) {
                if (!predicate.test(t)) return false;
            }
            return true;
        } catch (Exception e) { throw propagate(e); }
    }

    public <R extends Comparable<R>> Optional<T> minBy(Function<T, R> selector) {
        Objects.requireNonNull(selector);
        try {
            Iterator<T> it = source.iterator();
            if (!it.hasNext()) return Optional.empty();
            T minElement = it.next();
            R minKey = selector.apply(minElement);
            while (it.hasNext()) {
                T current = it.next();
                R key = selector.apply(current);
                if (key.compareTo(minKey) < 0) {
                    minElement = current;
                    minKey = key;
                }
            }
            return Optional.of(minElement);
        } catch (Exception e) { throw propagate(e); }
    }

    public <R extends Comparable<R>> Optional<T> maxBy(Function<T, R> selector) {
        Objects.requireNonNull(selector);
        try {
            Iterator<T> it = source.iterator();
            if (!it.hasNext()) return Optional.empty();
            T maxElement = it.next();
            R maxKey = selector.apply(maxElement);
            while (it.hasNext()) {
                T current = it.next();
                R key = selector.apply(current);
                if (key.compareTo(maxKey) > 0) {
                    maxElement = current;
                    maxKey = key;
                }
            }
            return Optional.of(maxElement);
        } catch (Exception e) { throw propagate(e); }
    }

    public <R> R aggregate(R seed, BiFunction<R, T, R> accumulator) {
        Objects.requireNonNull(accumulator);
        try {
            R result = seed;
            for (T t : source) {
                result = accumulator.apply(result, t);
            }
            return result;
        } catch (Exception e) { throw propagate(e); }
    }

    // ─── Lookup ──────────────────────────────────────────────────────────

    public <K> Lookup<K, T> toLookup(Function<T, K> keySelector) {
        return Lookup.create(source, keySelector);
    }

    public <K, V> Lookup<K, V> toLookup(Function<T, K> keySelector, Function<T, V> elementSelector) {
        return Lookup.create(source, keySelector, elementSelector);
    }

    // ─── Filtering & projection ───────────────────────────────────────────

    public Queryable<T> where(Predicate<T> predicate) {
        Objects.requireNonNull(predicate);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                T next;
                boolean loaded;

                private void advance() {
                    try {
                        while (it.hasNext()) {
                            T t = it.next();
                            if (predicate.test(t)) {
                                next = t;
                                loaded = true;
                                return;
                            }
                        }
                        loaded = false;
                    } catch (Exception e) { throw propagate(e); }
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public T next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public <R> Queryable<R> select(Function<T, R> selector) {
        Objects.requireNonNull(selector);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }
                @Override
                public R next() {
                    try { return selector.apply(it.next()); }
                    catch (Exception e) { throw propagate(e); }
                }
            };
        });
    }

    public <R> Queryable<R> selectMany(Function<T, Iterable<R>> selector) {
        Objects.requireNonNull(selector);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                Iterator<R> inner;
                R next;
                boolean loaded;

                private void advance() {
                    try {
                        while (inner != null && inner.hasNext()) {
                            next = inner.next();
                            loaded = true;
                            return;
                        }
                        while (it.hasNext()) {
                            inner = selector.apply(it.next()).iterator();
                            if (inner.hasNext()) {
                                next = inner.next();
                                loaded = true;
                                return;
                            }
                        }
                        loaded = false;
                    } catch (Exception e) { throw propagate(e); }
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public R next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public <R> Queryable<R> selectManyIndexed(BiFunction<T, Integer, Iterable<R>> selector) {
        Objects.requireNonNull(selector);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                Iterator<R> inner;
                R next;
                boolean loaded;
                int index;

                private void advance() {
                    try {
                        while (inner != null && inner.hasNext()) {
                            next = inner.next();
                            loaded = true;
                            return;
                        }
                        while (it.hasNext()) {
                            inner = selector.apply(it.next(), index++).iterator();
                            if (inner.hasNext()) {
                                next = inner.next();
                                loaded = true;
                                return;
                            }
                        }
                        loaded = false;
                    } catch (Exception e) { throw propagate(e); }
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public R next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }
    
    // ─── Ordering ──────────────────────────────────────────────────────────

    public <K extends Comparable<K>> OrderedQueryable<T> orderBy(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        Comparator<T> cmp = (a, b) -> {
            try { return keySelector.select(a).compareTo(keySelector.select(b)); }
            catch (Exception e) { throw propagate(e); }
        };
        return new OrderedQueryable<>(this, cmp);
    }

    public <K extends Comparable<K>> OrderedQueryable<T> orderByDescending(KeySelector<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        Comparator<T> cmp = (a, b) -> {
            try { return keySelector.select(b).compareTo(keySelector.select(a)); }
            catch (Exception e) { throw propagate(e); }
        };
        return new OrderedQueryable<>(this, cmp);
    }

    // ─── Grouping ────────────────────────────────────────────────────────

    public <K> Queryable<Grouping<K, T>> groupBy(Function<T, K> keySelector) {
        Objects.requireNonNull(keySelector);
        try {
            List<T> list = toList();
            // Preserve insertion order of keys using LinkedHashMap
            var byKey = new java.util.LinkedHashMap<K, List<T>>();
            for (T item : list) {
                K key = keySelector.apply(item);
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
            List<Grouping<K, T>> groups = new ArrayList<>();
            for (var e : byKey.entrySet()) {
                groups.add(new Grouping<>(e.getKey(), e.getValue()));
            }
            return fromIterable(groups);
        } catch (Exception e) { throw propagate(e); }
    }

    // ─── Joins ────────────────────────────────────────────────────────────

    public <TInner, TKey, TResult> Queryable<TResult> join(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, TInner, TResult> resultSelector) {
        Objects.requireNonNull(outerKeySelector);
        Objects.requireNonNull(innerKeySelector);
        Objects.requireNonNull(resultSelector);
        List<TInner> innerList = new ArrayList<>();
        for (TInner x : inner) innerList.add(x);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                Iterator<TResult> current;
                T outer;
                int innerIndex;

                private void advance() {
                    try {
                        while (outer != null || it.hasNext()) {
                            if (outer == null) {
                                outer = it.next();
                                innerIndex = 0;
                            }
                            TKey ok = outerKeySelector.apply(outer);
                            for (int i = innerIndex; i < innerList.size(); i++) {
                                TInner in = innerList.get(i);
                                if (Objects.equals(ok, innerKeySelector.apply(in))) {
                                    innerIndex = i + 1;
                                    TResult one = resultSelector.apply(outer, in);
                                    current = java.util.Collections.singletonList(one).iterator();
                                    return;
                                }
                            }
                            outer = null;
                        }
                        current = null;
                    } catch (Exception e) { throw propagate(e); }
                }

                @Override
                public boolean hasNext() {
                    if (current != null && current.hasNext()) return true;
                    advance();
                    return current != null && current.hasNext();
                }

                @Override
                public TResult next() {
                    if (current == null || !current.hasNext()) advance();
                    if (current == null || !current.hasNext()) throw new NoSuchElementException();
                    return current.next();
                }
            };
        });
    }

    public <TInner, TKey, TResult> Queryable<TResult> leftJoin(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, TInner, TResult> resultSelector) {
        Objects.requireNonNull(outerKeySelector);
        Objects.requireNonNull(innerKeySelector);
        Objects.requireNonNull(resultSelector);
        List<TInner> innerList = new ArrayList<>();
        for (TInner x : inner) innerList.add(x);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                T outer;
                int innerIndex;
                TResult next;
                boolean hasNext;
                boolean hadMatchForCurrentOuter;

                private void advance() {
                    try {
                        hasNext = false;
                        if (outer == null && !it.hasNext()) return;
                        if (outer == null) {
                            outer = it.next();
                            innerIndex = 0;
                            hadMatchForCurrentOuter = false;
                        }
                        TKey ok = outerKeySelector.apply(outer);
                        for (int i = innerIndex; i < innerList.size(); i++) {
                            TInner in = innerList.get(i);
                            if (Objects.equals(ok, innerKeySelector.apply(in))) {
                                hadMatchForCurrentOuter = true;
                                next = resultSelector.apply(outer, in);
                                innerIndex = i + 1;
                                hasNext = true;
                                return;
                            }
                        }
                        if (!hadMatchForCurrentOuter) {
                            next = resultSelector.apply(outer, null);
                            hasNext = true;
                        }
                        outer = null;
                        if (!hasNext) advance();
                    } catch (Exception e) { throw propagate(e); }
                }

                @Override
                public boolean hasNext() {
                    if (hasNext) return true;
                    advance();
                    return hasNext;
                }

                @Override
                public TResult next() {
                    if (!hasNext) advance();
                    if (!hasNext) throw new NoSuchElementException();
                    hasNext = false;
                    return next;
                }
            };
        });
    }

    public <TInner, TKey, TResult> Queryable<TResult> groupJoin(
            Iterable<TInner> inner,
            Function<T, TKey> outerKeySelector,
            Function<TInner, TKey> innerKeySelector,
            BiFunction<T, Queryable<TInner>, TResult> resultSelector) {
        Objects.requireNonNull(outerKeySelector);
        Objects.requireNonNull(innerKeySelector);
        Objects.requireNonNull(resultSelector);
        List<TInner> innerList = new ArrayList<>();
        for (TInner x : inner) innerList.add(x);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }
                @Override
                public TResult next() {
                    try {
                        T o = it.next();
                        TKey ok = outerKeySelector.apply(o);
                        List<TInner> matches = new ArrayList<>();
                        for (TInner in : innerList) {
                            if (Objects.equals(ok, innerKeySelector.apply(in))) matches.add(in);
                        }
                        return resultSelector.apply(o, fromIterable(matches));
                    } catch (Exception e) { throw propagate(e); }
                }
            };
        });
    }

    public <TInner, TResult> Queryable<TResult> crossJoin(
            Iterable<TInner> inner,
            BiFunction<T, TInner, TResult> resultSelector) {
        Objects.requireNonNull(resultSelector);
        List<TInner> innerList = new ArrayList<>();
        for (TInner x : inner) innerList.add(x);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                T outer = null;
                int j = 0;

                private void ensureOuter() {
                    if (outer == null && it.hasNext()) {
                        outer = it.next();
                        j = 0;
                    }
                }

                @Override
                public boolean hasNext() {
                    ensureOuter();
                    return outer != null && j < innerList.size();
                }

                @Override
                public TResult next() {
                    try {
                        ensureOuter();
                        if (outer == null || j >= innerList.size()) throw new NoSuchElementException();
                        TResult r = resultSelector.apply(outer, innerList.get(j));
                        j++;
                        if (j >= innerList.size()) { outer = null; j = 0; }
                        return r;
                    } catch (Exception e) { throw propagate(e); }
                }
            };
        });
    }

    // ─── Partitioning & set operations ────────────────────────────────────

    public Queryable<T> take(int count) {
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                int n = count;

                @Override
                public boolean hasNext() { return n > 0 && it.hasNext(); }
                @Override
                public T next() {
                    if (n <= 0) throw new NoSuchElementException();
                    n--;
                    return it.next();
                }
            };
        });
    }

    public Queryable<T> skip(int count) {
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            int n = count;
            while (n > 0 && it.hasNext()) { it.next(); n--; }
            return it;
        });
    }

    public Queryable<T> distinct() {
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            LinkedHashSet<T> seen = new LinkedHashSet<>();
            return new Iterator<>() {
                T next;
                boolean loaded;

                private void advance() {
                    while (it.hasNext()) {
                        T t = it.next();
                        if (seen.add(t)) {
                            next = t;
                            loaded = true;
                            return;
                        }
                    }
                    loaded = false;
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public T next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public Queryable<T> union(Iterable<T> other) {
        // Lazy: stream through source then other, skipping already-seen
        return fromIterable(() -> {
            Iterator<T> first = source.iterator();
            Iterator<T> second = other.iterator();
            LinkedHashSet<T> seen = new LinkedHashSet<>();
            return new Iterator<>() {
                T next;
                boolean loaded;

                private void advance() {
                    while (first.hasNext()) {
                        T t = first.next();
                        if (seen.add(t)) { next = t; loaded = true; return; }
                    }
                    while (second.hasNext()) {
                        T t = second.next();
                        if (seen.add(t)) { next = t; loaded = true; return; }
                    }
                    loaded = false;
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public T next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public Queryable<T> intersect(Iterable<T> other) {
        LinkedHashSet<T> otherSet = new LinkedHashSet<>();
        for (T t : other) otherSet.add(t);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                T next;
                boolean loaded;

                private void advance() {
                    while (it.hasNext()) {
                        T t = it.next();
                        if (otherSet.contains(t)) { next = t; loaded = true; return; }
                    }
                    loaded = false;
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public T next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public Queryable<T> except(Iterable<T> other) {
        LinkedHashSet<T> otherSet = new LinkedHashSet<>();
        for (T t : other) otherSet.add(t);
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                T next;
                boolean loaded;

                private void advance() {
                    while (it.hasNext()) {
                        T t = it.next();
                        if (!otherSet.contains(t)) { next = t; loaded = true; return; }
                    }
                    loaded = false;
                }

                @Override
                public boolean hasNext() {
                    if (loaded) return true;
                    advance();
                    return loaded;
                }

                @Override
                public T next() {
                    if (!loaded) advance();
                    if (!loaded) throw new NoSuchElementException();
                    loaded = false;
                    return next;
                }
            };
        });
    }

    public Queryable<T> concat(Iterable<T> other) {
        return fromIterable(() -> {
            Iterator<T> a = source.iterator();
            Iterator<T> b = other.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return a.hasNext() || b.hasNext(); }
                @Override
                public T next() { return a.hasNext() ? a.next() : b.next(); }
            };
        });
    }

    public Queryable<List<T>> chunk(int size) {
        if (size <= 0) throw new IllegalArgumentException("chunk size must be positive");
        return fromIterable(() -> {
            Iterator<T> it = source.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }
                @Override
                public List<T> next() {
                    List<T> chunk = new ArrayList<>();
                    for (int i = 0; i < size && it.hasNext(); i++) chunk.add(it.next());
                    return chunk;
                }
            };
        });
    }

    public Queryable<T> reverse() {
        List<T> list = toList();
        List<T> reversed = new ArrayList<>(list);
        java.util.Collections.reverse(reversed);
        return fromIterable(reversed);
    }
}