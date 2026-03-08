package com.amarildoaliaj.datum.core.generator.unique;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;

public interface UniqueValueGenerator {

    Object nextUniqueValue(TableMeta table, ColumnMeta column);
}
