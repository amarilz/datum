package com.amarildoaliaj.datum.core.execution;

import com.amarildoaliaj.datum.core.planning.InsertPlan;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class JdbcBatchInserter implements BatchInserter {

    private final InsertPlan plan;
    private final PreparedStatement statement;
    private final int batchSize;

    private int pendingCount;

    public JdbcBatchInserter(Connection connection, InsertPlan plan, int batchSize) throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");

        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }

        this.batchSize = batchSize;
        this.statement = connection.prepareStatement(plan.insertSql());
        this.pendingCount = 0;
    }

    @Override
    public void add(List<Object> values) throws SQLException {
        Objects.requireNonNull(values, "values must not be null");

        if (values.size() != plan.columnCount()) {
            throw new IllegalArgumentException("Expected " + plan.columnCount() + " values but got " + values.size());
        }

        bind(values);
        statement.addBatch();
        pendingCount++;

        if (pendingCount >= batchSize) {
            flush();
        }
    }

    @Override
    public int[] flush() throws SQLException {
        if (pendingCount == 0) {
            return new int[0];
        }

        int[] result = statement.executeBatch();
        pendingCount = 0;
        return result;
    }

    @Override
    public void close() throws SQLException {
        statement.close();
    }

    private void bind(List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            int parameterIndex = i + 1;

            if (value == null) {
                int jdbcType = plan.insertableColumns().get(i).jdbcType();
                statement.setNull(parameterIndex, jdbcType);
            } else {
                statement.setObject(parameterIndex, value);
            }
        }
    }
}
