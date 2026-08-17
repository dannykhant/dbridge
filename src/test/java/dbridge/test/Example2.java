package dbridge.test;

import dbridge.runtime.DBridgeConnection;
import dbridge.runtime.DBridgePreparedStatement;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Hand-written equivalent of what DBridge produces for Example1 (batched JDBC).
 */
public class Example2 {

    public static int getTotalPartCount(int startCategory) throws SQLException {
        DBridgeConnection con = DBridgeConnection.getConnection("dbr:jdbc:h2:mem:dbridge");
        DBridgePreparedStatement pstmt = con.prepareStatement("SELECT count(partkey) FROM part WHERE category = ?");
        int category = startCategory;
        int total = 0;
        while (category != -1) {
            pstmt.setInt(1, category);
            pstmt.addBatch();
            category = getParent(category);
        }
        pstmt.executeBatch();
        while (pstmt.getMoreResults()) {
            ResultSet rs = pstmt.getResultSet();
            if (rs.next()) {
                int partCount = rs.getInt(1);
                total += partCount;
            }
        }
        return total;
    }

    public static int getParent(int category) {
        return category - 1;
    }
}
