package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import org.jspecify.annotations.NullMarked;

import java.util.random.RandomGenerator;

@NullMarked
@FunctionalInterface
public interface ColumnValueGenerator {
    GeneratedValue generate(ColumnMeta column, RandomGenerator rnd);
}
