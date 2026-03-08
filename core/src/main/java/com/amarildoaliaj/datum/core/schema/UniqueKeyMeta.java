package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record UniqueKeyMeta(
        @Nullable String name,
        List<String> columns
) {
    public UniqueKeyMeta {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
    }

    public boolean isSingleColumn() {
        return columns.size() == 1;
    }

    public String singleColumnOrThrow() {
        if (!isSingleColumn()) {
            throw new IllegalStateException("Unique key is not single-column: " + columns);
        }
        return columns.getFirst();
    }
}
