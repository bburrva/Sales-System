package com.theironyard.charlotte;

import org.h2.engine.Mode;
import org.h2.tools.Server;
import spark.ModelAndView;
import spark.Session;
import spark.Spark;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {
    static User user;

    public static void main(String[] args) throws SQLException{

        Server.createWebServer().start();
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id IDENTITY, name VARCHAR, userName VARCHAR, password VARCHAR, email VARCHAR, streetAddress VARCHAR, city VARCHAR, " +
                "state VARCHAR, zipCode INTEGER, phoneNumber VARCHAR)");

//        stmt.execute("SELECT * FROM users ORDER BY ID");

        Spark.get("/", (request, response) -> {
            HashMap m = new HashMap();
            m.put("users", selectUser(conn));

             if (user==null) {
                 return new ModelAndView(m, "login.html");
             } else {
                 return new ModelAndView(m, "index.html");
             }
        });

        Spark.post("/create-user", (req, res) -> {
            String name = req.queryParams("name");
            String userName = req.queryParams("userName");
            String password = req.queryParams("password");
            String email = req.queryParams("email");
            String streetAddress = req.queryParams("streetAddress");
            String city = req.queryParams("city");
            String state = req.queryParams("state");
            int zipCode = Integer.valueOf(req.queryParams("zipCode"));
            String phoneNumber = req.queryParams("phoneNumber");
            User newUser = insertUser(conn, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber);

            newUser.setId(Integer.valueOf(req.queryParams("id")));
            newUser.setName(req.queryParams("name"));

            insertUser(conn, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber);

            res.redirect("/");
            return "";
        });

        Spark.post("/login",(req, res)->{
            String message = "";
            String userName = req.queryParams("userName");
            String password = req.queryParams("password");
            loginUser(conn, userName, password);
            String[] result = loginUser(conn, userName, password);


            if (result[0].equals("1")) {
                return new ModelAndView(result, "index.html");
            } else {
                req.session().attribute("Message");
            }
            return "";
        });
    }

    public static ArrayList<User> selectUser (Connection conn) throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        Statement stmt = conn.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM users");
        while (results.next()) {
            int id = results.getInt("id");
            String name = results.getString("name");
            String userName = results.getString("userName");
            String password = results.getString("password");
            String email = results.getString("email");
            String streetAddress = results.getString("streetAddress");
            String city = results.getString("city");
            String state = results.getString("state");
            int zipCode = results.getInt("zipCode");
            String phoneNumber = results.getString("phoneNumber");
            users.add(new User(id, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber));
        }
        return users;
    }

    public static User insertUser(Connection conn, String name, String userName, String password, String email,
                                  String streetAddress, String city, String state, int zipCode, String phoneNumber) throws SQLException{
        User user = new User();
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        stmt.setString(1, name);
        stmt.setString(2, userName);
        stmt.setString(3, password);
        stmt.setString(4, email);
        stmt.setString(5, streetAddress);
        stmt.setString(6, city);
        stmt.setString(7, state);
        stmt.setInt(8, zipCode);
        stmt.setString(9, phoneNumber);
        stmt.execute();
        return user;
    }

    public static String[] loginUser(Connection conn, String userName, String password) throws SQLException {

        String message = "Username or password incorrect";
        String verify = "0";
        PreparedStatement stmt = conn.prepareStatement("SELECT userName, password FROM users WHERE username=?");
//        Statement stmt = conn.createStatement();
        stmt.setString(1, userName);
        ResultSet result = stmt.executeQuery();
        while (result.next()) {
            if (result.getString("password") == password) {
                verify = "1";
                message = "";
            } else if (result.getString("password") != password) {
                verify = "0";
            }
        }
        return new String[] {verify, message};
    }
}
