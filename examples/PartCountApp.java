package dbridge.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Original (iterative) JDBC application exercising all five DBridge rewrite
 * targets:
 *   - statement reordering (loop-var update after the query)
 *   - loop splitting (one query per category)
 *   - query rewrite (scalar aggregate)
 *   - conditional blocks (if isActive ...)
 *   - order-sensitive operations (log() must observe iteration order)
 */
public class PartCountApp {

    static final String URL = "jdbc:h2:mem:dbridge;DB_CLOSE_DELAY=-1";
    static final StringBuilder LOG = new StringBuilder();

    public static void main(String[] args) throws Exception {
        int categories = args.length > 0 ? Integer.parseInt(args[0]) : 50000;

        long t0 = System.nanoTime();
        setup(categories);
        long t1 = System.nanoTime();

        long q0 = System.nanoTime();
        int total = computeTotal(categories - 1);
        long q1 = System.nanoTime();

        System.out.println("RESULT=" + total);
        System.out.println("LOG_HASH=" + LOG.toString().hashCode());
        System.out.println("SETUP_MS=" + (t1 - t0) / 1_000_000);
        System.out.println("QUERY_MS=" + (q1 - q0) / 1_000_000);
    }

    static void setup(int categories) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL);
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE part(partkey INT PRIMARY KEY, category INT)");
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO part VALUES (?, ?)")) {
                for (int cat = 0; cat < categories; cat++) {
                    for (int k = 0; k < 3; k++) {
                        ins.setInt(1, cat * 3 + k);
                        ins.setInt(2, cat);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            st.execute("CREATE INDEX part_cat ON part(category)");
        }
    }

    static boolean isActive(int category) {
        return category % 2 == 0;
    }

    /** Order-sensitive side effect: appends in iteration order. */
    static void log(int category, int partCount) {
        LOG.append(category).append('=').append(partCount).append(',');
    }

    /** Iterative JDBC: one round-trip per active category. */
    public static int computeTotal(int startCategory) throws SQLException {
        Connection con = DriverManager.getConnection(URL);
        PreparedStatement pstmt = con.prepareStatement(
                "SELECT count(partkey) FROM part WHERE category = ?");
        int category = startCategory;
        int total = 0;
        while (category != -1) {
            if (isActive(category)) {
                pstmt.setInt(1, category);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int partCount = rs.getInt(1);
                    total += partCount;
                    log(category, partCount);
                }
            }
            category = category - 1;
        }
        return total;
    }
}
