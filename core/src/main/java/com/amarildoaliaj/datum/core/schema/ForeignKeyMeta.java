package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record ForeignKeyMeta(
        @Nullable String name,
        String pkTable,
        String fkTable,
        List<String> pkColumns,
        List<String> fkColumns
) {
    public ForeignKeyMeta(
            @Nullable String name,
            String pkTable,
            String fkTable,
            List<String> pkColumns,
            List<String> fkColumns
    ) {
        this.name = name;
        this.pkTable = pkTable;
        this.fkTable = fkTable;
        this.pkColumns = List.copyOf(pkColumns);
        this.fkColumns = List.copyOf(fkColumns);
        if (this.pkColumns.size() != this.fkColumns.size()) {
            throw new IllegalArgumentException("pkColumns and fkColumns must have same size");
        }
    }
}
