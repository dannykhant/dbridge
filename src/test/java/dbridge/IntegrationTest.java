package dbridge;

import dbridge.test.Example1;
import dbridge.test.Example2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end semantic-preservation test: the original iterative JDBC program
 * and the batched (set-oriented) program must compute the same result.
 */
public class IntegrationTest {

    @BeforeAll
    static void loadDriver() throws Exception {
        Class.forName("org.h2.Driver");
    }

    private static void setup(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE part(partkey INT, category INT)");
            st.execute("INSERT INTO part VALUES (10, 1), (20, 1), (30, 2), (40, 3)");
        }
    }

    @Test
    public void originalAndTransformedAgree() throws Exception {
        try (Connection c1 = DriverManager.getConnection("jdbc:h2:mem:x");
             Connection c2 = DriverManager.getConnection("jdbc:h2:mem:dbridge")) {
            setup(c1);
            setup(c2);

            int original = Example1.getTotalPartCount(3);
            int transformed = Example2.getTotalPartCount(3);

            assertEquals(original, transformed, "original and transformed must agree");
            assertEquals(4, original);
        }
    }
}
