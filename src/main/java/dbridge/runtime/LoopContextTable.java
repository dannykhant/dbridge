package dbridge.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An ordered table of loop-context {@link Record}s, iterated in the order the
 * records were produced so that order-sensitive operations remain correct.
 */
public class LoopContextTable implements Iterable<Record> {

    private final List<Record> records = new ArrayList<>();

    public void addRecord(Record r) {
        records.add(r);
    }

    /** Convenience for the transformed code: (loopKey, flag, value). */
    public void addRecord(int key, boolean flag, int value) {
        Record r = new Record();
        r.set("c0", key);
        r.set("c1", flag);
        r.set("c2", value);
        records.add(r);
    }

    public int size() {
        return records.size();
    }

    @Override
    public Iterator<Record> iterator() {
        return records.iterator();
    }

    /**
     * Augment each record with the set-oriented query result for its loop key.
     * Each result row is {@code (aggregate, key)}; the aggregate is stored in
     * the record as a new "result" column.
     */
    public void mergeResults(DBridgePreparedStatement pstmt) throws SQLException {
        Map<Object, Record> byKey = new HashMap<>();
        for (Record r : records) {
            Object key = r.get(0);
            if (key != null) {
                byKey.put(key, r);
            }
        }
        ResultSet rs = pstmt.getResultSet();
        if (rs == null) {
            return;
        }
        while (rs.next()) {
            Object aggregate = rs.getObject(1);
            Object key = rs.getObject(2);
            Record r = byKey.get(key);
            if (r != null) {
                r.set("result", aggregate);
            }
        }
    }
}
