package com.amarildoaliaj.datum.core.order;

import com.amarildoaliaj.datum.core.schema.ForeignKeyMeta;
import com.amarildoaliaj.datum.core.schema.TableId;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

@NullMarked
public final class TopologicalTableOrderPlanner implements TableOrderPlanner {

    @Override
    public List<TableMeta> planInsertionOrder(List<TableMeta> tables) {
        Objects.requireNonNull(tables, "tables must not be null");

        Map<TableId, TableMeta> tableById = indexTables(tables);
        Map<TableId, Set<TableId>> adjacency = new LinkedHashMap<>();
        Map<TableId, Integer> indegree = new LinkedHashMap<>();

        for (TableMeta table : tables) {
            TableId tableId = table.id();
            adjacency.put(tableId, new LinkedHashSet<>());
            indegree.put(tableId, 0);
        }

        for (TableMeta child : tables) {
            TableId childId = child.id();

            for (ForeignKeyMeta fk : child.importedKeys()) {
                TableId parentId = TableId.of(fk.pkSchema(), fk.pkTable());
                TableId fkChildId = TableId.of(fk.fkSchema(), fk.fkTable());

                if (!childId.equals(fkChildId)) {
                    throw new IllegalStateException(
                            "Imported foreign key mismatch for table " + child.qualifiedName() +
                                    ": expected child " + childId + " but FK declares " + fkChildId
                    );
                }

                if (!tableById.containsKey(parentId)) {
                    continue;
                }

                Set<TableId> outgoing = adjacency.get(parentId);
                if (outgoing.add(childId)) {
                    indegree.put(childId, indegree.get(childId) + 1);
                }
            }
        }

        Queue<TableId> queue = new PriorityQueue<>(Comparator.comparing(TableId::qualifiedName));
        for (Map.Entry<TableId, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<TableMeta> ordered = new ArrayList<>(tables.size());

        while (!queue.isEmpty()) {
            TableId current = queue.remove();
            ordered.add(tableById.get(current));

            for (TableId dependent : adjacency.get(current)) {
                int next = indegree.get(dependent) - 1;
                indegree.put(dependent, next);

                if (next == 0) {
                    queue.add(dependent);
                }
            }
        }

        if (ordered.size() != tables.size()) {
            throw new CyclicForeignKeyException(buildCycleMessage(indegree));
        }

        return ordered;
    }

    private Map<TableId, TableMeta> indexTables(List<TableMeta> tables) {
        Map<TableId, TableMeta> tableById = new LinkedHashMap<>();

        for (TableMeta table : tables) {
            TableId tableId = table.id();
            TableMeta previous = tableById.put(tableId, table);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate table in input: " + tableId);
            }
        }

        return tableById;
    }

    private String buildCycleMessage(Map<TableId, Integer> indegree) {
        List<String> unresolved = indegree.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .map(TableId::qualifiedName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return "Cycle detected in foreign-key graph. Cannot compute insertion order. " +
                "Unresolved tables: " + unresolved;
    }
}
