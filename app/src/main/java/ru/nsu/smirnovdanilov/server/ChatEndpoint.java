package ru.nsu.smirnovdanilov.server;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Класс-обработчик WebSocket соединений.
 * Обрабатывает жизненный цикл подключений, регистрацию пользователей,
 * пересылку сообщений и управление сессиями.
 */
@ServerEndpoint("/chat")
public class ChatEndpoint {
    private static final Logger LOGGER = Logger.getLogger(ChatEndpoint.class.getName());
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(10);

    private Session session;
    private UserSession userSession;

    /**
     * Вызывается при установке нового WebSocket соединения
     * @param session объект сессии нового подключения
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        LOGGER.info("Новое подключение: " + session.getId());
    }

    /**
     * Обрабатывает входящие сообщения от клиента
     * @param message полученное текстовое сообщение
     * @param session объект сессии-отправителя
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        workerPool.submit(() -> processMessage(message));
    }

    /**
     * Маршрутизирует сообщения в соответствующие обработчики
     * @param message полученное сообщение для обработки
     */
    private void processMessage(String message) {
        if ("GET_SERVER_STATE".equals(message)) {
            handleServerStateRequest();
            return;
        }

        if (userSession == null) {
            handleUserRegistration(message);
        } else {
            handleChatMessage(message);
        }
    }

    /**
     * Обрабатывает запрос на получение текущего состояния сервера
     */
    private void handleServerStateRequest() {
        try {
            String jsonState = DatabaseManager.getServerState(userSession.getDbConnection());
            session.getAsyncRemote().sendText(jsonState);
            LOGGER.info("Отправлено состояние сервера для сессии: " + session.getId());
        } catch (SQLException e) {
            LOGGER.severe("Ошибка формирования состояния: " + e.getMessage());
        }
    }

    /**
     * Регистрирует нового пользователя в системе
     * @param credentials строка учетных данных в формате "username:room"
     * @throws IllegalArgumentException при неверном формате данных
     */
    private void handleUserRegistration(String credentials) {
        try {
            String[] parts = credentials.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Неверный формат регистрационных данных");
            }

            String username = parts[0].trim();
            String room = parts[1].trim();

            userSession = new UserSession(session, username, room);
            userSession.registerUser();

            session.getAsyncRemote().sendText("REGISTRATION_SUCCESS");
            LOGGER.info("Успешная регистрация пользователя: " + username);
        } catch (Exception e) {
            LOGGER.warning("Ошибка регистрации: " + e.getMessage());
            session.getAsyncRemote().sendText("ERROR: " + e.getMessage());
            closeSessionWithError();
        }
    }

    /**
     * Обрабатывает обычные сообщения чата
     * @param message текст сообщения для обработки
     */
    private void handleChatMessage(String message) {
        try {
            Message msg = new Message(
                    userSession.getUsername(),
                    userSession.getRoom(),
                    message
            );

            DatabaseManager.saveMessage(userSession.getDbConnection(), msg);
            userSession.broadcastMessage(msg.toString());
            LOGGER.fine("Сообщение обработано: " + msg);
        } catch (SQLException e) {
            LOGGER.severe("Ошибка сохранения сообщения: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает закрытие соединения клиентом
     * @param session закрываемая сессия
     * @param reason причина закрытия соединения
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        if (userSession != null) {
            userSession.cleanup();
            LOGGER.info("Пользователь вышел: " + userSession.getUsername());
        }
        LOGGER.info("Соединение закрыто: " + reason.getReasonPhrase());
    }

    /**
     * Обрабатывает ошибки в работе WebSocket соединения
     * @param session сессия с ошибкой
     * @param throwable объект исключения
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.severe("WebSocket ошибка: " + throwable.getMessage());
        closeSessionWithError();
    }

    /**
     * Корректно закрывает сессию с указанием кода ошибки протокола
     */
    private void closeSessionWithError() {
        try {
            session.close(new CloseReason(
                    CloseReason.CloseCodes.PROTOCOL_ERROR,
                    "Ошибка обработки данных"
            ));
        } catch (IOException e) {
            LOGGER.warning("Ошибка при закрытии сессии: " + e.getMessage());
        }
    }
}
