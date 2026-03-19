package net.linjq.expression;

/**
 * Identifies the kind of each expression node in a query expression tree.
 * Providers can switch on this to translate or optimize query operations.
 */
public enum ExpressionType {
    SOURCE,
    WHERE,
    SELECT,
    SELECT_MANY,
    SELECT_MANY_INDEXED,
    ORDER_BY,
    ORDER_BY_DESCENDING,
    THEN_BY,
    THEN_BY_DESCENDING,
    GROUP_BY,
    JOIN,
    LEFT_JOIN,
    GROUP_JOIN,
    CROSS_JOIN,
    TAKE,
    SKIP,
    DISTINCT,
    UNION,
    INTERSECT,
    EXCEPT,
    CONCAT,
    CHUNK,
    REVERSE
}
