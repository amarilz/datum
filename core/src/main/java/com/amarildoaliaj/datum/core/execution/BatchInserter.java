package com.amarildoaliaj.datum.core.execution;

import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.util.List;

@NullMarked
public interface BatchInserter extends AutoCloseable {

    void add(List<Object> values) throws SQLException;

    int[] flush() throws SQLException;

    @Override
    void close() throws SQLException;
}
