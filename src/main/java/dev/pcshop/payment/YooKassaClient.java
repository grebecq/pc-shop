package dev.pcshop.payment;

import dev.pcshop.config.ShopProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class YooKassaClient {

    private final RestClient http;

    public YooKassaClient(ShopProperties props) {
        var keys = props.yookassa();

        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(keys.apiUrl())

                .requestFactory(new BufferingClientHttpRequestFactory(timeouts))
                .defaultHeaders(h -> {
                    if (keys.enabled()) {
                        h.setBasicAuth(keys.shopId(), keys.secretKey());
                    }
                })
                .build();
    }

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
