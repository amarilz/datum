package com.amarildoaliaj.datum.core.generator.fk;

import com.amarildoaliaj.datum.core.schema.TableId;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ForeignKeyColumnRef(
        TableId fkTable,
        String fkColumn,
        TableId pkTable,
        String pkColumn,
        String constraintName
) {
}
