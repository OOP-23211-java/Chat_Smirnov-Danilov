package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;

public class ServerStateParser {

    /**
     * Разбирает JSON состояния сервера и возвращает строку вида:
     * Доступные комнаты и пользователи:
     * Комната 1: [user1, user2, ...]
     * Комната 2: [user3, user4, ...]
     *
     * @param jsonFile Файл с JSON-состоянием сервера.
     * @return Форматированная строка с комнатами и списками пользователей.
     */
    public static String parseServerState(File jsonFile) {
        StringBuilder result = new StringBuilder("Доступные комнаты и пользователи:\n");
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(jsonFile);
            JsonNode connectedUsers = root.get("connected_users");
            Map<String, List<String>> roomUsers = new HashMap<>();
            if (connectedUsers != null && connectedUsers.isArray()) {
                for (JsonNode userNode : connectedUsers) {
                    String room = userNode.get("room").asText();
                    String username = userNode.get("username").asText();
                    roomUsers.computeIfAbsent(room, k -> new ArrayList<>()).add(username);
                }
            }
            List<String> rooms = new ArrayList<>(roomUsers.keySet());
            rooms.sort(Comparator.comparingInt(Integer::parseInt));
            for (String room : rooms) {
                result.append("Комната ").append(room).append(": ");
                result.append(roomUsers.get(room).toString()).append("\n");
            }
        } catch (Exception e) {
            return "Ошибка парсинга: " + e.getMessage();
        }
        return result.toString();
    }
}
