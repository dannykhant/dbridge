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
}
