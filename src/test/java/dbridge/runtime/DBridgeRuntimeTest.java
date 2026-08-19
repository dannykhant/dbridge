package dbridge.runtime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DBridgeRuntimeTest {

    @BeforeAll
    static void loadDriver() throws Exception {
        Class.forName("org.h2.Driver");
    }

    private static void createPartTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE part(partkey INT, category INT)");
        }
    }

    @Test
    public void scalarAggregateBatchReturnsCountsPerBinding() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_select")) {
            createPartTable(setup);
            try (Statement st = setup.createStatement()) {
                st.execute("INSERT INTO part VALUES (10, 1), (20, 1), (30, 2), (40, 3)");
            }

            DBridgeConnection dbc = DBridgeConnection.getConnection("dbr:jdbc:h2:mem:testdb_select");
            DBridgePreparedStatement pstmt = dbc.prepareStatement(
                    "SELECT count(partkey) FROM part WHERE category = ?");
            int[] cats = {1, 2, 3, 4};
            for (int c : cats) {
                pstmt.setInt(1, c);
                pstmt.addBatch();
            }
            pstmt.executeBatch();

            Map<Integer, Long> counts = new HashMap<>();
            ResultSet rs = pstmt.getResultSet();
            while (rs.next()) {
                counts.put(rs.getInt(2), rs.getLong(1));
            }
            assertEquals(2L, counts.get(1));
            assertEquals(1L, counts.get(2));
            assertEquals(1L, counts.get(3));
            assertEquals(0L, counts.get(4));
            dbc.close();
        }
    }

    @Test
    public void prebuiltSetQueryExecutesWithoutRuntimeRewrite() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_prebuilt")) {
            createPartTable(setup);
            try (Statement st = setup.createStatement()) {
                st.execute("INSERT INTO part VALUES (10, 1), (20, 1), (30, 2)");
            }

            DBridgeConnection dbc = DBridgeConnection.getConnection("dbr:jdbc:h2:mem:testdb_prebuilt");
            DBridgePreparedStatement pstmt = dbc.prepareStatement(
                    "/* dbridge-prebuilt */ SELECT count(partkey), pb.category AS category, "
                            + "pb.batch_ordinal FROM pb LEFT JOIN part ON pb.category = part.category "
                            + "GROUP BY pb.batch_ordinal, pb.category");
            pstmt.setInt(1, 1);
            pstmt.addBatch();
            pstmt.setInt(1, 2);
            pstmt.addBatch();
            pstmt.executeBatch();

            ResultSet rs = pstmt.getResultSet();
            assertTrue(rs.next());
            assertEquals(2L, rs.getLong(1));
            assertTrue(rs.next());
            assertEquals(1L, rs.getLong(1));
            assertTrue(!rs.next());
            dbc.close();
        }
    }

    @Test
    public void mergeResultsCopiesResultsIntoRecordsInOrder() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_merge")) {
            createPartTable(setup);
            try (Statement st = setup.createStatement()) {
                st.execute("INSERT INTO part VALUES (10, 1), (20, 1), (30, 2), (40, 3)");
            }

            DBridgeConnection dbc = DBridgeConnection.getConnection("jdbc:h2:mem:testdb_merge");
            DBridgePreparedStatement pstmt = dbc.prepareStatement(
                    "SELECT count(partkey) FROM part WHERE category = ?");
            LoopContextTable ctx = new LoopContextTable();
            int[] cats = {1, 1, 2};
            long[] expected = {2L, 2L, 1L};
            for (int c : cats) {
                pstmt.bind(1, c);
                pstmt.addBatch();
                Record r = new Record();
                r.set("category", c);
                ctx.addRecord(r);
            }
            pstmt.executeBatch();
            ctx.mergeResults(pstmt);

            int idx = 0;
            for (Record r : ctx) {
                assertEquals(cats[idx], ((Number) r.get("category")).intValue());
                assertEquals(expected[idx], ((Number) r.get(1)).longValue());
                idx++;
            }
            assertEquals(3, ctx.size());

            pstmt.bind(1, 3);
            pstmt.addBatch();
            pstmt.executeBatch();
            ResultSet secondResult = pstmt.getResultSet();
            assertTrue(secondResult.next());
            assertEquals(1L, secondResult.getLong(1));
            assertEquals(3, secondResult.getInt(2));
            assertTrue(!secondResult.next());
            dbc.close();
        }
    }

    @Test
    public void nativeInsertBatchInsertsAllRows() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_insert")) {
            createPartTable(setup);

            DBridgeConnection dbc = DBridgeConnection.getConnection("jdbc:h2:mem:testdb_insert");
            DBridgePreparedStatement pstmt = dbc.prepareStatement("INSERT INTO part VALUES (?, ?)");
            int[][] rows = {{100, 1}, {101, 1}, {102, 2}};
            for (int[] r : rows) {
                pstmt.setInt(1, r[0]);
                pstmt.setInt(2, r[1]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();

            try (Statement st = setup.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM part")) {
                rs.next();
                assertEquals(3, rs.getInt(1));
            }
            dbc.close();
        }
    }

    @Test
    public void emptyScalarBatchReturnsAnEmptyResultSet() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_empty")) {
            createPartTable(setup);
            DBridgeConnection dbc = DBridgeConnection.getConnection("dbr:jdbc:h2:mem:testdb_empty");
            DBridgePreparedStatement pstmt = dbc.prepareStatement(
                    "SELECT count(partkey) FROM part WHERE category = ?");
            pstmt.executeBatch();
            assertTrue(pstmt.getResultSet() != null);
            assertTrue(!pstmt.getResultSet().next());
            dbc.close();
        }
    }

    @Test
    public void generalQueryFallbackPreservesBatchOrdinals() throws Exception {
        try (Connection setup = DriverManager.getConnection("jdbc:h2:mem:testdb_general")) {
            try (Statement st = setup.createStatement()) {
                st.execute("CREATE TABLE customer(id INT, region VARCHAR(20))");
                st.execute("CREATE TABLE orders(id INT, customer_id INT, status VARCHAR(20))");
                st.execute("INSERT INTO customer VALUES (1, 'north'), (2, 'south')");
                st.execute("INSERT INTO orders VALUES (10, 1, 'open'), (13, 2, 'open')");
            }
            DBridgeConnection dbc = DBridgeConnection.getConnection("dbr:jdbc:h2:mem:testdb_general");
            DBridgePreparedStatement pstmt = dbc.prepareStatement(
                    "SELECT o.id, c.region FROM customer c JOIN orders o "
                            + "ON c.id = o.customer_id WHERE c.region = ? AND o.status = ?");
            pstmt.setString(1, "north");
            pstmt.setString(2, "open");
            pstmt.addBatch();
            pstmt.setString(1, "south");
            pstmt.setString(2, "open");
            pstmt.addBatch();
            pstmt.executeBatch();
            ResultSet rs = pstmt.getResultSet();
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1));
            assertEquals(0, rs.getLong(3));
            assertTrue(rs.next());
            assertEquals(13, rs.getInt(1));
            assertEquals(1, rs.getLong(3));
            assertTrue(!rs.next());
            dbc.close();
        }
    }
}
