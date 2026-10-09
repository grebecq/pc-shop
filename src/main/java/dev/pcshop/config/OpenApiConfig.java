package dev.pcshop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Описание REST API для Swagger UI: /swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shopOpenApi() {
        return new OpenAPI().info(new Info()
                .title("«Сборка» — API магазина")
                .version("1.0")
                .description("Каталог, фильтры и заказы. Это же API использует корзина сайта. "
                        + "POST-запросы требуют CSRF-токен: его выдаёт сайт в cookie XSRF-TOKEN."));
    }
}
