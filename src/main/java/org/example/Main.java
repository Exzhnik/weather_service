package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Scanner;

public class Main {
    private static final int MAX_LIMIT = 7;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    public static void main(String[] args) {
        System.exit(run());
    }

    private static int run() {
        // Вставьте сюда свой ключ Яндекс Погоды
        String apiKey = "";

        Scanner scanner = new Scanner(System.in);

        double lat;
        double lon;
        int limit;
        try {
            lat = readDouble(scanner, "Введите широту (lat) от -90 до 90, например 55.75: ", -90, 90);
            lon = readDouble(scanner, "Введите долготу (lon) от -180 до 180, например 37.62: ", -180, 180);
            limit = readInt(scanner, "Введите количество суток (limit) от 1 до " + MAX_LIMIT + ", например 7: ", 1, MAX_LIMIT);
        } catch (IllegalStateException e) {
            System.err.println();
            System.err.println("Ошибка: ввод прерван, программа завершена");
            return 1;
        }

        String url = "https://api.weather.yandex.ru/v2/forecast?lat=" + formatCoordinate(lat)
                + "&lon=" + formatCoordinate(lon) + "&limit=" + limit;
        System.out.println("Запрос: " + url);

        String body = fetchForecast(url, apiKey);
        if (body == null) {
            return 1;
        }

        JsonNode root;
        try {
            root = new ObjectMapper().readTree(body);
        } catch (JsonProcessingException e) {
            System.err.println("Ошибка: ответ сервиса не является корректным JSON (" + e.getOriginalMessage() + ")");
            return 1;
        }

        JsonNode temp = root.path("fact").path("temp");
        if (temp.isNumber()) {
            System.out.println("Текущая температура (fact.temp): " + temp.asInt() + " °C");
        } else {
            System.out.println("Текущая температура (fact.temp): нет данных");
        }

        JsonNode forecasts = root.path("forecasts");
        int days = Math.min(limit, forecasts.size());
        double sum = 0;
        int count = 0;
        for (int i = 0; i < days; i++) {
            JsonNode tempAvg = forecasts.get(i).path("parts").path("day").path("temp_avg");
            if (tempAvg.isNumber()) {
                sum += tempAvg.asDouble();
                count++;
            }
        }

        if (count > 0) {
            System.out.printf("Средняя дневная температура за %d сут.: %.1f °C%n", count, sum / count);
        } else {
            System.out.println("Средняя дневная температура: нет данных в прогнозе");
        }
        return 0;
    }

    private static String fetchForecast(String url, String apiKey) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Yandex-Weather-Key", apiKey)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Код ответа: " + response.statusCode());

            System.out.println("=== Полный ответ ===");
            System.out.println(response.body());

            if (response.statusCode() != 200) {
                System.err.println("Ошибка: " + describeHttpError(response.statusCode()));
                return null;
            }
            return response.body();
        } catch (HttpConnectTimeoutException e) {
            System.err.println("Ошибка: не удалось подключиться к сервису за " + CONNECT_TIMEOUT.toSeconds() + " сек.");
        } catch (HttpTimeoutException e) {
            System.err.println("Ошибка: сервис не ответил за " + REQUEST_TIMEOUT.toSeconds() + " сек.");
        } catch (ConnectException e) {
            System.err.println("Ошибка: нет соединения с сервисом, проверьте подключение к интернету");
        } catch (IOException e) {
            System.err.println("Ошибка сети при запросе к сервису: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Ошибка: запрос был прерван");
        }
        return null;
    }

    private static String formatCoordinate(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String describeHttpError(int statusCode) {
        return switch (statusCode) {
            case 400 -> "некорректные параметры запроса (400)";
            case 401, 403 -> "доступ запрещён (" + statusCode + "), проверьте ключ X-Yandex-Weather-Key и тариф";
            case 404 -> "адрес API не найден (404)";
            case 429 -> "превышен лимит запросов по тарифу (429)";
            default -> statusCode >= 500
                    ? "сервис Яндекс Погоды временно недоступен (" + statusCode + "), попробуйте позже"
                    : "неожиданный код ответа " + statusCode;
        };
    }

    private static double readDouble(Scanner scanner, String prompt, double min, double max) {
        while (true) {
            String input = readLine(scanner, prompt).replace(',', '.');
            try {
                double value = Double.parseDouble(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Значение должно быть от " + min + " до " + max + ", попробуйте ещё раз");
            } catch (NumberFormatException e) {
                System.out.println("Это не число, попробуйте ещё раз");
            }
        }
    }

    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            String input = readLine(scanner, prompt);
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Значение должно быть от " + min + " до " + max + ", попробуйте ещё раз");
            } catch (NumberFormatException e) {
                System.out.println("Это не целое число, попробуйте ещё раз");
            }
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new IllegalStateException("Ввод прерван");
        }
        return scanner.nextLine().trim();
    }
}
