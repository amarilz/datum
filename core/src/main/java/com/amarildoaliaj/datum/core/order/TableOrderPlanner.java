package com.amarildoaliaj.datum.core.order;

import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public interface TableOrderPlanner {

    List<TableMeta> planInsertionOrder(List<TableMeta> tables);
}
