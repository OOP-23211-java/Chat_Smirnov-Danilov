package ru.nsu.smirnovdanilov.clientPart;

public class Utilits {
    private static boolean isCorrectUsername(String username) {
        return username.matches("^[a-zA-Z]+[0-9]*$");
    }

    private static boolean isCorrectNumberOfRoom(String number) {
        return number.matches("^(1[0-9]|20|[1-9])$");
    }


    public static void validateArgs(String[] args) throws IllegalArgumentException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Введено неверное количество аргументов\n");
        }
        if (!isCorrectUsername(args[0].trim())) {
            throw new IllegalArgumentException("Неверный формат имени. Попробуйте еще раз\n");
        }
        if(!isCorrectNumberOfRoom(args[1].trim())) {
            throw new IllegalArgumentException("Неверный номер комнаты. Попробуйте еще раз\n");
        }
    }
}
