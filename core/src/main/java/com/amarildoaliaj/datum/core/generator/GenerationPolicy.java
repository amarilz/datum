package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;

import java.util.random.RandomGenerator;

public final class GenerationPolicy {
    private final double nullProbability;

    public GenerationPolicy(double nullProbability) {
        if (nullProbability < 0.0 || nullProbability > 1.0) {
            throw new IllegalArgumentException("nullProbability");
        }
        this.nullProbability = nullProbability;
    }

    public static GenerationPolicy defaults() {
        // TODO: 2% NULL on nullable (this should be parametric in the future)
        return new GenerationPolicy(0.02);
    }

    public boolean shouldSkip(ColumnMeta c) {
        // example: serial/identity or generated columns
        return c.autoIncrement() || (c.defaultValue() != null && !c.defaultValue().isBlank());
    }

    public boolean shouldGenerateNull(ColumnMeta c, RandomGenerator rnd) {
        return c.nullable() && rnd.nextDouble() < nullProbability;
    }
}
