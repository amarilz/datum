package com.amarildoaliaj.datum.core.schema;


import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@NullMarked
public final class JdbcSchemaInspector implements SchemaInspector {

    private static final Set<String> DEFAULT_TABLE_TYPES = Set.of("TABLE");

    @Override
    public List<TableMeta> inspect(Connection connection) throws SQLException {
        return inspect(connection, null, List.of());
    }

    @Override
    public List<TableMeta> inspect(
            Connection connection,
            @Nullable String schema,
            List<String> onlyTables
    ) throws SQLException {
        Objects.requireNonNull(connection, "connection");

        DatabaseMetaData md = connection.getMetaData();

        List<TableRef> tables = readTables(md, schema, onlyTables);

        Map<TableRef, List<ColumnMeta>> columnsByTable = new LinkedHashMap<>();
        Map<TableRef, List<String>> pkByTable = new LinkedHashMap<>();
        Map<TableRef, List<ForeignKeyMeta>> importedFkByTable = new LinkedHashMap<>();

        for (TableRef tableRef : tables) {
            List<ColumnMeta> columnMetas = readColumns(md, tableRef.schema(), tableRef.name());
            columnsByTable.put(tableRef, columnMetas);

            List<String> primaryKeys = readPrimaryKey(md, tableRef.schema(), tableRef.name());
            pkByTable.put(tableRef, primaryKeys);

            List<ForeignKeyMeta> foreignKeyMetas = readImportedKeys(md, tableRef.schema(), tableRef.name());
            importedFkByTable.put(tableRef, foreignKeyMetas);
        }

        List<TableMeta> result = new ArrayList<>(tables.size());
        for (TableRef tableRef : tables) {
            TableMeta tableMeta = new TableMeta(
                    tableRef.schema(),
                    tableRef.name(),
                    columnsByTable.getOrDefault(tableRef, List.of()),
                    pkByTable.getOrDefault(tableRef, List.of()),
                    importedFkByTable.getOrDefault(tableRef, List.of()),
                    readUniqueKeys(md, tableRef.schema(), tableRef.name(), pkByTable.getOrDefault(tableRef, List.of())));
            result.add(tableMeta);
        }
        return result;
    }

    private List<TableRef> readTables(
            DatabaseMetaData md,
            @Nullable String schema,
            List<String> onlyTables
    ) throws SQLException {
        Set<String> filterSet = new HashSet<>(onlyTables);

        List<TableRef> tables = new ArrayList<>();

        try (ResultSet rs = md.getTables(null, schema, "%", DEFAULT_TABLE_TYPES.toArray(String[]::new))) {
            while (rs.next()) {
                String tableSchema = normalizeSchema(rs.getString("TABLE_SCHEM"));
                String tableName = rs.getString("TABLE_NAME");

                if (!filterSet.isEmpty() && !filterSet.contains(tableName)) {
                    continue;
                }

                tables.add(new TableRef(tableSchema, tableName));
            }
        }

        tables.sort(Comparator.comparing(
                        (TableRef t) -> t.schema() == null
                                ? ""
                                : t.schema(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(TableRef::name, String.CASE_INSENSITIVE_ORDER));

        return tables;
    }

    private List<ColumnMeta> readColumns(
            DatabaseMetaData md,
            @Nullable String schema,
            String table
    ) throws SQLException {
        List<ColumnMeta> cols = new ArrayList<>();

        try (ResultSet rs = md.getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");

                int nullableFlag = rs.getInt("NULLABLE");
                boolean nullable = nullableFlag == DatabaseMetaData.columnNullable;

                Integer size = getNullableInt(rs, "COLUMN_SIZE");
                Integer scale = getNullableInt(rs, "DECIMAL_DIGITS");
                String defaultValue = rs.getString("COLUMN_DEF");

                boolean autoIncrement = false;
                try {
                    String isAuto = rs.getString("IS_AUTOINCREMENT");
                    autoIncrement = "YES".equalsIgnoreCase(isAuto);
                } catch (SQLException ignored) {
                    // some drivers do not support IS_AUTOINCREMENT.
                }

                ColumnMeta columnMeta = new ColumnMeta(
                        name,
                        jdbcType,
                        typeName,
                        nullable,
                        size,
                        scale,
                        defaultValue,
                        autoIncrement);
                cols.add(columnMeta);
            }
        }
        return cols;
    }

    private List<String> readPrimaryKey(
            DatabaseMetaData md,
            @Nullable String schema,
            String table
    ) throws SQLException {
        record PkCol(String name, short seq) {
        }

        List<PkCol> pk = new ArrayList<>();

        try (ResultSet rs = md.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                short seq = rs.getShort("KEY_SEQ");
                pk.add(new PkCol(column, seq));
            }
        }

        pk.sort(Comparator.comparingInt(PkCol::seq));
        return pk.stream()
                .map(PkCol::name)
                .toList();
    }

