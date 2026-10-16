package dev.pcshop.payment;

import dev.pcshop.config.ShopProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Запросы к API ЮKassa. Вход — HTTP Basic: shopId и секретный ключ.
 * Ошибки сети и ответы 4xx/5xx летят как RestClientException: что с ними делать, решает PaymentService.
 */
@Component
public class YooKassaClient {

    private final RestClient http;

    public YooKassaClient(ShopProperties props) {
        var keys = props.yookassa();
        // Без таймаутов зависшая платёжка держала бы поток с покупателем
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(keys.apiUrl())
                // Буфер: тело уходит целиком с Content-Length, а не кусками (chunked) — так его примет любой сервер
                .requestFactory(new BufferingClientHttpRequestFactory(timeouts))
                .defaultHeaders(h -> {
                    if (keys.enabled()) {
                        h.setBasicAuth(keys.shopId(), keys.secretKey());
                    }
                })
                .build();
    }

    /**
     * @param idempotenceKey повтор запроса с тем же ключом вернёт тот же платёж, а не создаст второй
     */
    public YooKassa.Payment createPayment(YooKassa.CreatePayment request, String idempotenceKey) {
        return http.post().uri("/payments")
                .header("Idempotence-Key", idempotenceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(YooKassa.Payment.class);
    }

    public YooKassa.Payment payment(String paymentId) {
        return http.get().uri("/payments/{id}", paymentId).retrieve().body(YooKassa.Payment.class);
    }

    /** Чей это магазин и тестовый ли он: проверка ключей со страницы настроек. */
    public YooKassa.Shop me() {
        return http.get().uri("/me").retrieve().body(YooKassa.Shop.class);
    }

    public void refund(YooKassa.Refund refund, String idempotenceKey) {
        http.post().uri("/refunds")
                .header("Idempotence-Key", idempotenceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(refund)
                .retrieve()
                .toBodilessEntity();
    }
}
