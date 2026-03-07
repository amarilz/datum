package com.amarildoaliaj.datum.core.schema;

import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@NullMarked
public interface SchemaInspector {

    List<TableMeta> inspect(Connection connection) throws SQLException;

    List<TableMeta> inspect(Connection connection, String schema, List<String> onlyTables) throws SQLException;
}
