package dev.pcshop.bot;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Корзина Telegram-бота. Хранится в базе, чтобы не теряться при перезапуске. */
@Component
class BotCart {

    static final int MAX_QTY = 20;

    private final JdbcClient jdbc;

    BotCart(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void add(long chatId, long productId) {
        jdbc.sql("""
                        INSERT INTO bot_cart_item (chat_id, product_id, quantity) VALUES (:chat, :product, 1)
                        ON CONFLICT (chat_id, product_id)
                        DO UPDATE SET quantity = LEAST(bot_cart_item.quantity + 1, :max)""")
                .param("chat", chatId).param("product", productId).param("max", MAX_QTY)
                .update();
    }

    @Transactional
    void decrement(long chatId, long productId) {
        int removed = jdbc.sql("DELETE FROM bot_cart_item WHERE chat_id = :chat AND product_id = :product AND quantity <= 1")
                .param("chat", chatId).param("product", productId).update();
        if (removed == 0) {
            jdbc.sql("UPDATE bot_cart_item SET quantity = quantity - 1 WHERE chat_id = :chat AND product_id = :product")
                    .param("chat", chatId).param("product", productId).update();
        }
    }

    void clear(long chatId) {
        jdbc.sql("DELETE FROM bot_cart_item WHERE chat_id = :chat").param("chat", chatId).update();
    }

    List<Line> lines(long chatId) {
        return jdbc.sql("SELECT product_id, quantity FROM bot_cart_item WHERE chat_id = :chat ORDER BY product_id")
                .param("chat", chatId)
                .query((rs, n) -> new Line(rs.getLong("product_id"), rs.getInt("quantity")))
                .list();
    }

    record Line(long productId, int quantity) {
    }
}
