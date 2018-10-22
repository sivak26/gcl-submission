package org.usagreencardlottery.utils.db;

import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * Generic class for mysql connections to be used by stand alone java
 * applications.
 */
public class DBConnector {

    private String host = "localhost";
    private String database = "bp";
    private String userName = "root";
    private String password = "";
    private boolean driverRegistered = false;

    /**
     * The constructor. All it does is just register the jdbc driver. If
     * registration succeeds then sets a flag.
     *
     */
    public DBConnector() {
        try {
            Class.forName("org.gjt.mm.mysql.Driver").newInstance();
            driverRegistered = true;
        } catch (Exception e) {
            System.err.println("Error registering driver....");
        }
    }

    /**
     * Access methods for class private info
     */
    public String getHost() {
        return this.host;
    }

    public String getDatabase() {
        return this.database;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getPassword() {
        return this.password;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean loadConfig(String configFile) {
        boolean loaded = false;
        Properties config = new Properties();
        FileInputStream configStream = null;
        try {
            configStream = new FileInputStream(configFile);
            config.load(configStream);
            loaded = true;
        } catch (Exception e) {
            System.err.println("Error while reading config file : " + configFile + " -> " + e.getMessage());
        } finally {
            if (configStream != null) {
                try {
                    configStream.close();
                } catch (Exception e) {
                    System.err.println("Error closing the file : " + configFile);
                }
            }
            //listProperties(config);
        }

        if (config.get("host") != null) {
            setHost((String) config.get("host"));
        }
        if (config.get("password") != null) {
            setPassword((String) config.get("password"));
        }
        if (config.get("database") != null) {
            setDatabase((String) config.get("database"));
        }
        if (config.get("username") != null) {
            setUserName((String) config.get("username"));
        }
        return loaded;
    }

    public void listProperties(Properties container) {
        Enumeration keys = container.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            System.err.println(key + " = " + container.get(key));
        }
    }

    /**
     * Create a new connection.
     */
    public Connection getConnection() {
        Connection conn = null;
        if (driverRegistered) {
            try {
                conn = DriverManager.getConnection("jdbc:mysql://" + host + "/" + database + "?user=" + userName + "&password=" + password);
            } catch (Exception e) {
                conn = null;
                System.err.println("Error while creating connection : " + e.getMessage());
            }
        }
        return conn;
    }

    /**
     * Close the connection passed as parameter.
     *
     * @param conn The connection object holding the connection.
     */
    public void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                System.err.println("Error while closing connection : " + e.getMessage());
            }
        } else {
            System.err.println("Request to close a null connection ignored...");
        }
    }

    /**
     * Close the result set passed as parameter if not null.
     *
     * @param rs The Result set object
     */
    public void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                System.err.println("Error while closing result set : " + e.getMessage());
            }
        } else {
            System.err.println("Request to close a null result set ignored...");
        }
    }

    /**
     * Close the Statement passed as parameter.
     *
     * @param stmt The statement object to be closed
     */
    public void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (Exception e) {
                System.err.println("Error while closing statment : " + e.getMessage());
            }
        } else {
            System.err.println("Request to close a null statment ignored...");
        }
    }
}
