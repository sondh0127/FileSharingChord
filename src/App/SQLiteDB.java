package App;

import App.Components.SharedFile;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDB {

    private static String myDb = "mySharedFiles.db";

    public SQLiteDB() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS FILE (" +
                    "USER_IP     TEXT        NOT NULL," +
                    "PORT        INT         NOT NULL," +
                    "FILE_ID     LONG        DEFAULT -1," +
                    "TITLE       TEXT        NOT NULL, " +
                    "AUTHOR      TEXT        NOT NULL, " +
                    "LOCATION    TEXT        NOT NULL," +
                    "SHARED      INTEGER     DEFAULT 0," +
                    "PRIMARY KEY (USER_IP, PORT, LOCATION)" +
                    ");";
            stmt.executeUpdate(sql);
            stmt.close();
            c.close();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public void addNewFile(SharedFile newSharedFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();
            String sql = "INSERT INTO FILE (USER_IP,PORT,FILE_ID,TITLE,AUTHOR,LOCATION,SHARED) VALUES ('" +
                            newSharedFile.getOwnerAddress().getAddress().getHostAddress() + "', '" +
                            newSharedFile.getOwnerAddress().getPort() + "', '" +
                            newSharedFile.getId() + "', '" +
                            newSharedFile.getTitle() + "', '" +
                            newSharedFile.getAuthor() + "', '" +
                            newSharedFile.getLocation() + "', '" +
                            "1');";
            int status = stmt.executeUpdate(sql);
            if (status > 0) {
                System.out.println("Inserted successfully");
            } else {
                System.out.println("Can't insert to the db");
            }
            stmt.close();
            c.commit();
            c.close();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public List<SharedFile> getAllMyFiles(InetSocketAddress myaddress) {
        List<SharedFile> sharedFileList = new ArrayList();

        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();
            String sql = "SELECT * FROM FILE WHERE USER_IP='" + myaddress.getAddress().getHostAddress() +
                            "' AND PORT='" + myaddress.getPort() + "';";
            ResultSet rs = stmt.executeQuery( sql);

            while ( rs.next() ) {
                String  title = rs.getString("TITLE");
                String author  = rs.getString("AUTHOR");
                String location = rs.getString("LOCATION");
                int sharedValue = rs.getInt("SHARED");

                long fileId = -1;
                Boolean isShared = false;
                if (sharedValue > 0) {
                    isShared = true;
                    fileId = rs.getLong("FILE_ID");
                }
                System.out.println("SQLDB fileId=" + fileId + " sharedValue=" + sharedValue + " ,isShared=" + isShared);
                SharedFile newSharedFile = new SharedFile(fileId, myaddress, title, author, location, isShared);
                sharedFileList.add(newSharedFile);
            }

            stmt.close();
            c.commit();
            c.close();
            System.out.println("Successfully get all files");
            return sharedFileList;
        } catch ( Exception e ) {
            e.printStackTrace();
            return sharedFileList;
        }
    }

    public void updateFileShareStatus(SharedFile sharedFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            int shared = 0;
            if (sharedFile.getIsShared()) { shared = 1; }
            Statement stmt = c.createStatement();
            String sql = "UPDATE FILE SET SHARED='" + shared +
                            "', FILE_ID='" + sharedFile.getId() +
                            "' WHERE USER_IP='" + sharedFile.getOwnerAddress().getAddress().getHostAddress() +
                            "' AND PORT='" + sharedFile.getOwnerAddress().getPort() +
                            "' AND LOCATION='" + sharedFile.getLocation() + "';";
            int status = stmt.executeUpdate(sql);

            stmt.close();
            c.commit();
            c.close();
            System.out.println(sql);
            System.out.println("Updated sharedFile's share status successfully (" + status + "): " + sharedFile.getId() + ", " + sharedFile.getTitle() + ", " + sharedFile.getLocation());
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public void unshareAllFiles(InetSocketAddress address) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();
            String sql = "UPDATE FILE SET SHARED='0' " +
                        "WHERE USER_IP='" + address.getAddress().getHostAddress() +
                        "' AND PORT='" + address.getPort() +"';";
            stmt.executeUpdate(sql);

            stmt.close();
            c.commit();
            c.close();
            System.out.println("Unshared all files successfully");
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    public boolean updateFileLocation(SharedFile sharedFile, SharedFile newSharedFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();

            int shared = 0;
            if (sharedFile.getIsShared()) { shared = 1; }
            String sql = "UPDATE FILE SET LOCATION='" + newSharedFile.getLocation() +
                            "', FILE_ID='" + newSharedFile.getId() +
                            "', SHARED='" + shared +
                            "' WHERE USER_IP='" + sharedFile.getOwnerAddress().getAddress().getHostAddress() +
                            "' AND PORT='" + sharedFile.getOwnerAddress().getPort() +
                            "' AND LOCATION='" + sharedFile.getLocation() + "';";
            int status = stmt.executeUpdate(sql);

            stmt.close();
            c.commit();
            c.close();

            if (status > 0) {
                System.out.println("Updated sharedFile's location successfully");
                return true;
            } else {
                System.out.println("Unsuccessful Updated sharedFile's location");
                return false;
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkIfFileExists(InetSocketAddress address, String location) {
        try {
            System.out.println("Checking if SharedFile exists in the DB: " + address.getAddress().getHostAddress() + ":" + address.getPort() + ", " + location);
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + myDb);
            c.setAutoCommit(false);
//            System.out.println("Opened database successfully");

            Statement stmt = c.createStatement();

            String sql = "SELECT * FROM FILE WHERE USER_IP='" + address.getAddress().getHostAddress() +
                    "' AND PORT='" + address.getPort() +
                    "' AND LOCATION='" + location + "';";
            ResultSet rs = stmt.executeQuery(sql);

            boolean status = rs.next();
            stmt.close();
            c.commit();
            c.close();

//            System.out.println(status);
            if (status) {
                System.out.println("SharedFile exists");
                return true;
            } else {
                System.out.println("SharedFile doesn't exist");
                return false;
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            return true;
        }
    }
}
