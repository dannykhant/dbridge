package dbridge.runtime;

import java.util.LinkedHashMap;
import java.util.Set;

/**
 * An ordered, name-indexed row of the loop context table. Columns are added by
 * position (a varargs constructor) or by name; the first column is the loop key.
 */
public class Record {

    private final LinkedHashMap<String, Object> columns = new LinkedHashMap<>();

    public Record() {
    }

    /** Positional constructor: columns are named c0, c1, ... in order. */
    public Record(Object... values) {
        for (int i = 0; i < values.length; i++) {
            columns.put("c" + i, values[i]);
        }
    }

    public void set(String column, Object value) {
        columns.put(column, value);
    }

    public Object get(String column) {
        return columns.get(column);
    }

    public Set<String> columns() {
        return columns.keySet();
    }

    public Object get(int index) {
        int i = 0;
        for (Object value : columns.values()) {
            if (i == index) {
                return value;
            }
            i++;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", size: " + columns.size());
    }

    public int getInt(int index) {
        return ((Number) get(index)).intValue();
    }

    public long getLong(int index) {
        return ((Number) get(index)).longValue();
    }

    public boolean getBoolean(int index) {
        return (Boolean) get(index);
    }

    public String getString(int index) {
        return (String) get(index);
    }
}
