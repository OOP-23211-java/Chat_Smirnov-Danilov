package ru.nsu.smirnovdanilov.server;

import org.glassfish.tyrus.server.Server;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.logging.*;

/**
 * Основной класс для запуска и управления сервером чата.
 * Обеспечивает инициализацию компонентов системы, обработку команд администратора
 * и корректное завершение работы сервера.
 */
public class ChatServer {
    private static final Logger LOGGER = Logger.getLogger(ChatServer.class.getName());
    private static final int PORT = 8081;
    private static final String IP = "0.0.0.0";
    private static final String DB_URL = "jdbc:sqlite:chat_server.db";

    private Server websocketServer;
    private Connection dbConnection;

    /**
     * Точка входа в серверное приложение
     * @param args аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        new ChatServer().start();
    }

    /**
     * Инициализирует и запускает основные компоненты сервера:
     * 1. Подключение к базе данных
     * 2. WebSocket сервер
     * 3. Систему логирования
     * 4. Обработчик консольных команд
     */
    public void start() {
        initializeDatabase();
        initializeWebSocketServer();
        initializeLogger();

        LOGGER.info("Сервер запущен и готов принимать подключения");
        new Thread(this::handleConsoleInput).start();
    }

    /**
     * Устанавливает соединение с SQLite базой данных и создает необходимые таблицы.
     * В случае ошибки подключения записывает сообщение в лог и завершает работу.
     */
    private void initializeDatabase() {
        try {
            dbConnection = DriverManager.getConnection(DB_URL);
            DatabaseManager.createTables(dbConnection);
            LOGGER.info("Инициализировано подключение к БД");
        } catch (SQLException e) {
            LOGGER.severe("Ошибка подключения к БД: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Запускает WebSocket сервер на указанном порту и IP-адресе.
     * Использует реализацию Tyrus (GlassFish) для обработки WebSocket соединений.
     */
    private void initializeWebSocketServer() {
        websocketServer = new Server(IP, PORT, "/chat", null, ChatEndpoint.class);
        try {
            websocketServer.start();
        } catch (Exception e) {
            LOGGER.severe("Ошибка запуска WebSocket сервера: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Настраивает систему логирования в файл server.log.
     * Использует SimpleFormatter для чистого вывода без XML-разметки.
     */
    private void initializeLogger() {
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Ошибка настройки логгера: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает ввод из консоли для административных команд.
     * Поддерживаемые команды:
     * - stop: корректное завершение работы сервера
     */
    private void handleConsoleInput() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String command = scanner.nextLine();
            if ("stop".equalsIgnoreCase(command)) {
                shutdown();
                break;
            }
        }
        scanner.close();
    }

    /**
     * Корректно завершает работу сервера:
     * 1. Останавливает WebSocket сервер
     * 2. Закрывает соединение с базой данных
     * 3. Выполняет очистку ресурсов
     */
    private void shutdown() {
        LOGGER.info("Начало процедуры остановки сервера");
        websocketServer.stop();
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException e) {
            LOGGER.severe("Ошибка закрытия БД: " + e.getMessage());
        }
        LOGGER.info("Сервер остановлен");
        System.exit(0);
    }
}
