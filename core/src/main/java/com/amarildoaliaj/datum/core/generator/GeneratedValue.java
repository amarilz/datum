package com.amarildoaliaj.datum.core.generator;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface GeneratedValue permits GeneratedValue.Param, GeneratedValue.NullValue {

    static GeneratedValue of(@Nullable Object v) {
        return v == null
                ? NullValue.INSTANCE
                : new Param(v);
    }

    @Nullable
    default Object orNull() {
        return switch (this) {
            case Param(var value) -> value;
            case NullValue ignored -> null;
        };
    }

    record Param(Object value) implements GeneratedValue {
    }

    record NullValue() implements GeneratedValue {
        public static final NullValue INSTANCE = new NullValue();
    }
}
