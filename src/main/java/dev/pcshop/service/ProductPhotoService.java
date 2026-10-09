package dev.pcshop.service;

import dev.pcshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Фото товаров, загруженные владельцем через админку. */
@Service
@RequiredArgsConstructor
public class ProductPhotoService {

    public static final int MAX_BYTES = 3 * 1024 * 1024;

    private final JdbcClient jdbc;
    private final ProductRepository products;

    public record Photo(String contentType, byte[] data) {
    }

    @Transactional
    public void save(long productId, byte[] data) {
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Фото должно быть не больше 3 МБ");
        }
        // Тип определяем по содержимому файла, а не по имени или заголовку из браузера
        String type = detectType(data);
        if (type == null) {
            throw new IllegalArgumentException("Подойдёт JPEG, PNG или WebP");
        }
        var product = products.findById(productId).orElseThrow();
        jdbc.sql("""
                        INSERT INTO product_photo (product_id, content_type, data, updated_at) VALUES (:id, :type, :data, now())
                        ON CONFLICT (product_id) DO UPDATE SET content_type = :type, data = :data, updated_at = now()""")
                .param("id", productId).param("type", type).param("data", data)
                .update();
        // Версия в ссылке сбрасывает кеш браузера после замены фото
        product.setImageUrl("/media/products/" + productId + "?v=" + System.currentTimeMillis());
    }

    @Transactional
    public void delete(long productId) {
        jdbc.sql("DELETE FROM product_photo WHERE product_id = :id").param("id", productId).update();
        products.findById(productId).ifPresent(p -> p.setImageUrl(null));
    }

    @Transactional(readOnly = true)
    public Optional<Photo> find(long productId) {
        return jdbc.sql("SELECT content_type, data FROM product_photo WHERE product_id = :id")
                .param("id", productId)
                .query((rs, n) -> new Photo(rs.getString(1), rs.getBytes(2)))
                .optional();
    }

    static String detectType(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
