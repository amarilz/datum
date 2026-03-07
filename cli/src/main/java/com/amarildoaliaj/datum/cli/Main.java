package com.amarildoaliaj.datum.cli;

import com.amarildoaliaj.datum.core.execution.BatchInserter;
import com.amarildoaliaj.datum.core.execution.JdbcBatchInserter;
import com.amarildoaliaj.datum.core.generator.DefaultColumnGeneratorRegistry;
import com.amarildoaliaj.datum.core.generator.GenerationPolicy;
import com.amarildoaliaj.datum.core.generator.RowGenerator;
import com.amarildoaliaj.datum.core.generator.fk.ForeignKeyLookup;
import com.amarildoaliaj.datum.core.generator.fk.JdbcForeignKeyValueProvider;
import com.amarildoaliaj.datum.core.order.TableOrderPlanner;
import com.amarildoaliaj.datum.core.order.TopologicalTableOrderPlanner;
import com.amarildoaliaj.datum.core.planning.DefaultInsertPlanner;
import com.amarildoaliaj.datum.core.planning.InsertPlan;
import com.amarildoaliaj.datum.core.planning.InsertPlanner;
import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.JdbcSchemaInspector;
import com.amarildoaliaj.datum.core.schema.SchemaInspector;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;

@NullMarked
@CommandLine.Command(
        name = "datum",
        mixinStandardHelpOptions = true,
        version = "datum 0.1.0",
        description = "Generate and load synthetic data into a database.",
        subcommands = {
                Main.InspectCommand.class,
                Main.SeedCommand.class
        }
)
public class Main implements Runnable {

    @CommandLine.Option(
            names = "--verbose",
            description = "Print stacktraces on errors."
    )
    boolean verbose;

    public static void main(String[] args) {
        Main rootCommand = new Main();
        CommandLine cmd = new CommandLine(rootCommand);
        cmd.setExecutionExceptionHandler(new RootExecutionExceptionHandler());

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static final class RootExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        @Override
        public int handleExecutionException(
                Exception ex,
                CommandLine commandLine,
                CommandLine.ParseResult parseResult
        ) {
            Main root = findRootCommand(commandLine);

            System.err.println("Error: " + ex.getMessage());
            if (root.verbose) {
                ex.printStackTrace(System.err);
            } else {
                System.err.println("Run with --verbose to see the stacktrace.");
            }

            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        }

        private Main findRootCommand(CommandLine commandLine) {
            CommandLine current = commandLine;
            while (current.getParent() != null) {
                current = current.getParent();
            }

            Object root = current.getCommand();
            if (root instanceof Main main) {
                return main;
            }

            throw new IllegalStateException("Root command is not Main");
        }
    }

    static final class DbOptions {

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
                description = "Target schema. Default: ${DEFAULT-VALUE}"
        )
        String schema;

        @CommandLine.Option(
                names = {"-t", "--tables"},
                split = ",",
                description = "Comma-separated list of tables (without schema). Example: users,orders"
        )
        @Nullable List<String> tables;

        String effectivePassword() {
            String effectivePassword = password;

            if (effectivePassword == null || effectivePassword.isBlank()) {
                effectivePassword = System.getenv("DB_PASSWORD");
            }

            if (effectivePassword == null || effectivePassword.isBlank()) {
                throw new IllegalArgumentException("Password not provided. Use --password or set DB_PASSWORD.");
            }

            return effectivePassword;
        }

