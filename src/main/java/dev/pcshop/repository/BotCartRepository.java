package dev.pcshop.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Позиции корзины Telegram-бота: чат, товар, количество. */
@Repository
@RequiredArgsConstructor
public class BotCartRepository {

    public static final int MAX_QTY = 20;

    private final JdbcClient jdbc;

    public void add(long chatId, long productId) {
        jdbc.sql("""
                        INSERT INTO bot_cart_item (chat_id, product_id, quantity) VALUES (:chat, :product, 1)
                        ON CONFLICT (chat_id, product_id)
                        DO UPDATE SET quantity = LEAST(bot_cart_item.quantity + 1, :max)""")
                .param("chat", chatId).param("product", productId).param("max", MAX_QTY)
                .update();
    }

    public void decrement(long chatId, long productId) {
        int removed = jdbc.sql("DELETE FROM bot_cart_item WHERE chat_id = :chat AND product_id = :product AND quantity <= 1")
                .param("chat", chatId).param("product", productId).update();
        if (removed == 0) {
            jdbc.sql("UPDATE bot_cart_item SET quantity = quantity - 1 WHERE chat_id = :chat AND product_id = :product")
                    .param("chat", chatId).param("product", productId).update();
        }
    }

    public void clear(long chatId) {
        jdbc.sql("DELETE FROM bot_cart_item WHERE chat_id = :chat").param("chat", chatId).update();
    }

    public List<Line> lines(long chatId) {
        return jdbc.sql("SELECT product_id, quantity FROM bot_cart_item WHERE chat_id = :chat ORDER BY product_id")
                .param("chat", chatId)
                .query((rs, n) -> new Line(rs.getLong("product_id"), rs.getInt("quantity")))
                .list();
    }

    public record Line(long productId, int quantity) {
    }
}
