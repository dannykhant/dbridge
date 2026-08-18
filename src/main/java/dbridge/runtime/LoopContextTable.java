package dbridge.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        addRecord(r);
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
     * Each result row is {@code (aggregate, key, batchOrdinal)}; the aggregate
     * is stored in the matching record as a new "result" column.
     */
    public void mergeResults(DBridgePreparedStatement pstmt) throws SQLException {
        List<Record> byOrdinal = new ArrayList<>(records);
        ResultSet rs = pstmt.getResultSet();
        if (rs == null) {
            return;
        }
        while (rs.next()) {
            Object aggregate = rs.getObject(1);
            long ordinal = rs.getLong(3);
            if (!rs.wasNull() && ordinal >= 0 && ordinal < byOrdinal.size()) {
                Record r = byOrdinal.get((int) ordinal);
                r.set("result", aggregate);
            }
        }
    }
}
