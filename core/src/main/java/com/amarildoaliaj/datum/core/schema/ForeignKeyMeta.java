package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record ForeignKeyMeta(
        @Nullable String name,
        @Nullable String pkSchema,
        String pkTable,
        @Nullable String fkSchema,
        String fkTable,
        List<String> pkColumns,
        List<String> fkColumns
) {
    public ForeignKeyMeta(
            @Nullable String name,
            @Nullable String pkSchema,
            String pkTable,
            @Nullable String fkSchema,
            String fkTable,
            List<String> pkColumns,
            List<String> fkColumns
    ) {
        this.name = name;
        this.pkSchema = pkSchema;
        this.pkTable = pkTable;
        this.fkSchema = fkSchema;
        this.fkTable = fkTable;
        this.pkColumns = List.copyOf(pkColumns);
        this.fkColumns = List.copyOf(fkColumns);

        if (this.pkColumns.size() != this.fkColumns.size()) {
            throw new IllegalArgumentException("pkColumns and fkColumns must have same size");
        }
    }
}
