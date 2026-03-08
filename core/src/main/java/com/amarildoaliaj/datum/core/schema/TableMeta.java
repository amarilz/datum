package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record TableMeta(
        @Nullable String schema,
        String name,
        List<ColumnMeta> columns,
        List<String> primaryKeyColumns,
        List<ForeignKeyMeta> importedKeys,
        List<UniqueKeyMeta> uniqueKeys
) {

    public String qualifiedName() {
        return (schema == null || schema.isBlank())
                ? name
                : schema + "." + name;
    }

    public TableId id() {
        return TableId.of(this);
    }
}
