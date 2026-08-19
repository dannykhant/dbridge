package dbridge.runtime;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DBridgeConnection {

    private final Connection connection;

    private DBridgeConnection(Connection connection) {
        this.connection = connection;
    }

    public static DBridgeConnection getConnection(String url) throws SQLException {
        String realUrl = url;
        if (url != null && url.startsWith("dbr:")) {
            realUrl = url.substring("dbr:".length());
        }
        Connection c = DriverManager.getConnection(realUrl);
        return new DBridgeConnection(c);
    }

    public DBridgePreparedStatement prepareStatement(String sql) throws SQLException {
        // The generated set-oriented query references pb, which is created at executeBatch time.
        PreparedStatement ps = sql != null && sql.startsWith("/* dbridge-prebuilt */ ")
                ? connection.prepareStatement("SELECT ?")
                : connection.prepareStatement(sql);
        return new DBridgePreparedStatement(ps, sql);
    }

    public void close() throws SQLException {
        connection.close();
    }
}
