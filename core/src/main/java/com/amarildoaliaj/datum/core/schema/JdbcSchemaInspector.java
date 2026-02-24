package com.amarildoaliaj.datum.core.schema;


import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        Map<String, List<ColumnMeta>> columnsByTable = new HashMap<>();
        Map<String, List<String>> pkByTable = new HashMap<>();
        Map<String, List<ForeignKeyMeta>> importedFkByTable = new HashMap<>();

        // 1. read tables
        List<String> tables = readTables(md, schema, onlyTables);

        // 2. columns + pk + fk for all tables
        for (String table : tables) {
            List<ColumnMeta> columnMetas = readColumns(md, schema, table);
            columnsByTable.put(table, columnMetas);

            List<String> primaryKeys = readPrimaryKey(md, schema, table);
            pkByTable.put(table, primaryKeys);

            List<ForeignKeyMeta> foreignKeyMetas = readImportedKeys(md, schema, table);
            importedFkByTable.put(table, foreignKeyMetas);
        }

        // 3. build response
        List<TableMeta> result = new ArrayList<>();
        for (String table : tables) {
            TableMeta tableMeta = new TableMeta(
                    schema,
                    table,
                    columnsByTable.getOrDefault(table, List.of()),
                    pkByTable.getOrDefault(table, List.of()),
                    importedFkByTable.getOrDefault(table, List.of()));
            result.add(tableMeta);
        }
        return result;
    }

    private List<String> readTables(DatabaseMetaData md, @Nullable String schema, List<String> onlyTables) throws SQLException {
        Set<String> filterSet = new HashSet<>(onlyTables);

        List<String> tables = new ArrayList<>();
        try (ResultSet rs = md.getTables(null, schema, "%", DEFAULT_TABLE_TYPES.toArray(new String[0]))) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!filterSet.isEmpty() && !filterSet.contains(tableName)) {
                    continue;
                }
                tables.add(tableName);
            }
        }
        tables.sort(String::compareToIgnoreCase);
        return tables;
    }

    private List<ColumnMeta> readColumns(DatabaseMetaData md, @Nullable String schema, String table) throws SQLException {
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
                String defVal = rs.getString("COLUMN_DEF");

                boolean autoIncrement = false;
                try {
                    String isAuto = rs.getString("IS_AUTOINCREMENT");
                    autoIncrement = "YES".equalsIgnoreCase(isAuto);
                } catch (SQLException ignored) {
                    // some drivers don't support this column
                }

                ColumnMeta columnMeta = new ColumnMeta(
                        name,
                        jdbcType,
                        typeName,
                        nullable,
                        size,
                        scale,
                        defVal,
                        autoIncrement);
                cols.add(columnMeta);
            }
        }
        return cols;
    }

    private List<String> readPrimaryKey(DatabaseMetaData md, @Nullable String schema, String table) throws SQLException {

        record PkCol(String name, short seq) {
        }

        List<PkCol> pk = new ArrayList<>();
        try (ResultSet rs = md.getPrimaryKeys(null, schema, table)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                short seq = rs.getShort("KEY_SEQ");
                pk.add(new PkCol(col, seq));
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
        // group by FK_NAME and sort by KEY_SEQ
        record FkRow(@Nullable String fkName, String pkTable, String fkTable, String pkColumn, String fkColumn,
                     short seq) {
        }

        List<FkRow> rows = new ArrayList<>();
        try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String fkTable = rs.getString("FKTABLE_NAME");
                String pkCol = rs.getString("PKCOLUMN_NAME");
                String fkCol = rs.getString("FKCOLUMN_NAME");
                short seq = rs.getShort("KEY_SEQ");

                FkRow fkRow = new FkRow(fkName, pkTable, fkTable, pkCol, fkCol, seq);
                rows.add(fkRow);
            }
        }

        Function<FkRow, String> fkRowStringFunction = r -> r.fkName() == null
                ? "<unnamed>"
                : r.fkName();
        Map<String, List<FkRow>> byName = rows.stream()
                .collect(Collectors.groupingBy(fkRowStringFunction));

        List<ForeignKeyMeta> fks = new ArrayList<>();
        for (Map.Entry<String, List<FkRow>> e : byName.entrySet()) {
            List<FkRow> fkRows = new ArrayList<>(e.getValue());
            fkRows.sort(Comparator.comparingInt(FkRow::seq));

            String fkName = "<unnamed>".equals(e.getKey())
                    ? null
                    : e.getKey();
            String pkTable = fkRows.getFirst().pkTable();
            String fkTable = fkRows.getFirst().fkTable();

            List<String> pkCols = fkRows.stream().map(FkRow::pkColumn).toList();
            List<String> fkCols = fkRows.stream().map(FkRow::fkColumn).toList();

            fks.add(new ForeignKeyMeta(fkName, pkTable, fkTable, pkCols, fkCols));
        }

        // stable order
        Function<ForeignKeyMeta, String> foreignKeyMetaStringFunction = fk -> fk.name() == null
                ? ""
                : fk.name();
        fks.sort(Comparator.comparing(foreignKeyMetaStringFunction));
        return fks;
    }

    @Nullable
    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull()
                ? null
                : val;
    }
}
