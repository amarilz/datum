package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import org.jspecify.annotations.NullMarked;

import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

@NullMarked
public final class DefaultColumnGeneratorRegistry {

    private final GenerationPolicy policy;
    private final Map<Integer, ColumnValueGenerator> byJdbcType;

    public DefaultColumnGeneratorRegistry(GenerationPolicy policy) {
        this.policy = policy;

        this.byJdbcType = new HashMap<>();
        byJdbcType.put(Types.VARCHAR, this::genText);
        byJdbcType.put(Types.LONGVARCHAR, this::genText);
        byJdbcType.put(Types.CHAR, this::genText);
        byJdbcType.put(Types.NVARCHAR, this::genText);
        byJdbcType.put(Types.LONGNVARCHAR, this::genText);
        byJdbcType.put(Types.INTEGER, this::genInt);
        byJdbcType.put(Types.SMALLINT, this::genInt);
        byJdbcType.put(Types.TINYINT, this::genInt);
        byJdbcType.put(Types.BIGINT, this::genInt);
        byJdbcType.put(Types.BOOLEAN, this::genBoolean);
        byJdbcType.put(Types.BIT, this::genBoolean);
        byJdbcType.put(Types.TIMESTAMP, this::genTimestamp);
        byJdbcType.put(Types.TIMESTAMP_WITH_TIMEZONE, this::genTimestamp);
    }

    public ColumnValueGenerator forColumn(ColumnMeta c) {
        return (col, rnd) -> {
            if (policy.shouldSkip(col)) return GeneratedValue.NullValue.INSTANCE;
            if (policy.shouldGenerateNull(col, rnd)) return GeneratedValue.NullValue.INSTANCE;

            ColumnValueGenerator gen = byJdbcType.get(col.jdbcType());
            if (gen == null) {
                // fallback: if not supported -> NULL for now
                return GeneratedValue.NullValue.INSTANCE;
            }
            return gen.generate(col, rnd);
        };
    }

    private GeneratedValue genText(ColumnMeta columnMeta, RandomGenerator rnd) {
        // avoid huge strings
        int max = columnMeta.size() > 0
                ? Math.min(columnMeta.size(), 64)
                : 32;
        int len = Math.max(1, rnd.nextInt(1, max + 1));
        return GeneratedValue.of(randomAlphaNum(rnd, len));
    }

    private GeneratedValue genInt(ColumnMeta c, RandomGenerator rnd) {
        return GeneratedValue.of(rnd.nextInt(0, 1_000_001));
    }

    private GeneratedValue genBigInt(ColumnMeta c, RandomGenerator rnd) {
        long v = rnd.nextLong(0L, 10_000_000_000L);
        return GeneratedValue.of(v);
    }

    private GeneratedValue genBoolean(ColumnMeta c, RandomGenerator rnd) {
        return GeneratedValue.of(rnd.nextBoolean());
    }

    private GeneratedValue genTimestamp(ColumnMeta c, RandomGenerator rnd) {
        // TODO: past and future?
        // now - random days (0..365), random seconds in day
        long days = rnd.nextLong(0, 366);
        long seconds = rnd.nextLong(0, 24 * 60 * 60);
        Instant ts = Instant.now()
                .minus(days, ChronoUnit.DAYS)
                .minus(seconds, ChronoUnit.SECONDS);
        return GeneratedValue.of(java.sql.Timestamp.from(ts));
    }

    private String randomAlphaNum(RandomGenerator rnd, int len) {
        final char[] alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        var sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }
}
