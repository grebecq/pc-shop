package dev.pcshop.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** Одноразовые ссылки из писем: случайный токен уходит в письмо, в базе хранится только его SHA-256. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** 32 случайных байта в base64url: в ссылке не нужно ничего экранировать. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Хеш токена из ссылки; пустой токен — пусто. */
    public static Optional<String> hashOf(String token) {
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(sha256(token));
    }

    public static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 есть в любой JVM", e);
        }
    }
}
