package dbridge.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Example1 {

    public static int getTotalPartCount(int startCategory) throws SQLException {
        Connection con = DriverManager.getConnection("jdbc:h2:mem:x");
        PreparedStatement pstmt = con.prepareStatement("SELECT count(partkey) FROM part WHERE category = ?");
        int category = startCategory;
        int total = 0;
        while (category != -1) {
            pstmt.setInt(1, category);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int partCount = rs.getInt(1);
                total += partCount;
            }
            category = getParent(category);
        }
        return total;
    }

    public static int getParent(int category) {
        return category - 1;
    }
}