    private List<ForeignKeyMeta> readImportedKeys(
            DatabaseMetaData md,
            @Nullable String schema,
            String table
    ) throws SQLException {
        Map<ForeignKeyKey, ForeignKeyBuilder> builders = new LinkedHashMap<>();

        try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");

                String pkSchema = normalizeSchema(rs.getString("PKTABLE_SCHEM"));
                String pkTable = rs.getString("PKTABLE_NAME");

                String fkSchema = normalizeSchema(rs.getString("FKTABLE_SCHEM"));
                String fkTable = rs.getString("FKTABLE_NAME");

                String pkColumn = rs.getString("PKCOLUMN_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");

                short seq = rs.getShort("KEY_SEQ");

                ForeignKeyKey key = new ForeignKeyKey(fkName, pkSchema, pkTable, fkSchema, fkTable);

                ForeignKeyBuilder builder = builders.computeIfAbsent(
                        key,
                        ignored -> new ForeignKeyBuilder(fkName, pkSchema, pkTable, fkSchema, fkTable));

                builder.add(seq, pkColumn, fkColumn);
            }
        }

        return builders.values().stream()
                .map(ForeignKeyBuilder::build)
                .sorted(
                        Comparator.comparing(
                                        (ForeignKeyMeta fk) -> fk.name() == null ? "" : fk.name(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        fk -> fk.pkSchema() == null ? "" : fk.pkSchema(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(ForeignKeyMeta::pkTable, String.CASE_INSENSITIVE_ORDER)
                )
                .toList();
    }

    private List<UniqueKeyMeta> readUniqueKeys(
            DatabaseMetaData md,
            @Nullable String schema,
            String table,
            List<String> primaryKeyColumns
    ) throws SQLException {
        record IndexColumn(String indexName, String columnName, short ordinalPosition) {
        }
        record UniqueKeyBuilder(String name, List<IndexColumn> columns) {
        }

        Map<String, List<IndexColumn>> byIndex = new LinkedHashMap<>();

        try (ResultSet rs = md.getIndexInfo(null, schema, table, true, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");

                if (indexName == null || columnName == null) {
                    continue;
                }

                short ordinalPosition = rs.getShort("ORDINAL_POSITION");
                byIndex.computeIfAbsent(indexName, ignored -> new ArrayList<>())
                        .add(new IndexColumn(indexName, columnName, ordinalPosition));
            }
        }

        List<UniqueKeyMeta> result = new ArrayList<>();

        for (Map.Entry<String, List<IndexColumn>> entry : byIndex.entrySet()) {
            List<IndexColumn> columns = new ArrayList<>(entry.getValue());
            columns.sort(Comparator.comparingInt(IndexColumn::ordinalPosition));

            List<String> names = columns.stream()
                    .map(IndexColumn::columnName)
                    .toList();

            if (names.equals(primaryKeyColumns)) {
                continue;
            }

            result.add(new UniqueKeyMeta(entry.getKey(), names));
        }

        result.sort(Comparator.comparing(uk -> uk.name() == null
                        ? ""
                        : uk.name(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Nullable
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @Nullable
    private String normalizeSchema(@Nullable String schema) {
        return (schema == null || schema.isBlank()) ? null : schema;
    }

    private record TableRef(
            @Nullable String schema,
            String name
    ) {
    }

    private record ForeignKeyKey(
            @Nullable String name,
            @Nullable String pkSchema,
            String pkTable,
            @Nullable String fkSchema,
            String fkTable
    ) {
    }

    private record FkColumn(
            short seq,
            String pkColumn,
            String fkColumn
    ) {
    }

    private static final class ForeignKeyBuilder {

        private final @Nullable String name;
        private final @Nullable String pkSchema;
        private final String pkTable;
        private final @Nullable String fkSchema;
        private final String fkTable;

        private final List<FkColumn> columns = new ArrayList<>();

        private ForeignKeyBuilder(
                @Nullable String name,
                @Nullable String pkSchema,
                String pkTable,
                @Nullable String fkSchema,
                String fkTable
        ) {
            this.name = name;
            this.pkSchema = pkSchema;
            this.pkTable = Objects.requireNonNull(pkTable, "pkTable");
            this.fkSchema = fkSchema;
            this.fkTable = Objects.requireNonNull(fkTable, "fkTable");
        }

        private void add(short seq, String pkColumn, String fkColumn) {
            columns.add(new FkColumn(seq, pkColumn, fkColumn));
        }

        private ForeignKeyMeta build() {
            columns.sort(Comparator.comparingInt(FkColumn::seq));

            List<String> pkColumns = columns.stream()
                    .map(FkColumn::pkColumn)
                    .toList();

            List<String> fkColumns = columns.stream()
                    .map(FkColumn::fkColumn)
                    .toList();

            return new ForeignKeyMeta(
                    name,
                    pkSchema,
                    pkTable,
                    fkSchema,
                    fkTable,
                    pkColumns,
                    fkColumns
            );
        }
    }
}