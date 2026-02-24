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
        CommandLine cmd = new CommandLine(new Main());

        CommandLine.IExecutionExceptionHandler iExecutionExceptionHandler = (ex, commandLine, parseResult) -> {
            // commandLine qui è quello del comando che ha lanciato l’eccezione (subcommand incluso).
            // Non facciamo cast: recuperiamo il root e leggiamo verbose in modo sicuro.
            boolean verbose = false;

            CommandLine root = commandLine;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Object rootObj = root.getCommand();
            if (rootObj instanceof Main m) {
                verbose = m.verbose;
            }

            System.err.println("Error: " + ex.getMessage());
            if (verbose) {
                ex.printStackTrace(System.err);
            } else {
                System.err.println("Run with --verbose to see the stacktrace.");
            }
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        };
        cmd.setExecutionExceptionHandler(iExecutionExceptionHandler);

        System.exit(cmd.execute(args));
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
                description = "Database password.",
                interactive = true,
                arity = "0..1"
        )
        @Nullable String password;

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

            String effectivePassword = getEffectivePassword();

            try (Connection connection = DriverManager.getConnection(url, user, effectivePassword)) {
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

        private String getEffectivePassword() {
            String effectivePassword = password;

            // fallback on env var (for CI)
            if (effectivePassword == null || effectivePassword.isBlank()) {
                effectivePassword = System.getenv("DB_PASSWORD");
            }
            if (effectivePassword == null || effectivePassword.isBlank()) {
                // picocli asks with TTY, but in non-interactive env (Gradle run/CI) you can't do that
                throw new IllegalArgumentException("Password not provided. Use --password or set DB_PASSWORD.");
            }
            return effectivePassword;
        }
    }
}
