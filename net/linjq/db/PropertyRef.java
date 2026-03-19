package net.linjq.db;

import net.linjq.exceptions.LinjqException;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A serializable function that captures a method reference and can extract
 * the property/column name from it via {@link SerializedLambda}.
 *
 * <p>This is the bridge between {@code Person::age} and the SQL column name {@code "age"}.</p>
 *
 * @param <T> the entity type
 * @param <R> the return type of the property
 */
@FunctionalInterface
public interface PropertyRef<T, R> extends Serializable {

    R apply(T t);

    /**
     * Cache to avoid repeated reflection for the same method reference.
     */
    Map<PropertyRef<?, ?>, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Extracts the property name from this method reference.
     * Handles record components (name()), getters (getName()), and boolean getters (isActive()).
     *
     * <p>For example:</p>
     * <ul>
     *   <li>{@code Person::name} → "name"</li>
     *   <li>{@code Person::getName} → "name"</li>
     *   <li>{@code Person::isActive} → "active"</li>
     * </ul>
     */
    default String propertyName() {
        return CACHE.computeIfAbsent(this, ref -> {
            try {
                Method writeReplace = ref.getClass().getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(ref);
                String methodName = lambda.getImplMethodName();
                return toPropertyName(methodName);
            } catch (Exception e) {
                throw new LinjqException("Failed to resolve property name from method reference. "
                        + "Ensure you are passing a method reference (e.g. Person::name), not a lambda.", e);
            }
        });
    }

    /**
     * Extracts the declaring class name from this method reference.
     */
    default String declaringClass() {
        try {
            Method writeReplace = this.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(this);
            return lambda.getImplClass().replace('/', '.');
        } catch (Exception e) {
            throw new LinjqException("Failed to resolve declaring class from method reference.", e);
        }
    }

    /**
     * Converts a method name to a property name.
     * "getName" → "name", "isActive" → "active", "age" → "age"
     */
    private static String toPropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }
}