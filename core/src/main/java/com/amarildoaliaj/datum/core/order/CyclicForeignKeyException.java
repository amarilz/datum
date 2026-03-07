package com.amarildoaliaj.datum.core.order;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CyclicForeignKeyException extends RuntimeException {

    public CyclicForeignKeyException(String message) {
        super(message);
    }
}
