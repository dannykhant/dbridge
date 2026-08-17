package dbridge.runtime;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LoopContextTable implements Iterable<Record> {

    private final List<Record> records = new ArrayList<>();

    public void addRecord(Record r) {
        records.add(r);
    }

    public int size() {
        return records.size();
    }

    @Override
    public Iterator<Record> iterator() {
        return records.iterator();
    }

    public void mergeResults(DBridgePreparedStatement pstmt) throws SQLException {
        Iterator<Record> it = records.iterator();
        while (pstmt.getMoreResults()) {
            ResultSet rs = pstmt.getResultSet();
            if (rs == null || !it.hasNext()) {
                break;
            }
            Record record = it.next();
            if (!rs.next()) {
                continue;
            }
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                setColumn(record, md.getColumnLabel(i), rs.getObject(i));
            }
        }
    }

    private void setColumn(Record record, String label, Object value) {
        for (String existing : record.columns()) {
            if (existing.equalsIgnoreCase(label)) {
                record.set(existing, value);
                return;
            }
        }
        record.set(label, value);
    }
}
