package com.amarildoaliaj.datum.cli;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.ForeignKeyMeta;
import com.amarildoaliaj.datum.core.schema.JdbcSchemaInspector;
import com.amarildoaliaj.datum.core.schema.SchemaInspector;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "datum",
        mixinStandardHelpOptions = true,
        version = "datum 0.1.0",
        description = "Generate and load synthetic data into a database.",
        subcommands = {Main.InspectCommand.class}
)
public class Main implements Runnable {

    @CommandLine.Option(names = "--verbose", description = "Print stacktraces on errors.")
    boolean verbose;

    public static void main(String[] args) {

        CommandLine.IExecutionExceptionHandler iExecutionExceptionHandler = (ex, cmd, parseResult) -> {
            Main root = cmd.getCommand();
            System.err.println("Error: " + ex.getMessage());
            if (root != null && root.verbose) {
                ex.printStackTrace(System.err);
            } else {
                System.err.println("Run with --verbose to see the stacktrace.");
            }
            return cmd.getCommandSpec().exitCodeOnExecutionException();
        };

        int exitCode = new CommandLine(new Main())
                .setExecutionExceptionHandler(iExecutionExceptionHandler)
                .execute(args);

        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Se l'utente esegue solo "datum" senza subcommand
        CommandLine.usage(this, System.out);
    }

    @CommandLine.Command(
            name = "inspect",
            description = "Inspect database schema (tables, columns, PKs, imported FKs).",
            mixinStandardHelpOptions = true
    )
    static class InspectCommand implements Callable<Integer> {

        @CommandLine.Option(
                names = {"-u", "--url"},
                required = true,
                description = "JDBC URL. Example: jdbc:postgresql://localhost:5432/mydb"
        )
        String url;

        @CommandLine.Option(
                names = {"-U", "--user"},
                required = true,
                description = "Database user."
        )
        String user;

        @CommandLine.Option(
                names = {"-p", "--password"},
                required = true,
                description = "Database password.",
                interactive = true,
                arity = "0..1"
        )
        String password;

        @CommandLine.Option(
                names = {"-s", "--schema"},
                defaultValue = "public",
                description = "Schema to inspect. Default: ${DEFAULT-VALUE}"
        )
        String schema;

        @CommandLine.Option(
                names = {"-t", "--tables"},
                split = ",",
                description = "Comma-separated list of tables to inspect (without schema). Example: users,orders"
        )
        @Nullable List<String> tables;

        @CommandLine.Option(
                names = "--no-fk",
                description = "Do not print foreign keys."
        )
        boolean noFk;

        @Override
        public Integer call() throws Exception {

            // if password is not set -> ask again (TTY)
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                SchemaInspector inspector = new JdbcSchemaInspector();

                List<String> onlyTables = tables == null
                        ? List.of()
                        : tables;
                List<TableMeta> metas = inspector.inspect(connection, schema, onlyTables);

                if (metas.isEmpty()) {
                    System.out.println("No tables found.");
                    return 0;
                }

                for (TableMeta t : metas) {
                    System.out.println(t.qualifiedName());
                    System.out.println("  PK: " + t.primaryKeyColumns());

                    for (ColumnMeta col : t.columns()) {
                        String notNull = col.nullable()
                                ? ""
                                : " NOT NULL";
                        String def = (col.defaultValue() == null)
                                ? ""
                                : " DEFAULT " + col.defaultValue();
                        String auto = col.autoIncrement()
                                ? " (auto)"
                                : "";
                        System.out.println("  - " + col.name() + " " + col.typeName() + notNull + def + auto);
                    }

                    if (!noFk) {
                        for (ForeignKeyMeta fk : t.importedKeys()) {
                            System.out.println("  FK -> " + fk.pkTable() + " " + fk.fkColumns() + " -> " + fk.pkColumns());
                        }
                    }

                    System.out.println();
                }

                return 0;
            }
        }
    }
}
