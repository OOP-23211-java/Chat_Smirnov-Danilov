package ru.nsu.smirnovdanilov.server;

/**
 * Модель сообщения чата.
 * Содержит информацию об авторе, комнате и тексте сообщения.
 */
public class Message {
    private final String username;
    private final String room;
    private final String content;

    /**
     * Создает новый объект сообщения
     * @param username имя отправителя
     * @param room номер комнаты
     * @param content текст сообщения
     */
    public Message(String username, String room, String content) {
        this.username = username;
        this.room = room;
        this.content = content;
    }

    /**
     * Форматирует сообщение для пересылки через сеть
     * @return строка в формате "username:room:content"
     */
    @Override
    public String toString() {
        return username + " : " + room + " : " + content;
    }

    public String getUsername() { return username; }
    public String getRoom() { return room; }
    public String getContent() { return content; }
}
