package com.amarildoaliaj.datum.core.generator;

import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

@NullMarked
public final class DefaultColumnGeneratorRegistry {

    private static final char[] ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final GenerationPolicy policy;
    private final Map<Integer, ColumnValueGenerator> byJdbcType;

    public DefaultColumnGeneratorRegistry(GenerationPolicy policy) {
        this.policy = policy;

        this.byJdbcType = new HashMap<>();

        // text types
        byJdbcType.put(Types.VARCHAR, this::genText);
        byJdbcType.put(Types.LONGVARCHAR, this::genText);
        byJdbcType.put(Types.CHAR, this::genText);
        byJdbcType.put(Types.NVARCHAR, this::genText);
        byJdbcType.put(Types.LONGNVARCHAR, this::genText);
        byJdbcType.put(Types.NCHAR, this::genText);
        byJdbcType.put(Types.CLOB, this::genText);
        byJdbcType.put(Types.NCLOB, this::genText);

        // integer types
        byJdbcType.put(Types.TINYINT, this::genTinyInt);
        byJdbcType.put(Types.SMALLINT, this::genSmallInt);
        byJdbcType.put(Types.INTEGER, this::genInt);
        byJdbcType.put(Types.BIGINT, this::genBigInt);

        // floating point types
        byJdbcType.put(Types.FLOAT, this::genDouble);
        byJdbcType.put(Types.DOUBLE, this::genDouble);
        byJdbcType.put(Types.REAL, this::genFloat);
        byJdbcType.put(Types.NUMERIC, this::genDecimal);
        byJdbcType.put(Types.DECIMAL, this::genDecimal);

        // boolean types
        byJdbcType.put(Types.BOOLEAN, this::genBoolean);
        byJdbcType.put(Types.BIT, this::genBoolean);

        // date/time types
        byJdbcType.put(Types.DATE, this::genDate);
        byJdbcType.put(Types.TIME, this::genTime);
        byJdbcType.put(Types.TIME_WITH_TIMEZONE, this::genTime);
        byJdbcType.put(Types.TIMESTAMP, this::genTimestamp);
        byJdbcType.put(Types.TIMESTAMP_WITH_TIMEZONE, this::genTimestamp);

        // binary types
        byJdbcType.put(Types.BINARY, this::genBytes);
        byJdbcType.put(Types.VARBINARY, this::genBytes);
        byJdbcType.put(Types.LONGVARBINARY, this::genBytes);
        byJdbcType.put(Types.BLOB, this::genBytes);
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

    private GeneratedValue genTinyInt(ColumnMeta col, RandomGenerator rnd) {
        // signed TINYINT: -128..127 (use 0..127 for simplicity)
        return GeneratedValue.of(rnd.nextInt(0, 128));
    }

    private GeneratedValue genSmallInt(ColumnMeta col, RandomGenerator rnd) {
        // signed SMALLINT: -32768..32767
        return GeneratedValue.of(rnd.nextInt(0, 32_768));
    }

    private GeneratedValue genInt(ColumnMeta col, RandomGenerator rnd) {
        // signed INTEGER: up to ~2.1 billion; keep range sane for test data
        return GeneratedValue.of(rnd.nextInt(0, 1_000_001));
    }

    private GeneratedValue genBigInt(ColumnMeta col, RandomGenerator rnd) {
        return GeneratedValue.of(rnd.nextLong(0L, 10_000_000_000L));
    }

    private GeneratedValue genFloat(ColumnMeta col, RandomGenerator rnd) {
        return GeneratedValue.of((float) (rnd.nextDouble() * 1_000_000));
    }

    private GeneratedValue genDouble(ColumnMeta col, RandomGenerator rnd) {
        return GeneratedValue.of(rnd.nextDouble() * 1_000_000);
    }

    private GeneratedValue genDecimal(ColumnMeta col, RandomGenerator rnd) {
        int precision = col.size() > 0 ? col.size() : 10;
        int scale = col.scale() >= 0 ? col.scale() : 2;
        // generate an integer part up to (precision - scale) digits
        int intDigits = Math.max(1, precision - scale);
        long bound = (long) Math.pow(10, intDigits);
        long intPart = rnd.nextLong(0, bound);
        long fracPart = scale > 0 ? rnd.nextLong(0, (long) Math.pow(10, scale)) : 0;
        BigDecimal value = new BigDecimal(intPart + "." + String.format("%0" + scale + "d", fracPart));
        return GeneratedValue.of(value);
    }

    private GeneratedValue genBoolean(ColumnMeta col, RandomGenerator rnd) {
        return GeneratedValue.of(rnd.nextBoolean());
    }

    private GeneratedValue genDate(ColumnMeta col, RandomGenerator rnd) {
        long daysBack = rnd.nextLong(0, 366);
        LocalDate date = LocalDate.now().minusDays(daysBack);
        return GeneratedValue.of(java.sql.Date.valueOf(date));
    }

    private GeneratedValue genTime(ColumnMeta col, RandomGenerator rnd) {
        long secondsInDay = rnd.nextLong(0, 24L * 60 * 60);
        LocalTime time = LocalTime.ofSecondOfDay(secondsInDay);
        return GeneratedValue.of(java.sql.Time.valueOf(time));
    }

    private GeneratedValue genTimestamp(ColumnMeta col, RandomGenerator rnd) {
        long days = rnd.nextLong(0, 366);
        long seconds = rnd.nextLong(0, 24L * 60 * 60);
        Instant ts = Instant.now()
                .minus(days, ChronoUnit.DAYS)
                .minus(seconds, ChronoUnit.SECONDS);
        return GeneratedValue.of(java.sql.Timestamp.from(ts));
    }

    private GeneratedValue genBytes(ColumnMeta col, RandomGenerator rnd) {
        int max = col.size() > 0 ? Math.min(col.size(), 64) : 32;
        int len = Math.max(1, rnd.nextInt(1, max + 1));
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[i] = (byte) rnd.nextInt(0, 256);
        }
        return GeneratedValue.of(b);
    }

    private String randomAlphaNum(RandomGenerator rnd, int len) {
        var sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
