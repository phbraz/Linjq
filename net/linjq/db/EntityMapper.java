package net.linjq.db;

import net.linjq.exceptions.LinjqException;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatically maps {@link ResultSet} rows to Java records or objects
 * using reflection. No annotations or manual mapping required.
 *
 * <p>For records, reads columns matching component names and invokes
 * the canonical constructor. Column names are matched case-insensitively.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EntityMapper<Person> mapper = EntityMapper.forRecord(Person.class);
 * Person person = mapper.map(resultSet);
 * }</pre>
 */
public final class EntityMapper<T> {

    private final Constructor<T> constructor;
    private final ComponentMapping[] mappings;

    private EntityMapper(Constructor<T> constructor, ComponentMapping[] mappings) {
        this.constructor = constructor;
        this.mappings = mappings;
    }

    /**
     * Creates an EntityMapper for a Java record class.
     * Maps record component names to ResultSet column names.
     */
    public static <T extends Record> EntityMapper<T> forRecord(Class<T> recordClass) {
        if (!recordClass.isRecord()) {
            throw new LinjqException(recordClass.getName() + " is not a record class");
        }

        RecordComponent[] components = recordClass.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        ComponentMapping[] mappings = new ComponentMapping[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            mappings[i] = new ComponentMapping(components[i].getName(), components[i].getType());
        }

        try {
            Constructor<T> ctor = recordClass.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return new EntityMapper<>(ctor, mappings);
        } catch (NoSuchMethodException e) {
            throw new LinjqException("Cannot find canonical constructor for record " + recordClass.getName(), e);
        }
    }

    /**
     * Maps the current ResultSet row to an entity instance.
     */
    public T map(ResultSet rs) throws SQLException {
        Object[] args = new Object[mappings.length];
        for (int i = 0; i < mappings.length; i++) {
            args[i] = readColumn(rs, mappings[i].columnName, mappings[i].type);
        }
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new LinjqException("Failed to construct entity from ResultSet row", e);
        }
    }

    /**
     * Returns the column names this mapper reads from.
     */
    public List<String> columnNames() {
        var names = new ArrayList<String>();
        for (var m : mappings) {
            names.add(m.columnName);
        }
        return names;
    }

    private Object readColumn(ResultSet rs, String columnName, Class<?> type) throws SQLException {
        Object value = rs.getObject(columnName);
        if (value == null) return null;

        // Handle common type conversions
        if (type == int.class || type == Integer.class) return rs.getInt(columnName);
        if (type == long.class || type == Long.class) return rs.getLong(columnName);
        if (type == double.class || type == Double.class) return rs.getDouble(columnName);
        if (type == float.class || type == Float.class) return rs.getFloat(columnName);
        if (type == boolean.class || type == Boolean.class) return rs.getBoolean(columnName);
        if (type == String.class) return rs.getString(columnName);
        if (type == java.math.BigDecimal.class) return rs.getBigDecimal(columnName);
        if (type == java.time.LocalDate.class) {
            var date = rs.getDate(columnName);
            return date != null ? date.toLocalDate() : null;
        }
        if (type == java.time.LocalDateTime.class) {
            var ts = rs.getTimestamp(columnName);
            return ts != null ? ts.toLocalDateTime() : null;
        }
        return value;
    }

    private record ComponentMapping(String columnName, Class<?> type) {}
}