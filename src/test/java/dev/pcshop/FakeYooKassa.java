package dev.pcshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Заглушка ЮKassa
public class FakeYooKassa {

    public static final String SHOP_ID = "123456";
    public static final String SECRET_KEY = "test_secret";

    public record Call(String method, String path, String authorization, String idempotenceKey, Map<String, Object> body) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final Map<String, String> byIdempotenceKey = new ConcurrentHashMap<>();
    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    public FakeYooKassa() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v3/", this::handle);
        server.start();
    }

    public String apiUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3";
    }

    public void succeed(String paymentId) {
        var payment = payments.get(paymentId);
        payment.put("status", "succeeded");
        payment.put("payment_method", Map.of("type", "bank_card", "card", Map.of("last4", "4242", "card_type", "Visa")));
    }

    public Map<String, Object> payment(String paymentId) {
        return payments.get(paymentId);
    }

    public List<Call> calls(String method, String path) {
        return calls.stream().filter(c -> c.method().equals(method) && c.path().equals(path)).toList();
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    @SuppressWarnings("unchecked")
    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring("/v3".length());
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> body = raw.isEmpty() ? Map.of() : JSON.readValue(raw, Map.class);
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String key = ex.getRequestHeaders().getFirst("Idempotence-Key");
        calls.add(new Call(ex.getRequestMethod(), path, auth, key, body));

        if (down) {
            reply(ex, 500, Map.of("type", "error", "code", "internal_server_error"));
            return;
        }
        String expected = "Basic " + Base64.getEncoder().encodeToString((SHOP_ID + ":" + SECRET_KEY).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(auth)) {
            reply(ex, 401, Map.of("type", "error", "code", "invalid_credentials"));
            return;
        }
        if (ex.getRequestMethod().equals("POST") && path.equals("/payments")) {

            String id = byIdempotenceKey.computeIfAbsent(key, k -> {
                String newId = UUID.randomUUID().toString();
                var confirmation = (Map<String, Object>) body.get("confirmation");
                var payment = new LinkedHashMap<String, Object>();
                payment.put("id", newId);
                payment.put("status", "pending");
                payment.put("amount", body.get("amount"));
                payment.put("description", body.get("description"));
                payment.put("metadata", body.get("metadata"));
                payment.put("confirmation", Map.of("type", "redirect", "return_url", confirmation.get("return_url"),
                        "confirmation_url", "https://yoomoney.test/checkout?orderId=" + newId));
                payment.put("test", true);
                payments.put(newId, payment);
                return newId;
            });
            reply(ex, 200, payments.get(id));
        } else if (ex.getRequestMethod().equals("GET") && path.startsWith("/payments/")) {
            var payment = payments.get(path.substring("/payments/".length()));
            reply(ex, payment == null ? 404 : 200, payment == null ? Map.of("type", "error", "code", "not_found") : payment);
        } else if (ex.getRequestMethod().equals("POST") && path.equals("/refunds")) {
            var payment = payments.get((String) body.get("payment_id"));
            payment.put("refunded_amount", body.get("amount"));
            reply(ex, 200, Map.of("id", UUID.randomUUID().toString(), "status", "succeeded", "amount", body.get("amount")));
        } else if (ex.getRequestMethod().equals("POST") && path.equals("/receipts")) {
            reply(ex, 200, Map.of("id", UUID.randomUUID().toString(), "type", "payment", "status", "pending"));
        } else if (ex.getRequestMethod().equals("GET") && path.equals("/me")) {
            reply(ex, 200, Map.of("account_id", SHOP_ID, "test", true, "status", "enabled"));
        } else {
            reply(ex, 404, Map.of("type", "error", "code", "not_found"));
        }
    }

    private static void reply(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
