package ru.nsu.smirnovdanilov.clientPart;

import javax.websocket.*;
import java.net.URI;
import java.sql.*;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Обработчик клиента: устанавливает WebSocket соединение, обрабатывает обмен сообщениями и взаимодействие с БД
 */
@ClientEndpoint
public class ClientHandler {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    static {
        configureLogger();
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
    private final Object dbLock = new Object();

    /**
     * Конструктор обработчика
     * @param serverUri  URI сервера.
     * @param username   Имя пользователя.
     * @param roomNumber Номер комнаты.
     */
    public ClientHandler(String serverUri, String username, String roomNumber) {
        this.serverUri = serverUri;
        this.username = username;
        this.roomNumber = roomNumber;
        this.executor = Executors.newFixedThreadPool(4);

        initDatabase();
        connect();
    }

    /**
     * Настраивает логгер: добавляет файловый обработчик и удаляет обработчик консоли
     */
    private static void configureLogger() {
        try {
            FileHandler fileHandler = new FileHandler("chat.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);

            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    rootLogger.removeHandler(handler);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Устанавливает номер комнаты
     * @param newRoomNumber новый номер комнаты.
     */
    public void setRoomNumber(String newRoomNumber) {
        this.roomNumber = newRoomNumber;
        LOGGER.info("Номер комнаты изменён на: " + newRoomNumber);
    }

    /**
     * Устанавливает WebSocket соединение с сервером чата
     */
    private void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(serverUri));
            LOGGER.info("Попытка подключения к серверу: " + serverUri);
        } catch (Exception e) {
            LOGGER.severe("Ошибка подключения к серверу: " + e.getMessage());
        }
    }

    /**
     * Инициализирует БД и создаёт таблицы
     */
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

                LOGGER.info("БД создана");
            }
        } catch (SQLException e) {
            LOGGER.severe("Ошибка подключения к БД: " + e.getMessage());
        }
    }

    /**
     * Обработчик события открытия соединения.
     * @param session установленная сессия.
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        LOGGER.info("Соединение с сервером установлено");
    }

    /**
     * Отправляет запрос состояния сервера и ожидает ответа.
     * @return имя файла, в котором сохранено состояние сервера (json)
     * @throws RuntimeException если соединение не установлено или таймаут.
     */
    public String waitForServerState() {
        if (session == null || !session.isOpen()) {
            throw new RuntimeException("Соединение не установлено.");
        }

        serverStateFuture = new CompletableFuture<>();
        session.getAsyncRemote().sendText("GET_SERVER_STATE");
        LOGGER.info("Отправлен GET_SERVER_STATE");

        try {
            String stateJson = serverStateFuture.get(10, TimeUnit.SECONDS);
            String filename = writeServerStateToFile(stateJson);

            LOGGER.info("Получены данные о состоянии сервера: " + stateJson);
            return filename;
        } catch (Exception e) {
            LOGGER.severe("Таймаут или ошибка при получении состояния сервера: " + e.getMessage());
            throw new RuntimeException("Таймаут или ошибка при получении состояния сервера: " + e.getMessage(), e);
        }
    }

    /**
     * Записывает ответ сервера в файл в формате json и сохраняет запись в БД
     * @param stateJson строка с состоянием сервера.
     * @return имя файла в который записано состояние.
     */
    private String writeServerStateToFile(String stateJson) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDate = now.format(formatter);

        String filename = "server_state_" + formattedDate + ".json";
        String fullPath = RESOURCES_DIR + filename;

        try (FileWriter writer = new FileWriter(fullPath)) {
            writer.write(stateJson);
            LOGGER.info("Данные о состоянии сервера записаны в файл: " + filename);

            insertServerStateRecord(filename);
        } catch (IOException e) {
            LOGGER.severe("Ошибка записи в файл: " + e.getMessage());
        }

        return filename;
    }

    /**
     * Сохраняет запись о состоянии сервера в БД
     * @param filename имя файла, содержащего состояние сервера
     */
    private void insertServerStateRecord(String filename) {
        String insertSql = "INSERT INTO currentServerState (filename) VALUES (?)";

        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setString(1, filename);
            pstmt.executeUpdate();

            LOGGER.info("Сохранена запись о состоянии сервера в БД: " + filename);
        } catch (SQLException e) {
            LOGGER.severe("Ошибка вставки записи о состоянии сервера в БД: " + e.getMessage());
        }
    }

    /**
     * Обработчик получения сообщения от сервера.
     * @param message полученное сообщение.
     */
    @OnMessage
    public void onMessage(String message) {
        String trimmedMessage = message.trim();

        if (trimmedMessage.startsWith("{") && serverStateFuture != null && !serverStateFuture.isDone()) {
            serverStateFuture.complete(trimmedMessage);
            LOGGER.info("Получен ответ на запрос состояния.");
            return;
        }

        executor.submit(() -> {
            storeMessageInDb(message);
            LOGGER.info("Получено сообщение от сервера: " + message);
            System.out.println(message);
        });
    }

    /**
     * Обработчик ошибки соединения
     * @param t Throwable
     */
    @OnError
    public void onError(Throwable t) {
        LOGGER.severe("Ошибка соединения: " + t.getMessage());
    }

    /**
     * Обработчик события закрытия соединения.
     * @param reason строка описывающая причину.
     */
    @OnClose
    public void onClose(CloseReason reason) {
        LOGGER.info("Соединение закрыто: " + reason);
    }

    /**
     * Отправляет сообщение на сервер.
     * @param content текст сообщения.
     */
    public void sendMessage(String content) {
        if (session != null && session.isOpen()) {
            String formattedMessage = username + " : " + roomNumber + " : " + content;
            session.getAsyncRemote().sendText(formattedMessage);
            LOGGER.info("Отправлено сообщение: " + formattedMessage);
        } else {
            LOGGER.warning("Сообщение не отправлено из-за неустановленного соединения.");
            System.out.println("Соединение не установлено.");
        }
    }

    /**
     * Сохраняет полученное сообщение в БД.
     * @param msg сообщение для сохранения.
     */
    private void storeMessageInDb(String msg) {
        synchronized (dbLock) {
            String insertSql = "INSERT INTO messages (room_name, username, content) VALUES (?, ?, ?)";

            try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                pstmt.setString(1, roomNumber);
                pstmt.setString(2, username);
                pstmt.setString(3, msg);
                pstmt.executeUpdate();

                LOGGER.info("Сообщение сохранено в БД: " + msg);
            } catch (SQLException e) {
                LOGGER.severe("Ошибка записи сообщения в БД: " + e.getMessage());
            }
        }
    }

    /**
     * Закрывает сессию, останавливает пул потоков и закрывает соединение с базой данных.
     */
    public void close() {
        closeSession();
        executor.shutdownNow();
        closeDatabase();
    }

    /**
     * Закрывает websocket сессию.
     */
    private void closeSession() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                LOGGER.info("Соединение закрыто по запросу клиента.");
            } catch (Exception e) {
                LOGGER.severe("Ошибка при закрытии соединения: " + e.getMessage());
            }
        }
    }

    /**
     * Закрывает соединение с базой данных.
     */
    private void closeDatabase() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("Соединение с БД закрыто.");
            } catch (SQLException e) {
                LOGGER.severe("Ошибка при закрытии БД: " + e.getMessage());
            }
        }
    }
}
