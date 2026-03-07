package com.amarildoaliaj.datum.core.planning;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

@NullMarked
public record InsertPlan(
        TableMeta table,
        List<ColumnMeta> insertableColumns,
        String insertSql
) {

    public InsertPlan {
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(insertableColumns, "insertableColumns must not be null");
        Objects.requireNonNull(insertSql, "insertSql must not be null");

        insertableColumns = List.copyOf(insertableColumns);
    }

    public int columnCount() {
        return insertableColumns.size();
    }

    public boolean usesDefaultValuesOnly() {
        return insertableColumns.isEmpty();
    }
}
