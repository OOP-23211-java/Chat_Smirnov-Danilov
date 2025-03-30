package org.example;

import javax.websocket.*;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

@ClientEndpoint
public class ClientHandler {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("chat.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);

            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                if (handler instanceof ConsoleHandler) {
                    rootLogger.removeHandler(handler);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Session session;
    private final String username;
    private String roomNumber;
    private final String serverUri;
    private final ExecutorService executor;
    private Connection connection;
    public static final String RESOURCES_DIR = "../../resources/";
    private static final String DB_URL = "jdbc:sqlite:chat_client.db";

    private CompletableFuture<String> serverStateFuture;

    public ClientHandler(String serverUri, String username, String roomNumber) {
        this.serverUri = serverUri;
        this.username = username;
        this.roomNumber = roomNumber;
        this.executor = Executors.newFixedThreadPool(4);

        initDatabase();
        connect();
    }

    public void setRoomNumber(String newRoomNumber) {
        this.roomNumber = newRoomNumber;
        LOGGER.info("setRoom:ClientHandler:Номер комнаты изменён на: " + newRoomNumber);
    }

    private void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(serverUri));
            LOGGER.info("connect:ClientHandler:Попытка подключения к серверу: " + serverUri);
        } catch (Exception e) {
            LOGGER.severe("connect:ClientHandler:Ошибка подключения к серверу: " + e.getMessage());
        }
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            try (Statement stmt = connection.createStatement()) {
                String sqlState = "CREATE TABLE IF NOT EXISTS currentServerState (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "filename TEXT NOT NULL" +
                        ");";
                stmt.execute(sqlState);

                String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "room_name TEXT, " +
                        "username TEXT, " +
                        "content TEXT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        ");";
                stmt.execute(sqlMessages);
                LOGGER.info("initDB:ClientHandler:База данных и таблицы инициализированы/проверены.");
            }
        } catch (SQLException e) {
            LOGGER.severe("initDB:ClientHandler:Ошибка подключения или инициализации БД: " + e.getMessage());
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        LOGGER.info("onOpen:ClientHandler:Соединение установлено с сервером.");
    }

    public String waitForServerState() {
        if (session == null || !session.isOpen()) {
            throw new RuntimeException("Соединение не установлено.");
        }
        serverStateFuture = new CompletableFuture<>();
        session.getAsyncRemote().sendText("GET_SERVER_STATE");
        LOGGER.info("waitForState:ClientHandler:Запрос состояния сервера отправлен: GET_SERVER_STATE");
        try {
            String stateJson = serverStateFuture.get(10, TimeUnit.SECONDS);
            String filename = writeServerStateToFile(stateJson);
            LOGGER.info("waitForState:ClientHandler:Получены данные о состоянии сервера: " + stateJson);
            return filename;
        } catch (Exception e) {
            LOGGER.severe("waitForState:ClientHandler:Таймаут или ошибка при получении состояния сервера: " + e.getMessage());
            throw new RuntimeException("Таймаут получения состояния сервера или ошибка: " + e.getMessage(), e);
        }
    }

    private String writeServerStateToFile(String stateJson) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDate = now.format(formatter);
        String filename = "server_state_" + formattedDate + ".json";
        String fullPath = RESOURCES_DIR + filename;
        try (FileWriter writer = new FileWriter(fullPath)) {
            writer.write(stateJson);
            LOGGER.info("writeState:ClientHandler:Данные о состоянии сервера записаны в файл: " + filename);
            insertServerStateRecord(filename);
        } catch (IOException e) {
            LOGGER.severe("writeState:ClientHandler:Ошибка записи в файл: " + e.getMessage());
        }
        return filename;
    }

    private void insertServerStateRecord(String filename) {
        String insertSql = "INSERT INTO currentServerState (filename) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setString(1, filename);
            pstmt.executeUpdate();
            LOGGER.info("insertState:ClientHandler:Сохранена запись о состоянии сервера в БД: " + filename);
        } catch (SQLException e) {
            LOGGER.severe("insertState:ClientHandler:Ошибка вставки записи о состоянии сервера в БД: " + e.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message) {
        String trimmed = message.trim();
        if (trimmed.startsWith("{") && serverStateFuture != null && !serverStateFuture.isDone()) {
            serverStateFuture.complete(trimmed);
            LOGGER.info("onMessage:ClientHandler:Получен JSON-сообщение (ответ на GET_SERVER_STATE).");
            return;
        }
        executor.submit(() -> {
            storeMessageInDb(message);
            LOGGER.info("onMessage:ClientHandler:Получено сообщение от сервера: " + message);
            System.out.println(message);
        });
    }

    @OnError
    public void onError(Throwable t) {
        LOGGER.severe("onError:ClientHandler:Ошибка в WebSocket-соединении: " + t.getMessage());
    }

    @OnClose
    public void onClose(CloseReason reason) {
        LOGGER.info("onClose:ClientHandler:Соединение закрыто: " + reason);
    }

    public void sendMessage(String content) {
        if (session != null && session.isOpen()) {
            String formatted = username + " : " + roomNumber + " : " + content;
            session.getAsyncRemote().sendText(formatted);
            LOGGER.info("sendMsg:ClientHandler:Отправлено сообщение: " + formatted);
        } else {
            LOGGER.warning("sendMsg:ClientHandler:Сообщение не отправлено, т.к. соединение не установлено.");
            System.out.println("Соединение не установлено.");
        }
    }

    private final Object dbLock = new Object();
    private void storeMessageInDb(String msg) {
        synchronized (dbLock) {
            String insertSql = "INSERT INTO messages (room_name, username, content) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                pstmt.setString(1, roomNumber);
                pstmt.setString(2, username);
                pstmt.setString(3, msg);
                pstmt.executeUpdate();
                LOGGER.info("storeMsg:ClientHandler:Сообщение сохранено в БД: " + msg);
            } catch (SQLException e) {
                LOGGER.severe("storeMsg:ClientHandler:Ошибка записи сообщения в БД: " + e.getMessage());
            }
        }
    }

    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                LOGGER.info("close:ClientHandler:WebSocket-сессия закрыта по запросу клиента.");
            } catch (Exception e) {
                LOGGER.severe("close:ClientHandler:Ошибка при закрытии соединения: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("close:ClientHandler:Соединение с БД закрыто.");
            } catch (SQLException e) {
                LOGGER.severe("close:ClientHandler:Ошибка при закрытии БД: " + e.getMessage());
            }
        }
    }
}
