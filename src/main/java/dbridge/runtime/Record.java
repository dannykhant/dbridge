package dbridge.runtime;

import java.util.LinkedHashMap;
import java.util.Set;

public class Record {

    private final LinkedHashMap<String, Object> columns = new LinkedHashMap<>();

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
}
