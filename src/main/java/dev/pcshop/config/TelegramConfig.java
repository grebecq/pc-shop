package dev.pcshop.config;

import dev.pcshop.bot.ShopBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${shop.telegram.bot-token:}'.isBlank()")
public class TelegramConfig {

    @Bean
    public TelegramClient telegramClient(ShopProperties props) {
        return new OkHttpTelegramClient(props.telegram().botToken());
    }

    @Bean(destroyMethod = "close")
    public TelegramBotsLongPollingApplication telegramBots(ShopProperties props, ShopBot bot) {
        var app = new TelegramBotsLongPollingApplication();
        try {
            app.registerBot(props.telegram().botToken(), bot);
        } catch (TelegramApiException e) {
            log.warn("Бот не запущен: нет связи с api.telegram.org ({}). Сайт работает без бота", e.getMessage());
        }
        return app;
    }
}
