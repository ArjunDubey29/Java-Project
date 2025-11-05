package shopingcartapp;

import java.sql.*;
import java.util.Scanner;

/**
 * ShopingCartApp.java
 *
 * Console-based shopping cart application with user and admin sides.
 * - User: view items, place order, view cart, logout
 * - Admin: view/add/update/delete items, view all orders, view users, logout
 *
 * Features:
 * - JDBC connection (MySQL)
 * - Robust admin login: uses DB role='admin' if users.role exists; otherwise falls back to hardcoded admin creds
 * - Transaction support for placeOrder (atomic insert order + update stock)
 * - Prepared statements and basic input sanitation
 *
 * NOTE:
 * - Ensure MySQL connector (mysql-connector-java) is on your classpath.
 * - Adjust DB_URL, DB_USER, DB_PASSWORD as necessary.
 * - This example uses plaintext passwords for simplicity (educational). For production, hash passwords and use env vars.
 */
public class ShopingCartApp {
    // === CONFIGURATION ===
    private static final String DB_URL = "jdbc:mysql://localhost:3306/shopping_cart_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";          // change as needed
    private static final String DB_PASSWORD = "Ayush@1234"; // change as needed

    // Fallback admin credentials (you provided these)
    private static final String FALLBACK_ADMIN_USER = "admin";
    private static final String FALLBACK_ADMIN_PASSWORD = "admin123";

    private static Connection conn;
    private static Scanner scanner = new Scanner(System.in);
    private static int loggedInUserId = -1;
    private static boolean isAdmin = false;
    private static String loggedInUsername = null;

    public static void main(String[] args) {
        try {
            // Optional explicit driver load
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.err.println("JDBC Driver not found in classpath (com.mysql.cj.jdbc.Driver). Make sure MySQL connector is added.");
            }

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connected to database!");

            while (true) {
                if (loggedInUserId == -1 && !isAdmin) {
                    System.out.println("\n==== Welcome ====");
                    System.out.println("1. User Login");
                    System.out.println("2. Admin Login");
                    System.out.println("3. Exit");
                    System.out.print("Choose an option: ");
                    int choice = readInt();

                    switch (choice) {
                        case 1:
                            loginUser();
                            break;
                        case 2:
                            loginAdmin();
                            break;
                        case 3:
                            closeAndExit();
                            break;
                        default:
                            System.out.println("Invalid choice.");
                    }
                } else if (isAdmin) {
                    adminMenu();
                } else {
                    userMenu();
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error:");
            e.printStackTrace();
        } finally {
            safeCloseConnection();
        }
    }

    // ========== Utility input helpers ==========
    private static int readInt() {
        while (true) {
            try {
                String line = scanner.nextLine().trim();
                return Integer.parseInt(line);
            } catch (NumberFormatException ex) {
                System.out.print("Please enter a valid integer: ");
            }
        }
    }

    private static String readLine() {
        return scanner.nextLine().trim();
    }

    // ========== Login flows ==========
    private static void loginUser() {
        System.out.print("Enter username: ");
        String username = readLine();
        System.out.print("Enter password: ");
        String password = readLine();

        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    loggedInUserId = rs.getInt("id");
                    isAdmin = false;
                    loggedInUsername = username;
                    System.out.println("Login successful. Welcome, " + username + "!");
                } else {
                    System.out.println("Invalid username or password.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error during user login:");
            e.printStackTrace();
        }
    }

