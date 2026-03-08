package com.amarildoaliaj.datum.core.generator.unique;


import com.amarildoaliaj.datum.core.schema.ColumnMeta;
import com.amarildoaliaj.datum.core.schema.TableMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@NullMarked
public final class DefaultUniqueValueGenerator implements UniqueValueGenerator {

    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().replace("-", "");

    private static long positiveHash64(String value) {
        byte[] bytes = value.getBytes(UTF_8);
        long hash = 0xcbf29ce484222325L;

        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }

        return hash;
    }

    @Override
    public synchronized Object nextUniqueValue(TableMeta table, ColumnMeta column) {
        String key = table.qualifiedName() + "." + column.name();
        long index = counters.compute(key, (k, current) -> current == null ? 0L : Math.incrementExact(current));

        return switch (column.jdbcType()) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR ->
                    generateSafeString(table.name(), column.name(), index, column.size());
            case Types.INTEGER -> nextIntValue(key, index);
            case Types.BIGINT -> nextLongValue(key, index);
            case Types.SMALLINT -> nextShortValue(key, index);
            case Types.TINYINT -> nextByteValue(key, index);
            default -> generateSafeString(table.name(), column.name(), index, column.size());
        };
    }

    private String generateSafeString(String tableName, String columnName, long index, @Nullable Integer maxSize) {
        // suffix (the part that guarantees uniqueness)
        String suffix = "_" + runId + "_" + index;
        int limit = (maxSize != null && maxSize > 0) ? maxSize : 255;

        if (suffix.length() >= limit) {
            return suffix.substring(suffix.length() - limit);
        }

        String base = tableName + "_" + columnName;
        int maxBaseLength = limit - suffix.length();
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        return base + suffix;
    }

    private int nextIntValue(String key, long index) {
        if (index > Integer.MAX_VALUE) {
            throw new IllegalStateException("Exhausted INTEGER unique values for key: " + key);
        }

        int base = intBase(key);
        return Math.addExact(base, Math.toIntExact(index));
    }

    private long nextLongValue(String key, long index) {
        long base = longBase(key);
        return Math.addExact(base, index);
    }

    private short nextShortValue(String key, long index) {
        int base = shortBase(key);
        long candidate = base + index;

        if (candidate < Short.MIN_VALUE || candidate > Short.MAX_VALUE) {
            throw new IllegalStateException("Exhausted SMALLINT unique values for key: " + key);
        }
        return (short) candidate;
    }

    private byte nextByteValue(String key, long index) {
        int base = byteBase(key);
        long candidate = base + index;

        if (candidate < Byte.MIN_VALUE || candidate > Byte.MAX_VALUE) {
            throw new IllegalStateException("Exhausted TINYINT unique values for key: " + key);
        }
        return (byte) candidate;
    }

    private int intBase(String key) {
        long hash = positiveHash64(key + "#" + runId);
        return Integer.MIN_VALUE + (int) Long.remainderUnsigned(hash, Integer.MAX_VALUE);
    }

    private long longBase(String key) {
        long hash = positiveHash64(key + "#" + runId);
        return Long.MIN_VALUE + Long.remainderUnsigned(hash, Long.MAX_VALUE);
    }

    private int shortBase(String key) {
        long hash = positiveHash64(key + "#" + runId);
        return Short.MIN_VALUE + (int) Long.remainderUnsigned(hash, Short.MAX_VALUE);
    }

    private int byteBase(String key) {
        long hash = positiveHash64(key + "#" + runId);
        return Byte.MIN_VALUE + (int) Long.remainderUnsigned(hash, Byte.MAX_VALUE);
    }
}
