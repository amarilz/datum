package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ColumnMeta(
        String name,
        int jdbcType,
        String typeName,
        boolean nullable,
        @Nullable Integer size,
        @Nullable Integer scale,
        @Nullable String defaultValue,
        boolean autoIncrement
) {
}
