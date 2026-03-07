package com.amarildoaliaj.datum.core.generator.fk;

import com.amarildoaliaj.datum.core.schema.TableId;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

@NullMarked
public final class JdbcForeignKeyValueProvider implements ForeignKeyValueProvider {

    private final Connection connection;
    private final Map<ReferencedColumnKey, List<Object>> cache = new LinkedHashMap<>();

    public JdbcForeignKeyValueProvider(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    @Override
    public Object resolveValue(ForeignKeyColumnRef foreignKeyColumnRef, RandomGenerator rnd) throws SQLException {
        Objects.requireNonNull(foreignKeyColumnRef, "foreignKeyColumnRef must not be null");
        Objects.requireNonNull(rnd, "rnd must not be null");

        ReferencedColumnKey key = new ReferencedColumnKey(
                foreignKeyColumnRef.pkTable(),
                foreignKeyColumnRef.pkColumn()
        );

        List<Object> values = cache.get(key);
        if (values == null) {
            values = loadValues(key);
            cache.put(key, values);
        }

        if (values.isEmpty()) {
            throw new IllegalStateException(
                    "No referenced values found for " +
                            key.tableId().qualifiedName() + "." + key.columnName()
            );
        }

        int index = rnd.nextInt(values.size());
        return values.get(index);
    }

    private List<Object> loadValues(ReferencedColumnKey key) throws SQLException {
        String sql = buildSelectSql(key.tableId(), key.columnName());

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Object> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getObject(1));
            }

            return List.copyOf(values);
        }
    }

    private String buildSelectSql(TableId tableId, String columnName) {
        String qualifiedTable = tableId.schema() == null || tableId.schema().isBlank()
                ? quoteIdentifier(tableId.name())
                : quoteIdentifier(tableId.schema()) + "." + quoteIdentifier(tableId.name());

        return "SELECT " + quoteIdentifier(columnName) +
                " FROM " + qualifiedTable +
                " WHERE " + quoteIdentifier(columnName) + " IS NOT NULL";
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record ReferencedColumnKey(
            TableId tableId,
            String columnName
    ) {
    }
}
