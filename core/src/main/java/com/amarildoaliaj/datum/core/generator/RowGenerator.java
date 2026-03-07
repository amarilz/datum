package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.generator.fk.ForeignKeyColumnRef;
import com.amarildoaliaj.datum.core.generator.fk.ForeignKeyLookup;
import com.amarildoaliaj.datum.core.generator.fk.ForeignKeyValueProvider;
import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

@NullMarked
@AllArgsConstructor
public final class RowGenerator {

    private final DefaultColumnGeneratorRegistry registry;
    private final ForeignKeyLookup foreignKeyLookup;
    private final ForeignKeyValueProvider foreignKeyValueProvider;

    public List<Object> generateValues(TableMeta table, List<ColumnMeta> columns, RandomGenerator rnd) throws SQLException {
        Map<String, ForeignKeyColumnRef> fkColumns = foreignKeyLookup.singleColumnForeignKeysOf(table);

        List<Object> values = new ArrayList<>(columns.size());

        for (ColumnMeta column : columns) {
            ForeignKeyColumnRef fkRef = fkColumns.get(column.name());

            if (fkRef != null) {
                Object referencedValue = foreignKeyValueProvider.resolveValue(fkRef, rnd);
                values.add(referencedValue);
                continue;
            }

            ColumnValueGenerator generator = registry.forColumn(column);
            GeneratedValue generatedValue = generator.generate(column, rnd);
            values.add(generatedValue.orNull());
        }

        return values;
    }
}
