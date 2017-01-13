package com.theironyard.charlotte;

import org.h2.tools.Server;
import spark.ModelAndView;
import spark.Spark;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {

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
            return new ModelAndView(m,"index.html");
        });
    }

    public static ArrayList<User> selectUser (Connection conn) throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        Statement stmt = conn.createStatement();
        ResultSet results = stmt.executeQuery("SELCT * FROM users");
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
}
