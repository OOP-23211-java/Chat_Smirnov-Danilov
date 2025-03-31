package ru.nsu.smirnovdanilov.server;

import javax.websocket.Session;
import java.sql.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Класс для управления пользовательскими сессиями.
 * Обрабатывает регистрацию пользователей, проверку уникальности имен,
 * управление комнатами и рассылку сообщений.
 */
public class UserSession {
    private static final ConcurrentHashMap<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    private final Session session;
    private final String username;
    private final String room;
    private Connection dbConnection;

    /**
     * Создает новую пользовательскую сессию
     * @param session WebSocket сессия пользователя
     * @param username уникальное имя пользователя
     * @param room номер комнаты для подключения
     */
    public UserSession(Session session, String username, String room) {
        this.session = session;
        this.username = username;
        this.room = room;
    }

    /**
     * Регистрирует пользователя в системе:
     * 1. Проверяет уникальность имени в комнате
     * 2. Создает комнату при необходимости
     * 3. Добавляет запись в базу данных
     * @throws SQLException при ошибках работы с БД
     * @throws IllegalArgumentException при дублировании имени пользователя
     */
    public void registerUser() throws SQLException {
        dbConnection = DriverManager.getConnection("jdbc:sqlite:chat_server.db");
        dbConnection.setAutoCommit(false);

        synchronized (roomUsers) {
            checkDuplicateUser();
            createRoomIfNotExists();
            addUserToDatabase();
            roomUsers.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(username);
        }
    }

    /**
     * Проверяет уникальность имени пользователя в выбранной комнате
     * @throws IllegalArgumentException если имя уже занято
     */
    private void checkDuplicateUser() {
        Set<String> usersInRoom = roomUsers.getOrDefault(room, Collections.emptySet());
        if (usersInRoom.contains(username)) {
            throw new IllegalArgumentException("Имя пользователя уже занято в этой комнате");
        }
    }

    /**
     * Создает новую комнату в базе данных, если она не существует
     * @throws SQLException при ошибках выполнения SQL-запросов
     */
    private void createRoomIfNotExists() throws SQLException {
        String sql = "INSERT OR IGNORE INTO rooms(name) VALUES(?)";
        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, room);
            pstmt.executeUpdate();
        }
    }

    /**
     * Добавляет пользователя в базу данных
     * @throws SQLException при ошибках выполнения SQL-запросов
     */
    private void addUserToDatabase() throws SQLException {
        String sql = "INSERT INTO users(username, room_id) " +
                "VALUES(?, (SELECT id FROM rooms WHERE name = ?))";

        try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, room);
            pstmt.executeUpdate();
            dbConnection.commit();
        }
    }

    /**
     * Рассылает сообщение всем участникам текущей комнаты
     * @param message текст сообщения для рассылки
     */
    public void broadcastMessage(String message) {
        session.getOpenSessions().stream()
                .filter(s -> s.isOpen() &&
                        ((UserSession)s.getUserProperties().get("userSession")).getRoom().equals(room))
                .forEach(s -> s.getAsyncRemote().sendText(message));
    }

    /**
     * Выполняет очистку ресурсов при выходе пользователя:
     * 1. Удаляет пользователя из кэша
     * 2. Закрывает соединение с БД
     */
    public void cleanup() {
        roomUsers.get(room).remove(username);
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка закрытия соединения: " + e.getMessage());
        }
    }

    public String getUsername() { return username; }
    public String getRoom() { return room; }
    public Connection getDbConnection() { return dbConnection; }
}
