package dev.pcshop.service;

import dev.pcshop.repository.BotCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Корзина Telegram-бота. Хранится в базе, чтобы не теряться при перезапуске. */
@Service
@RequiredArgsConstructor
public class BotCartService {

    private final BotCartRepository botCartRepository;

    @Transactional
    public void add(long chatId, long productId) {
        botCartRepository.add(chatId, productId);
    }

    @Transactional
    public void decrement(long chatId, long productId) {
        botCartRepository.decrement(chatId, productId);
    }

    @Transactional
    public void clear(long chatId) {
        botCartRepository.clear(chatId);
    }

    @Transactional(readOnly = true)
    public List<BotCartRepository.Line> lines(long chatId) {
        return botCartRepository.lines(chatId);
    }
}
