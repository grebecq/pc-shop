package dev.pcshop.config;

import dev.pcshop.bot.ShopBot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Бот включается, только если задан BOT_TOKEN. Без него сайт и API работают как обычно. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${shop.telegram.bot-token:}'.isBlank()")
public class TelegramConfig {

    @Bean
    public TelegramClient telegramClient(ShopProperties props) {
        return new OkHttpTelegramClient(props.telegram().botToken());
    }

    @Bean(destroyMethod = "close")
    public TelegramBotsLongPollingApplication telegramBots(ShopProperties props, ShopBot bot) throws Exception {
        var app = new TelegramBotsLongPollingApplication();
        app.registerBot(props.telegram().botToken(), bot);
        return app;
    }
}