    /**
     * Robust admin login:
     * - If users table has a 'role' column, check role='admin' in DB.
     * - If 'role' column doesn't exist, try DB username/password only (but do NOT elevate to admin).
     * - If DB checks fail, fallback to hardcoded admin credentials (FALLBACK_ADMIN_*).
     */
    private static void loginAdmin() {
        System.out.print("Enter admin username: ");
        String username = readLine();
        System.out.print("Enter admin password: ");
        String password = readLine();

        boolean dbTried = false;

        try {
            // check whether 'role' column exists in users table (case-insensitive typical)
            DatabaseMetaData meta = conn.getMetaData();
            boolean roleExists = false;
            try (ResultSet cols = meta.getColumns(null, null, "users", "role")) {
                if (cols.next()) {
                    roleExists = true;
                } else {
                    // Some DBs/catalogs use uppercase/lowercase - try a case-insensitive search
                    try (ResultSet cols2 = meta.getColumns(null, null, "users", null)) {
                        while (cols2.next()) {
                            String colName = cols2.getString("COLUMN_NAME");
                            if ("role".equalsIgnoreCase(colName)) {
                                roleExists = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (roleExists) {
                // 'role' column exists -> require role='admin'
                String sql = "SELECT id FROM users WHERE username = ? AND password = ? AND role = 'admin'";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            isAdmin = true;
                            loggedInUserId = -1;
                            loggedInUsername = username;
                            System.out.println("Admin login successful (DB role).");
                            return;
                        } else {
                            dbTried = true;
                        }
                    }
                }
            } else {
                // No 'role' column -> attempt username/password check only, but do NOT auto-elevate to admin
                String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            // Found a DB user, but since there's no 'role' column we cannot confirm admin role.
                            dbTried = true;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // If any DB error occurs, don't crash: we'll fallback to hardcoded admin
            System.err.println("DB admin-check error (proceeding to fallback): " + e.getMessage());
        }

        // If DB check didn't succeed in granting admin, try fallback hardcoded credentials
        if (FALLBACK_ADMIN_USER.equals(username) && FALLBACK_ADMIN_PASSWORD.equals(password)) {
            isAdmin = true;
            loggedInUserId = -1;
            loggedInUsername = username;
            System.out.println("Admin login successful (fallback credentials).");
        } else {
            // If dbTried true it means credentials matched a user without role; do not grant admin.
            if (dbTried) {
                System.out.println("User authenticated in DB but does not have admin role. Admin login failed.");
            } else {
                System.out.println("Invalid admin credentials.");
            }
        }
    }

    private static void closeAndExit() {
        safeCloseConnection();
        System.out.println("Goodbye!");
        System.exit(0);
    }

    private static void safeCloseConnection() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignore) {
            }
        }
    }

    // ========== User menu & actions ==========
    private static void userMenu() {
        System.out.println("\n==== User Menu ====");
        System.out.println("1. View Items");
        System.out.println("2. Place Order");
        System.out.println("3. View My Cart");
        System.out.println("4. Logout");
        System.out.println("5. Exit");
        System.out.print("Choose an option: ");
        int choice = readInt();

        switch (choice) {
            case 1:
                viewItems();
                break;
            case 2:
                placeOrder();
                break;
            case 3:
                viewCart();
                break;
            case 4:
                loggedInUserId = -1;
                loggedInUsername = null;
                System.out.println("Logged out.");
                break;
            case 5:
                closeAndExit();
                break;
            default:
                System.out.println("Invalid choice.");
        }
    }

    private static void viewItems() {
        String sql = "SELECT * FROM items";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            System.out.printf("%-5s %-30s %-10s %-7s\n", "ID", "Name", "Price", "Stock");
            System.out.println("-------------------------------------------------------------------");
            while (rs.next()) {
                System.out.printf("%-5d %-30s %-10.2f %-7d\n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("stock"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching items:");
            e.printStackTrace();
        }
    }

