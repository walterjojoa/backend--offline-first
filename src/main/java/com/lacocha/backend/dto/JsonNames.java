package com.lacocha.backend.dto;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Translates a Java property path ("ponds[0].updatedAt") into the name the client sees in the JSON
 * ("estanques[0].actualizado_en"), so validation errors use the same names as the request.
 */
public final class JsonNames {

    private JsonNames() {
    }

    public static String of(Class<?> root, String javaPath) {
        StringBuilder out = new StringBuilder();
        Class<?> current = root;
        for (String segment : javaPath.split("\\.")) {
            int bracket = segment.indexOf('[');
            String name = bracket >= 0 ? segment.substring(0, bracket) : segment;
            String index = bracket >= 0 ? segment.substring(bracket) : "";

            Field field = current != null ? findField(current, name) : null;
            JsonProperty json = field != null ? field.getAnnotation(JsonProperty.class) : null;
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(json != null ? json.value() : toSnake(name)).append(index);
            current = field != null ? elementType(field) : null;
        }
        return out.toString();
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        return null;
    }

    /** For List<PondSync> returns PondSync; for any other field, its own type. */
    private static Class<?> elementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType p && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return field.getType();
    }

    static String toSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
