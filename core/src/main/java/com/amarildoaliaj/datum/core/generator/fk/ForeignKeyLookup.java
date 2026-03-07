package com.amarildoaliaj.datum.core.generator.fk;

import com.amarildoaliaj.datum.core.schema.ForeignKeyMeta;
import com.amarildoaliaj.datum.core.schema.TableId;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@NullMarked
public final class ForeignKeyLookup {

    public Map<String, ForeignKeyColumnRef> singleColumnForeignKeysOf(TableMeta table) {
        Objects.requireNonNull(table, "table must not be null");

        Map<String, ForeignKeyColumnRef> result = new LinkedHashMap<>();

        for (ForeignKeyMeta fk : table.importedKeys()) {
            if (fk.fkColumns().size() != 1 || fk.pkColumns().size() != 1) {
                continue;
            }

            TableId fkTableId = TableId.of(fk.fkSchema(), fk.fkTable());
            TableId expectedTableId = table.id();

            if (!fkTableId.equals(expectedTableId)) {
                throw new IllegalStateException(
                        "Foreign key metadata mismatch for table " + table.qualifiedName() +
                                ": FK declares child table " + fkTableId.qualifiedName()
                );
            }

            String fkColumn = fk.fkColumns().getFirst();
            String pkColumn = fk.pkColumns().getFirst();

            ForeignKeyColumnRef ref = new ForeignKeyColumnRef(
                    fkTableId,
                    fkColumn,
                    TableId.of(fk.pkSchema(), fk.pkTable()),
                    pkColumn,
                    fk.name()
            );

            ForeignKeyColumnRef previous = result.put(fkColumn, ref);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple single-column foreign keys found for column " +
                                table.qualifiedName() + "." + fkColumn
                );
            }
        }

        return result;
    }
}
