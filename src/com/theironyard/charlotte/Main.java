package com.theironyard.charlotte;

import com.sun.org.apache.xpath.internal.operations.Or;
import org.h2.command.Prepared;
import org.h2.engine.Mode;
import org.h2.tools.Server;
import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

    public class Main {
        private static Connection getConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:h2:./main");
        }

        private static void initializeDatabase() throws SQLException {
            Statement stmt = getConnection().createStatement();
            stmt.execute("create table if not exists users  (id identity, name varchar, email varchar)");
            stmt.execute("create table if not exists orders (id identity, user_id int, complete boolean)");
            stmt.execute("create table if not exists items  (id identity, name varchar, quantity int, price double, order_id int)");
        }

        public static ArrayList<User> selectUser (Connection conn) throws SQLException {
        ArrayList<User> users = new ArrayList<>();
        Statement stmt = conn.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM users");
        while (results.next()) {
            int id = results.getInt("id");
            String name = results.getString("name");
            String email = results.getString("email");
            users.add(new User(id, name, email));
        }
        return users;
    }

        private static List<Order> getOrdersForUser(Integer userId) throws SQLException {
            List<Order> orderList = new ArrayList<>();
            PreparedStatement stmt = getConnection().prepareStatement("select * from orders where user_id = ?");

            stmt.setInt(1, userId);
            ResultSet results = stmt.executeQuery();
            while (results.next()) {
                orderList.add(new Order(results.getInt("id"), results.getInt("user_id"), results.getBoolean("complete")));
            }
            return orderList;
        }

        private static List<Item> getItemsForOrder(Integer orderId) throws SQLException {
            List<Item> itemList = new ArrayList<>();
            PreparedStatement stmt = getConnection().prepareStatement("SELECT * FROM items where order_id = ?");
            stmt.setInt(1, orderId);
            ResultSet results = stmt.executeQuery();
            while(results.next()) {
                itemList.add(new Item(results.getString("name"), results.getInt("quantity"), results.getDouble("price"), results.getInt("order_id")));
            }
            return itemList;
        }

        public static User insertUser(String name, String email) throws SQLException{
        User user = new User(name, email);
        PreparedStatement stmt = getConnection().prepareStatement("INSERT INTO users VALUES (null, ?, ?)");
        stmt.setString(1, name);
        stmt.setString(2, email);
        stmt.execute();
        return user;
    }

        private static User getUserById(Integer id) throws SQLException {
            User user = null;

            if (id != null) {
                PreparedStatement stmt = getConnection().prepareStatement("select * from users where id = ?");

                stmt.setInt(1, id);
                ResultSet results = stmt.executeQuery();

                if (results.next()) {
                    user = new User(id, results.getString("name"), results.getString("email"));

                    user.setOrders(getOrdersForUser(id));
                }
            }

            return user;
        }

        private static Integer getUserIdByEmail(String email) throws SQLException {
            Integer userId = null;

            if (email != null) {
                PreparedStatement stmt = getConnection().prepareStatement("select * from users where email = ?");
                stmt.setString(1, email);

                ResultSet results = stmt.executeQuery();

                if (results.next()) {
                    userId = results.getInt("id");
                }
            }

            return userId;
        }

        private static Order getLatestCurrentOrder(Integer userId) throws SQLException {
            Order order = null;

            if (userId != null) {
                PreparedStatement stmt = getConnection().prepareStatement("select top 1 * from orders where user_id = ? and complete = false");
                stmt.setInt(1, userId);

                ResultSet results = stmt.executeQuery();

                if (results.next()) {
                    order = new Order(results.getInt("id"), results.getInt("user_id"), results.getBoolean("complete"));
                }
            }

            return order;
        }

        private static int insertOrder(int userId) throws SQLException {
            PreparedStatement stmt = getConnection().prepareStatement("insert into orders values (NULL, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setBoolean(2, false);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();

            keys.next();

            return keys.getInt(1);
        }

        private static int insertItem(Item item) throws SQLException{
            PreparedStatement stmt = getConnection().prepareStatement("INSERT INTO items VALUES(null, ?, ?, ?, ?)");
            stmt.setString(1, item.getName());
            stmt.setInt(2, item.getQuantity());
            stmt.setDouble(3, item.getPrice());
            stmt.setInt(4, item.getOrderId());
            stmt.execute();

            return item.getId();
        }

        public static void main(String[] args) throws SQLException {
            Server.createWebServer().start();

            Spark.post("/items", (request, response) -> {
                Session session = request.session();

                User current = getUserById(session.attribute("fancy_user_id"));

                if (current != null) {
                    // see if there is a current order
                    Order currentOrder = getLatestCurrentOrder(current.getId());

                    if (currentOrder == null) {
                        // if not, make a new one
                        int orderId = insertOrder(current.getId());

                        // get item from post data
                        Item postedItem = new Item(request.queryParams("name"), Integer.valueOf(request.queryParams("quantity")), Double.valueOf("price"), orderId);

                        // add item to order
                        insertItem(postedItem);
                    }
                }

                // redirect
                response.redirect("/");
                return "";
            });

            Spark.get("/", (request, response) -> {
                HashMap model = new HashMap();
                Session session = request.session();

                User current = getUserById(session.attribute("fancy_user_id"));

                if (current != null) {
                    // pass user into model
                    model.put("user", current);
                    model.put("order_id", insertOrder(current.getId()));
                    model.put("items", getItemsForOrder(current.getId()));

                session.attribute("orderid", insertOrder(current.getId()));

                    return new ModelAndView(model, "index.html");
                } else {
                    return new ModelAndView(model, "login.html");
                }
            }, new MustacheTemplateEngine());

            Spark.post("/login", (request, response) -> {
                String email = request.queryParams("email");

                // look up the user by email address
                Integer userId = getUserIdByEmail(email);

                // if the user exists, save the id in session.
                if (userId != null) {
                    Session session = request.session();
                    session.attribute("fancy_user_id", userId);
                }
                response.redirect("/");
                return "";
            });

            Spark.get("/create-user", ((req, res) -> {
            HashMap m = new HashMap();
            m.put("users", selectUser(getConnection()));
            return new ModelAndView(m, "register.html");
        }), new MustacheTemplateEngine());

            Spark.post("/create-user", (req, res) -> {
            String name = req.queryParams("name");
            String email = req.queryParams("email");
            insertUser(name, email);

            res.redirect("/");
            return "";
        });

            initializeDatabase();
        }
    }


