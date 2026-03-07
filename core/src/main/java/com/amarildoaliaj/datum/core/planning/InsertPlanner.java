package com.amarildoaliaj.datum.core.planning;

import com.amarildoaliaj.datum.core.generator.GenerationPolicy;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InsertPlanner {
    InsertPlan plan(TableMeta table, GenerationPolicy generationPolicy);
}
