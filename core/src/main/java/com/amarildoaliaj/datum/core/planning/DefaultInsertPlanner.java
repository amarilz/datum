package com.amarildoaliaj.datum.core.planning;

import com.amarildoaliaj.datum.core.generator.GenerationPolicy;
import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@NullMarked
public final class DefaultInsertPlanner implements InsertPlanner {

    @Override
    public InsertPlan plan(TableMeta table, GenerationPolicy policy) {
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<ColumnMeta> insertableColumns = table.columns().stream()
                .filter(column -> !policy.shouldSkip(column))
                .toList();

        String insertSql = buildInsertSql(table, insertableColumns);

        return new InsertPlan(table, insertableColumns, insertSql);
    }

    private String buildInsertSql(TableMeta table, List<ColumnMeta> columns) {
        String qualifiedTableName = quoteQualifiedTableName(table);

        if (columns.isEmpty()) {
            return "INSERT INTO " + qualifiedTableName + " DEFAULT VALUES";
        }

        String columnList = columns.stream()
                .map(ColumnMeta::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));

        String placeholders = columns.stream()
                .map(column -> "?")
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + qualifiedTableName +
                " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    private String quoteQualifiedTableName(TableMeta table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.name());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.name());
    }

    private String quoteIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier must not be null");
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
