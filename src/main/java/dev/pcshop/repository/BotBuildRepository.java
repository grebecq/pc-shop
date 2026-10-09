package dev.pcshop.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Текущая сборка в Telegram-боте: одна на чат, слот → id товара. */
@Repository
@RequiredArgsConstructor
public class BotBuildRepository {

    private final JdbcClient jdbc;

    public Map<String, String> find(long chatId) {
        Map<String, String> items = new LinkedHashMap<>();
        jdbc.sql("SELECT e.key, e.value FROM bot_build b, jsonb_each_text(b.items) e WHERE b.chat_id = :chat")
                .param("chat", chatId)
                .query((rs, n) -> items.put(rs.getString(1), rs.getString(2)))
                .list();
        return items;
    }

    public void save(long chatId, Map<String, Long> items) {
        // Ключи — наши слоты, значения — числа, поэтому JSON собираем без библиотеки
        String json = items.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        jdbc.sql("""
                        INSERT INTO bot_build (chat_id, items) VALUES (:chat, CAST(:items AS jsonb))
                        ON CONFLICT (chat_id) DO UPDATE SET items = EXCLUDED.items""")
                .param("chat", chatId).param("items", json)
                .update();
    }
}