//    public static void main(String[] args) throws SQLException{
//
//        Server.createWebServer().start();
//        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
//        Statement stmt = conn.createStatement();
//        stmt.execute("CREATE TABLE IF NOT EXISTS users (id IDENTITY, name VARCHAR, userName VARCHAR, password VARCHAR, email VARCHAR, streetAddress VARCHAR, city VARCHAR, " +
//                "state VARCHAR, zipCode INTEGER, phoneNumber VARCHAR)");
//
//        stmt.execute("CREATE TABLE IF NOT EXISTS items (id IDENTITY, order_id INT, name VARCHAR, quantity INT, price DECIMAl(20,2))");
//
//        stmt.execute("CREATE TABLE IF NOT EXISTS orders (id IDENTITY, user_id INT)");
//
////        stmt.execute("SELECT * FROM users ORDER BY ID");
//
//        Spark.get("/", ((request, response) -> {
//            HashMap m = new HashMap();
//            String userName = request.session().attribute("userName");
////            Integer orderId = request.session().attribute("orderId");
//
//             if (userName == null) {
//                 return new ModelAndView(m, "login.html");
//             } else {
//                 m.put("userName", userName);
//                 return new ModelAndView(m, "index.html");
//             }
//        }), new MustacheTemplateEngine());
//
//        Spark.get("/create-user", ((req, res) -> {
//            HashMap m = new HashMap();
//            m.put("users", selectUser(conn));
//            return new ModelAndView(m, "register.html");
//        }), new MustacheTemplateEngine());
//
//        Spark.post("/create-user", (req, res) -> {
//            String name = req.queryParams("name");
//            String userName = req.queryParams("userName");
//            String password = req.queryParams("password");
//            String email = req.queryParams("email");
//            String streetAddress = req.queryParams("streetAddress");
//            String city = req.queryParams("city");
//            String state = req.queryParams("state");
//            int zipCode = Integer.valueOf(req.queryParams("zipCode"));
//            String phoneNumber = req.queryParams("phoneNumber");
////            User newUser = insertUser(conn, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber);
//
////            newUser.setId(Integer.valueOf(req.queryParams("id")));
////            newUser.setName(req.queryParams("name"));
//
//            insertUser(conn, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber);
//
//            res.redirect("/");
//            return "";
//        });
//
//        Spark.post("/login",((req, res)->{
//            HashMap m = new HashMap();
//            m.put("users", selectUser(conn));
//            Integer id = null;
//            String userName = req.queryParams("userName");
//            String password = req.queryParams("password");
//            Integer userId = loginUser(conn, userName, password, id);
////            User current = setUserByUserName(conn, userName);
//
//            if (userId != null) {
//                req.session().attribute("userName", userName);
//                req.session().attribute("userId", id);
//                res.redirect("/");
//                return "";
//            } else {
//                res.redirect("login-error.html");
//                return "";
//            }
//        }));
//
//        Spark.get("/login-error.html", ((req, res) -> {
//            HashMap m = new HashMap();
//            m.put("users", selectUser(conn));
//            return new ModelAndView(m, "login-error.html");
//        }), new MustacheTemplateEngine());
//
//        Spark.post("/create-item", ((req, res) -> {
//            Integer orderId = req.session().attribute("orderId");//Integer.valueOf(req.queryParams("orderId"));
//            String name = req.queryParams("name");
//            int quantity = Integer.valueOf(req.queryParams("quantity"));
//            BigDecimal price = BigDecimal.valueOf(Double.valueOf(req.queryParams("price")));
//            insertItem(conn, orderId, name, quantity, price);
//            return "";
//        }));
//
//        Spark.post("/create-order", ((req, res) -> {
//            Integer userId = req.session().attribute("userName");
//            return "";
//        }));
////
////        Spark.post("create-order", ((req, res) -> {
////            String
////        }));
//    }
//
//    public static ArrayList<User> selectUser (Connection conn) throws SQLException {
//        ArrayList<User> users = new ArrayList<>();
//        Statement stmt = conn.createStatement();
//        ResultSet results = stmt.executeQuery("SELECT * FROM users");
//        while (results.next()) {
//            int id = results.getInt("id");
//            String name = results.getString("name");
//            String userName = results.getString("userName");
//            String password = results.getString("password");
//            String email = results.getString("email");
//            String streetAddress = results.getString("streetAddress");
//            String city = results.getString("city");
//            String state = results.getString("state");
//            int zipCode = results.getInt("zipCode");
//            String phoneNumber = results.getString("phoneNumber");
//            users.add(new User(id, name, userName, password, email, streetAddress, city, state, zipCode, phoneNumber));
//        }
//        return users;
//    }
//
//    public static User insertUser(Connection conn, String name, String userName, String password, String email,
//                                  String streetAddress, String city, String state, int zipCode, String phoneNumber) throws SQLException{
//        User user = new User();
//        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (null, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
//        stmt.setString(1, name);
//        stmt.setString(2, userName);
//        stmt.setString(3, password);
//        stmt.setString(4, email);
//        stmt.setString(5, streetAddress);
//        stmt.setString(6, city);
//        stmt.setString(7, state);
//        stmt.setInt(8, zipCode);
//        stmt.setString(9, phoneNumber);
//        stmt.execute();
//        return user;
//    }
//
//    public static Integer loginUser(Connection conn, String userName, String password, Integer id) throws SQLException {
//        id = null;
//        PreparedStatement stmt = conn.prepareStatement("SELECT password, id FROM users WHERE username= ?");
//        stmt.setString(1, userName);
//        ResultSet result = stmt.executeQuery();
//        while (result.next()) {
//            if (result.getString("password").equals(password)) {
//                id = result.getInt("id");
//            }
//        }
//        return id;
//    }
//
//    public static Item insertItem (Connection conn, int orderId,String name, int quantity, BigDecimal price) throws SQLException {
//        PreparedStatement stmt = conn.prepareStatement("INSERT INTO items VALUES(null, ?, ?, ?, ?)");
//        stmt.setInt(1,orderId);
//        stmt.setString(2, name);
//        stmt.setInt(3, quantity);
//        stmt.setBigDecimal(4, price);
//        stmt.execute();
//        return null;
//    }

//    public static Order selectOrder(Connection conn, int id) throws SQLException {
//        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM orders INNER JOIN users ON orders.user_id = users.id WHERE orders.id = ?");
//        stmt.setInt(1, id);
//        ResultSet results = stmt.executeQuery();
//        if (results.next()) {
//        }
//
//    }
//
//    public static Item selectItem(Connection conn, int id) throws SQLException {
//        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM items INNER JOIN orders ON items.order_id = orders.id WHERE items.id = ?");
//
//    }
//    public static List<Order> getOrdersForUser(Connection conn, Integer userId) throws SQLException {
//        ArrayList<Order> orders = new ArrayList<>();
//        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM orders where user_id = ?");
//        stmt.setInt(1, userId);
//        ResultSet results = stmt.executeQuery();
//
//        if (results.next()) {
//            int orderId = results.getInt("id");
//            orders = new Order(userId,orderId);
//        }
//    }

