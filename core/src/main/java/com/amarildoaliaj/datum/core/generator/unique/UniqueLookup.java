package com.amarildoaliaj.datum.core.generator.unique;


import com.amarildoaliaj.datum.core.schema.TableMeta;
import com.amarildoaliaj.datum.core.schema.UniqueKeyMeta;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.Set;

@NullMarked
public final class UniqueLookup {

    public Set<String> singleColumnUniqueColumnsOf(TableMeta table) {
        Set<String> result = new LinkedHashSet<>();

        for (UniqueKeyMeta uniqueKey : table.uniqueKeys()) {
            if (uniqueKey.isSingleColumn()) {
                result.add(uniqueKey.singleColumnOrThrow());
            }
        }

        return result;
    }
}
