package com.agora.tenframework.util;

import java.lang.reflect.Field;

/**
 * Utility class
 *
 * @author Agora IO
 * @version 1.0.0
 */
public class Utils {

    /**
     * Get field value from object using reflection
     * Equivalent to Go's getFieldValue function
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get field value as string
     */
    public static String getFieldValueAsString(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        return value != null ? value.toString() : "";
    }

    /**
     * Get field value as integer
     */
    public static Integer getFieldValueAsInteger(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * Get field value as long
     */
    public static Long getFieldValueAsLong(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}