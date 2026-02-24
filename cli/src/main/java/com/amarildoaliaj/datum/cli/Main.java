package com.amarildoaliaj.datum.cli;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.ForeignKeyMeta;
import com.amarildoaliaj.datum.core.schema.JdbcSchemaInspector;
import com.amarildoaliaj.datum.core.schema.SchemaInspector;
import com.amarildoaliaj.datum.core.schema.TableMeta;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws SQLException {

        Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/mydb", "myuser", "mypassword");
        try (Connection c = connection) {
            SchemaInspector inspector = new JdbcSchemaInspector();
            List<TableMeta> tables = inspector.inspect(c, "public", List.of());

            for (TableMeta t : tables) {
                System.out.println(t.qualifiedName());
                System.out.println("  PK: " + t.primaryKeyColumns());
                for (ColumnMeta col : t.columns()) {
                    System.out.println("  - " + col.name() + " " + col.typeName() + (col.nullable() ? "" : " NOT NULL"));
                }
                for (ForeignKeyMeta fk : t.importedKeys()) {
                    System.out.println("  FK -> " + fk.pkTable() + " " + fk.fkColumns() + " -> " + fk.pkColumns());
                }
                System.out.println();
            }
        }
    }
}
