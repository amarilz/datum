package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

@AllArgsConstructor
public final class RowGenerator {

    private final DefaultColumnGeneratorRegistry registry;

    public Map<String, GeneratedValue> generateRow(TableMeta table, RandomGenerator rnd) {
        Map<String, GeneratedValue> row = new LinkedHashMap<>();
        for (ColumnMeta col : table.columns()) {
            ColumnValueGenerator gen = registry.forColumn(col);
            row.put(col.name(), gen.generate(col, rnd));
        }
        return row;
    }
}