        List<String> onlyTables() {
            return tables == null ? List.of() : tables;
        }
    }

    @CommandLine.Command(
            name = "inspect",
            description = "Inspect database schema (tables, columns, PKs, imported FKs).",
            mixinStandardHelpOptions = true
    )
    static final class InspectCommand implements Callable<Integer> {

        @CommandLine.Mixin
        DbOptions db;

        @CommandLine.Option(
                names = "--no-fk",
                description = "Do not print foreign keys."
        )
        boolean noFk;

        private static void printTable(TableMeta table, boolean noFk) {
            System.out.println(table.qualifiedName());
            System.out.println("  PK: " + table.primaryKeyColumns());

            for (ColumnMeta col : table.columns()) {
                String notNull = col.nullable() ? "" : " NOT NULL";
                String def = (col.defaultValue() == null) ? "" : " DEFAULT " + col.defaultValue();
                String auto = col.autoIncrement() ? " (auto)" : "";

                System.out.println("  - " + col.name() + " " + col.typeName() + notNull + def + auto);
            }

            if (!noFk && !table.importedKeys().isEmpty()) {
                System.out.println("  FKs:");
                table.importedKeys().forEach(fk -> {
                    String fkName = fk.name() == null ? "<unnamed>" : fk.name();
                    String pkQualified = qualify(fk.pkSchema(), fk.pkTable());
                    String fkQualified = qualify(fk.fkSchema(), fk.fkTable());

                    System.out.println("    - " + fkName);
                    System.out.println("      " + fkQualified + " " + fk.fkColumns()
                            + " -> " + pkQualified + " " + fk.pkColumns());
                });
            }

            System.out.println();
        }

        private static String qualify(@Nullable String schema, String table) {
            return (schema == null || schema.isBlank())
                    ? table
                    : schema + "." + table;
        }

        @Override
        public Integer call() throws Exception {
            Objects.requireNonNull(db, "db");

            try (Connection connection = DriverManager.getConnection(
                    db.url,
                    db.user,
                    db.effectivePassword()
            )) {
                SchemaInspector inspector = new JdbcSchemaInspector();
                List<TableMeta> tables = inspector.inspect(connection, db.schema, db.onlyTables());

                if (tables.isEmpty()) {
                    System.out.println("No tables found.");
                    return 0;
                }

                for (TableMeta table : tables) {
                    printTable(table, noFk);
                }

                return 0;
            }
        }
    }

    @CommandLine.Command(
            name = "seed",
            description = "Generate synthetic rows and insert them into the database.",
            mixinStandardHelpOptions = true
    )
    static final class SeedCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        Main parent;

        @CommandLine.Mixin
        DbOptions db;

        @CommandLine.Option(
                names = {"-r", "--rows"},
                defaultValue = "100",
                description = "Rows to insert for each selected table. Default: ${DEFAULT-VALUE}"
        )
        int rows;

        @CommandLine.Option(
                names = {"-b", "--batch-size"},
                defaultValue = "1000",
                description = "JDBC batch size. Default: ${DEFAULT-VALUE}"
        )
        int batchSize;

        @CommandLine.Option(
                names = "--null-probability",
                defaultValue = "0.02",
                description = "Probability of generating NULL on nullable non-FK columns. Default: ${DEFAULT-VALUE}"
        )
        double nullProbability;

        @Override
        public Integer call() throws Exception {
            validateArguments();

            try (Connection connection = DriverManager.getConnection(
                    db.url,
                    db.user,
                    db.effectivePassword()
            )) {
                connection.setAutoCommit(false);

                try {
                    SchemaInspector inspector = new JdbcSchemaInspector();
                    List<TableMeta> tables = inspector.inspect(connection, db.schema, db.onlyTables());

                    if (tables.isEmpty()) {
                        System.out.println("No tables found.");
                        connection.commit();
                        return 0;
                    }

                    TableOrderPlanner orderPlanner = new TopologicalTableOrderPlanner();
                    List<TableMeta> orderedTables = orderPlanner.planInsertionOrder(tables);

                    GenerationPolicy generationPolicy = new GenerationPolicy(nullProbability);
                    InsertPlanner insertPlanner = new DefaultInsertPlanner();
                    RowGenerator rowGenerator = new RowGenerator(
                            new DefaultColumnGeneratorRegistry(generationPolicy),
                            new ForeignKeyLookup(),
                            new JdbcForeignKeyValueProvider(connection)
                    );

                    Random rnd = new Random();

                    for (TableMeta table : orderedTables) {
                        InsertPlan plan = insertPlanner.plan(table, generationPolicy);

                        if (plan.usesDefaultValuesOnly()) {
                            System.out.println("Skipping " + table.qualifiedName()
                                    + ": insert plan uses only DEFAULT VALUES.");
                            continue;
                        }

                        System.out.println("Seeding " + table.qualifiedName());
                        System.out.println("  SQL: " + plan.insertSql());

                        try (BatchInserter inserter = new JdbcBatchInserter(connection, plan, batchSize)) {
                            for (int i = 0; i < rows; i++) {
                                List<Object> values = rowGenerator.generateValues(
                                        table,
                                        plan.insertableColumns(),
                                        rnd
                                );
                                inserter.add(values);
                            }
                            inserter.flush();
                        }
                    }

                    connection.commit();
                    System.out.println("Seed completed successfully.");
                    return 0;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }
        }

        private void validateArguments() {
            if (rows <= 0) {
                throw new IllegalArgumentException("--rows must be > 0");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("--batch-size must be > 0");
            }
            if (nullProbability < 0.0 || nullProbability > 1.0) {
                throw new IllegalArgumentException("--null-probability must be between 0.0 and 1.0");
            }
        }
    }
}
