package com.amarildoaliaj.datum.core.generator;

import org.jspecify.annotations.Nullable;

public sealed interface GeneratedValue permits GeneratedValue.Param, GeneratedValue.NullValue {

    static GeneratedValue of(@Nullable Object v) {
        return v == null
                ? NullValue.INSTANCE
                : new Param(v);
    }

    record Param(Object value) implements GeneratedValue {
    }

    record NullValue() implements GeneratedValue {
        public static final NullValue INSTANCE = new NullValue();
    }
}
