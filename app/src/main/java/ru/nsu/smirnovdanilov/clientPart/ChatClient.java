package ru.nsu.smirnovdanilov.clientPart;

import java.io.IOException;
import java.util.Scanner;
import java.util.logging.*;
import java.io.File;

public class ChatClient {

    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());

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

    public static void main(String[] args) {
        String username, roomNumber;
        try {
            Utilits.validateArgs(args);
            username = args[0].trim();
            roomNumber = args[1].trim();
        } catch (IllegalArgumentException e) {
            LOGGER.severe("validate:ChatClient:Ошибка аргументов: " + e.getMessage());
            return;
        }

        int port = 8081;
        String serverIP = "185.84.163.83";
        String serverUri = String.format("ws://%s:%d/chat", serverIP, port);
        LOGGER.info("connect:ChatClient:Попытка соединения с сервером: " + serverUri);

        ClientHandler clientHandler = new ClientHandler(serverUri, username, roomNumber);

        String stateFilename;
        try {
            stateFilename = clientHandler.waitForServerState();
            File stateFile = new File(ClientHandler.RESOURCES_DIR + stateFilename);
            String parsedState = ServerStateParser.parseServerState(stateFile);
            System.out.println(parsedState);
        } catch (RuntimeException e) {
            LOGGER.severe("waitForState:ChatClient:Ошибка получения состояния сервера: " + e.getMessage());
            clientHandler.close();
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.printf("Вы указали комнату '%s'. Для подтверждения/отклонения введите (Y/n): ", roomNumber);
        String answer = scanner.nextLine().trim();
        if (answer.equalsIgnoreCase("n")) {
            System.out.print("Введите номер комнаты: ");
            String newRoom = scanner.nextLine().trim();
            clientHandler.setRoomNumber(newRoom);
            System.out.printf("Вы выбрали комнату '%s'.%n", newRoom);
            LOGGER.info("roomChange:ChatClient:Пользователь сменил комнату на: " + newRoom);
        } else {
            System.out.printf("Продолжаем работу в комнате '%s'.%n", roomNumber);
            LOGGER.info("roomKeep:ChatClient:Пользователь остался в комнате: " + roomNumber);
        }

        System.out.print("Введите сообщение (или 'exit' для выхода, 'list' для запроса состояния чата): ");
        while (true) {
            System.out.println(">");
            String message = scanner.nextLine();
            if ("exit".equalsIgnoreCase(message)) {
                LOGGER.info("exit:ChatClient:Пользователь ввёл команду выхода.");
                break;
            }
            if ("list".equalsIgnoreCase(message)) {
                LOGGER.info("list:ChatClient:Пользователь запросил состояние сервера (list).");
                try {
                    stateFilename = clientHandler.waitForServerState();
                    File stateFile = new File(ClientHandler.RESOURCES_DIR + stateFilename);
                    String parsedState = ServerStateParser.parseServerState(stateFile);
                    System.out.println(parsedState);
                } catch (RuntimeException e) {
                    LOGGER.severe("list:ChatClient:Ошибка при повторном запросе состояния сервера: " + e.getMessage());
                }
                continue;
            }
            clientHandler.sendMessage(message);
        }

        clientHandler.close();
        scanner.close();
        LOGGER.info("exit:ChatClient:Программа ChatClient завершена.");
    }
}
