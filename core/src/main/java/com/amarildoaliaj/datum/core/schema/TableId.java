package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TableId(@Nullable String schema, @Nullable String name) {

    public TableId {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        schema = normalize(schema);
    }

    public static TableId of(TableMeta table) {
        return new TableId(table.schema(), table.name());
    }

    public static TableId of(@Nullable String schema, String name) {
        return new TableId(schema, name);
    }

    @Nullable
    private static String normalize(@Nullable String schema) {
        return (schema == null || schema.isBlank())
                ? null
                : schema;
    }

    @Nullable
    public String qualifiedName() {
        return schema == null
                ? name
                : schema + "." + name;
    }

    @Override
    @Nullable
    public String toString() {
        return qualifiedName();
    }
}
