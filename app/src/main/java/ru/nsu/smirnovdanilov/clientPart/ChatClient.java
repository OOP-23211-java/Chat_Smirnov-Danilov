package ru.nsu.smirnovdanilov.clientPart;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.*;

/**
 * Главный класс клиента чата.
 * Отвечает за установку соединения с сервером..
 */
public class ChatClient {

    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final int PORT = 8081;
    private static final String SERVER_IP = "185.84.163.83";
    private static final String SERVER_URI_TEMPLATE = "ws://%s:%d/chat";

    static {
        configureLogger();
    }

    public static void main(String[] args) {
        String username;
        String roomNumber;
        try {
            Utilits.validateArgs(args);
            username = args[0].trim();
            roomNumber = args[1].trim();
        } catch (IllegalArgumentException e) {
            LOGGER.severe("Ошибка аргументов: " + e.getMessage());
            return;
        }

        String serverUri = String.format(SERVER_URI_TEMPLATE, SERVER_IP, PORT);
        LOGGER.info("Попытка соединения с сервером: " + serverUri);

        ClientHandler clientHandler = new ClientHandler(serverUri, username, roomNumber);

        if (!printServerState(clientHandler)) {
            clientHandler.close();
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            roomNumber = handleRoomSelection(scanner, clientHandler, roomNumber);
            processChat(scanner, clientHandler, roomNumber);
        } finally {
            clientHandler.close();
            LOGGER.info("Завершение работы");
        }
    }

    /**
     * Настраивает логер, включая запись в файл и удаляет консольные.
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
     * Получает и выводит состояние сервера
     * @param clientHandler - обработчик клиента для взаимодействия с сервером
     * @return {@code true}, если состояние успешно получено, иначе {@code false}.
     */
    private static boolean printServerState(ClientHandler clientHandler) {
        try {
            String stateFilename = clientHandler.waitForServerState();
            File stateFile = new File(ClientHandler.RESOURCES_DIR + stateFilename);
            String parsedState = ServerStateParser.parseServerState(stateFile);
            System.out.println(parsedState);
            return true;
        } catch (RuntimeException e) {
            LOGGER.severe("Ошибка получения состояния сервера: " + e.getMessage());
            return false;
        }
    }

    /**
     * Обрабатывает выбор комнаты
     * @param scanner для чтения с консоли
     * @param clientHandler обработчик клиента чтобы обновить комнату
     * @param currentRoom текущий номер комнаты
     * @return выбранный номер комнаты
     */
    private static String handleRoomSelection(Scanner scanner, ClientHandler clientHandler, String currentRoom) {
        System.out.printf("Вы указали комнату '%s'. Для подтверждения/отклонения введите (Y/n): ", currentRoom);
        String answer = scanner.nextLine().trim();
        if (answer.equalsIgnoreCase("n")) {
            System.out.print("Введите номер комнаты: ");
            String newRoom = scanner.nextLine().trim();
            clientHandler.setRoomNumber(newRoom);
            System.out.printf("Вы выбрали комнату '%s'.%n", newRoom);
            LOGGER.info("Смена комнаты на : " + newRoom);
            return newRoom;
        } else {
            System.out.printf("Комната: '%s'.%n", currentRoom);
            LOGGER.info("Подтверждение комнаты: " + currentRoom);
            return currentRoom;
        }
    }

    /**
     * Обрабатывает отправку сообщений и выполнение команд
     * @param scanner для чтения с консоли
     * @param clientHandler обработчик клиента для отправки сообщений
     * @param roomNumber текущий номер комнаты
     */
    private static void processChat(Scanner scanner, ClientHandler clientHandler, String roomNumber) {
        System.out.println("Текущая комната: " + roomNumber);
        System.out.print("Введите сообщение (или 'exit' для выхода, 'list' для запроса состояния чата): ");
        while (true) {
            System.out.println(">");
            String message = scanner.nextLine().trim();
            if (message.equalsIgnoreCase("exit")) {
                LOGGER.info("Введена команда exit.");
                break;
            } else if (message.equalsIgnoreCase("list")) {
                LOGGER.info("Запрошено состояние сервера (команда list).");
                printServerState(clientHandler);
            } else {
                clientHandler.sendMessage(message);
            }
        }
    }
}
