package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;

import java.util.random.RandomGenerator;

@FunctionalInterface
public interface ColumnValueGenerator {
    GeneratedValue generate(ColumnMeta column, RandomGenerator rnd);
}
