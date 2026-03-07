package com.amarildoaliaj.datum.core.generator.fk;

import org.jspecify.annotations.NullMarked;

import java.sql.SQLException;
import java.util.random.RandomGenerator;

@NullMarked
public interface ForeignKeyValueProvider {

    Object resolveValue(ForeignKeyColumnRef foreignKeyColumnRef, RandomGenerator rnd) throws SQLException;
}
