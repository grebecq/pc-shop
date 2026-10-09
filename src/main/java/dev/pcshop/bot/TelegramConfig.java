package dev.pcshop.bot;

import dev.pcshop.config.ShopProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Бот включается, только если задан BOT_TOKEN. Без него сайт и API работают как обычно. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${shop.telegram.bot-token:}'.isBlank()")
class TelegramConfig {

    @Bean
    TelegramClient telegramClient(ShopProperties props) {
        return new OkHttpTelegramClient(props.telegram().botToken());
    }

    @Bean(destroyMethod = "close")
    TelegramBotsLongPollingApplication telegramBots(ShopProperties props, ShopBot bot) throws Exception {
        var app = new TelegramBotsLongPollingApplication();
        app.registerBot(props.telegram().botToken(), bot);
        return app;
    }
}