    /**
     * placeOrder uses transaction support to ensure:
     *  - order INSERT
     *  - items stock UPDATE
     * happen atomically.
     */
    private static void placeOrder() {
        if (loggedInUserId == -1) {
            System.out.println("You must be logged in as a user to place orders.");
            return;
        }

        System.out.print("Enter item ID to buy: ");
        int itemId = readInt();
        System.out.print("Enter quantity: ");
        int quantity = readInt();

        try {
            // Begin transaction
            conn.setAutoCommit(false);

            // 1) Check stock FOR UPDATE (locks row)
            String checkSql = "SELECT stock FROM items WHERE id = ? FOR UPDATE";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, itemId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        int stock = rs.getInt("stock");
                        if (stock < quantity) {
                            System.out.println("Not enough stock available.");
                            conn.rollback();
                            conn.setAutoCommit(true);
                            return;
                        }
                    } else {
                        System.out.println("Item not found.");
                        conn.rollback();
                        conn.setAutoCommit(true);
                        return;
                    }
                }
            }

            // 2) Insert order
            String orderSql = "INSERT INTO orders (user_id, item_id, quantity) VALUES (?, ?, ?)";
            try (PreparedStatement orderStmt = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                orderStmt.setInt(1, loggedInUserId);
                orderStmt.setInt(2, itemId);
                orderStmt.setInt(3, quantity);
                orderStmt.executeUpdate();
            }

            // 3) Update stock
            String updateSql = "UPDATE items SET stock = stock - ? WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, quantity);
                updateStmt.setInt(2, itemId);
                updateStmt.executeUpdate();
            }

            // Commit transaction
            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("Order placed successfully!");

        } catch (SQLException e) {
            System.err.println("Error placing order, rolling back:");
            e.printStackTrace();
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("Rollback failed:");
                ex.printStackTrace();
            }
        }
    }

    private static void viewCart() {
        if (loggedInUserId == -1) {
            System.out.println("You must be logged in as a user to view your cart.");
            return;
        }

        String sql = "SELECT items.name, items.price, orders.quantity, orders.order_date " +
                "FROM orders JOIN items ON orders.item_id = items.id " +
                "WHERE orders.user_id = ? ORDER BY orders.order_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, loggedInUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.printf("%-30s %-10s %-10s %-20s\n", "Item", "Price", "Quantity", "Date");
                System.out.println("--------------------------------------------------------------------------");
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("order_date");
                    String dateStr = ts != null ? ts.toString() : "N/A";
                    System.out.printf("%-30s %-10.2f %-10d %-20s\n",
                            rs.getString("name"),
                            rs.getDouble("price"),
                            rs.getInt("quantity"),
                            dateStr);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error viewing cart:");
            e.printStackTrace();
        }
    }

    // ========== Admin menu & actions ==========
    private static void adminMenu() {
        System.out.println("\n==== Admin Menu ====");
        System.out.println("1. View Items");
        System.out.println("2. Add Item");
        System.out.println("3. Update Item");
        System.out.println("4. Delete Item");
        System.out.println("5. View All Orders");
        System.out.println("6. View Users");
        System.out.println("7. Logout");
        System.out.println("8. Exit");
        System.out.print("Choose an option: ");
        int choice = readInt();

        switch (choice) {
            case 1:
                viewItems();
                break;
            case 2:
                addItem();
                break;
            case 3:
                updateItem();
                break;
            case 4:
                deleteItem();
                break;
            case 5:
                viewAllOrders();
                break;
            case 6:
                viewUsers();
                break;
            case 7:
                isAdmin = false;
                loggedInUsername = null;
                System.out.println("Admin logged out.");
                break;
            case 8:
                closeAndExit();
                break;
            default:
                System.out.println("Invalid choice.");
        }
    }

    private static void addItem() {
        System.out.print("Enter item name: ");
        String name = readLine();
        System.out.print("Enter price: ");
        double price;
        try {
            price = Double.parseDouble(readLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid price.");
            return;
        }
        System.out.print("Enter stock quantity: ");
        int stock = readInt();

        String sql = "INSERT INTO items (name, price, stock) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setDouble(2, price);
            stmt.setInt(3, stock);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Item added successfully.");
            } else {
                System.out.println("Failed to add item.");
            }
        } catch (SQLException e) {
            System.err.println("Error adding item:");
            e.printStackTrace();
        }
    }

    private static void updateItem() {
        System.out.print("Enter item ID to update: ");
        int itemId = readInt();

        // Check existence
        String check = "SELECT * FROM items WHERE id = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(check)) {
            checkStmt.setInt(1, itemId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Item not found.");
                    return;
                } else {
                    System.out.printf("Current -> Name: %s, Price: %.2f, Stock: %d\n",
                            rs.getString("name"), rs.getDouble("price"), rs.getInt("stock"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching item:");
            e.printStackTrace();
            return;
        }

        System.out.print("Enter new name (leave blank to keep unchanged): ");
        String name = readLine();
        System.out.print("Enter new price (or leave blank): ");
        String priceStr = readLine();
        System.out.print("Enter new stock (or leave blank): ");
        String stockStr = readLine();

        // Build update dynamically
        StringBuilder sb = new StringBuilder("UPDATE items SET ");
        boolean first = true;
        if (!name.isEmpty()) {
            sb.append("name = ?");
            first = false;
        }
        if (!priceStr.isEmpty()) {
            if (!first) sb.append(", ");
            sb.append("price = ?");
            first = false;
        }
        if (!stockStr.isEmpty()) {
            if (!first) sb.append(", ");
            sb.append("stock = ?");
        }
        sb.append(" WHERE id = ?");

        // If only WHERE clause (no changes), then nothing to do
        if (sb.toString().trim().equals("UPDATE items SET WHERE id = ?")) {
            System.out.println("No changes provided.");
            return;
        }

        try (PreparedStatement updateStmt = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            if (!name.isEmpty()) updateStmt.setString(idx++, name);
            if (!priceStr.isEmpty()) updateStmt.setDouble(idx++, Double.parseDouble(priceStr));
            if (!stockStr.isEmpty()) updateStmt.setInt(idx++, Integer.parseInt(stockStr));
            updateStmt.setInt(idx, itemId);
            int updated = updateStmt.executeUpdate();
            if (updated > 0) {
                System.out.println("Item updated.");
            } else {
                System.out.println("Update failed or no changes were made.");
            }
        } catch (SQLException e) {
            System.err.println("Error updating item:");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid numeric format for price/stock.");
        }
    }

    private static void deleteItem() {
        System.out.print("Enter item ID to delete: ");
        int itemId = readInt();
        String sql = "DELETE FROM items WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, itemId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Item deleted.");
            } else {
                System.out.println("Item not found or could not be deleted.");
            }
        } catch (SQLException e) {
            System.err.println("Error deleting item:");
            e.printStackTrace();
        }
    }

    private static void viewAllOrders() {
        String sql = "SELECT orders.id, users.username, items.name, orders.quantity, orders.order_date " +
                "FROM orders " +
                "JOIN users ON orders.user_id = users.id " +
                "JOIN items ON orders.item_id = items.id " +
                "ORDER BY orders.order_date DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            System.out.printf("%-5s %-20s %-30s %-10s %-20s\n", "ID", "User", "Item", "Quantity", "Date");
            System.out.println("----------------------------------------------------------------------------------------");
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("order_date");
                String dateStr = ts != null ? ts.toString() : "N/A";
                System.out.printf("%-5d %-20s %-30s %-10d %-20s\n",
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        dateStr);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching orders:");
            e.printStackTrace();
        }
    }

    private static void viewUsers() {
        String sql = "SELECT id, username, email, role FROM users";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            System.out.printf("%-5s %-20s %-30s %-10s\n", "ID", "Username", "Email", "Role");
            System.out.println("--------------------------------------------------------------------------");
            while (rs.next()) {
                System.out.printf("%-5d %-20s %-30s %-10s\n",
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email") != null ? rs.getString("email") : "",
                        rs.getString("role") != null ? rs.getString("role") : "");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching users:");
            e.printStackTrace();
        }
    }
}