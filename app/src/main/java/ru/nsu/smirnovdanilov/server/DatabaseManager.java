package ru.nsu.smirnovdanilov.server;

import java.sql.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Менеджер работы с базой данных сервера.
 * Обеспечивает выполнение SQL-запросов, управление схемой БД,
 * сохранение и загрузку данных чата.
 */
public class DatabaseManager {
    private static final String CREATE_TABLES_QUERY =
            "CREATE TABLE IF NOT EXISTS rooms (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT UNIQUE NOT NULL);" +

                    "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "room_id INTEGER NOT NULL, " +
                    "UNIQUE(username, room_id), " +
                    "FOREIGN KEY(room_id) REFERENCES rooms(id));" +

                    "CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id));";

    /**
     * Инициализирует структуру базы данных
     * @param conn активное соединение с БД
     * @throws SQLException при ошибках выполнения SQL-запросов
     */
    public static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLES_QUERY);
        }
    }

    /**
     * Сохраняет сообщение в базе данных
     * @param conn активное соединение с БД
     * @param msg объект сообщения для сохранения
     * @throws SQLException при ошибках выполнения SQL-запросов
     */
    public static void saveMessage(Connection conn, Message msg) throws SQLException {
        String sql = "INSERT INTO messages(user_id, content) " +
                "SELECT users.id FROM users " +
                "WHERE users.username = ? AND users.room_id = " +
                "(SELECT id FROM rooms WHERE name = ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, msg.getUsername());
            pstmt.setString(2, msg.getRoom());
            pstmt.executeUpdate();
        }
    }

    /**
     * Формирует JSON-представление текущего состояния сервера
     * @param conn активное соединение с БД
     * @return JSON-строка с информацией о пользователях и комнатах
     * @throws SQLException при ошибках выполнения SQL-запросов
     */
    public static String getServerState(Connection conn) throws SQLException {
        JSONObject json = new JSONObject();
        JSONArray users = new JSONArray();

        String sql = "SELECT r.name as room, u.username " +
                "FROM users u JOIN rooms r ON u.room_id = r.id";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                JSONObject user = new JSONObject();
                user.put("room", rs.getString("room"));
                user.put("username", rs.getString("username"));
                users.put(user);
            }
        }

        json.put("connected_users", users);
        return json.toString();
    }
}
