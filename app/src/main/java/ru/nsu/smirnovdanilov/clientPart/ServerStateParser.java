package ru.nsu.smirnovdanilov.clientPart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

/**
 * Класс парсер json файла состояния сервера.
 */
public class ServerStateParser {

    /**
     * Разбирает файл json состояния сервера и возвращает строку вида:
     * Доступные комнаты и пользователи:
     * Комната 1: [user1, user2, ...]
     * Комната 2: [user3, user4, ...]
     * @param jsonFile Файл json состояния сервера.
     * @return Строка в нужном формате с состоянием.
     */
    public static String parseServerState(File jsonFile) {
        StringBuilder result = new StringBuilder("Доступные комнаты и пользователи:\n");

        Map<String, List<String>> roomUsers = parseConnectedUsers(jsonFile);

        String formattedResult = formatRoomUsers(roomUsers);

        result.append(formattedResult);
        return result.toString();
    }

    /**
     * Парсит json и формирует мапу, с ключом - номер комнаты.
     * Значение – список с именами пользователей в комнате.
     * @param jsonFile Файл с состоянием сервера.
     * @return Мапа комнат и соответствующих списков пользователей.
     */
    private static Map<String, List<String>> parseConnectedUsers(File jsonFile) {
        Map<String, List<String>> roomUsers = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(jsonFile);
            JsonNode connectedUsers = root.get("connected_users");

            if (connectedUsers != null && connectedUsers.isArray()) {
                for (JsonNode userNode : connectedUsers) {
                    String room = userNode.get("room").asText();
                    String username = userNode.get("username").asText();
                    roomUsers.computeIfAbsent(room, k -> new ArrayList<>()).add(username);
                }
            }
        } catch (Exception e) {
            roomUsers.clear();
            roomUsers.put("Ошибка", Collections.singletonList(e.getMessage()));
        }

        return roomUsers;
    }

    /**
     * Форматирует мапу комнат и пользователей в строку, с сортировкой комнат по возрастанию.
     * @param roomUsers мапа [комната] - [список пользователей]
     * @return Строка с информацией о состоянии чата
     */
    private static String formatRoomUsers(Map<String, List<String>> roomUsers) {
        StringBuilder formatted = new StringBuilder();

        List<String> rooms = new ArrayList<>(roomUsers.keySet());
        try {
            rooms.sort(Comparator.comparingInt(Integer::parseInt));
        } catch (NumberFormatException e) {
            Collections.sort(rooms);
        }

        for (String room : rooms) {
            formatted.append("Комната ").append(room).append(": ");
            formatted.append(roomUsers.get(room).toString()).append("\n");
        }

        return formatted.toString();
    }
}
