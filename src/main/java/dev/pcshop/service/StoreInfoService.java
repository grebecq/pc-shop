package dev.pcshop.service;

import dev.pcshop.dto.request.StoreInfoRequest;
import dev.pcshop.entity.StoreInfo;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.repository.StoreInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreInfoService {

    public static final int MAX_LOGO_BYTES = 1024 * 1024;

    private final StoreInfoRepository repository;
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public StoreInfo get() {
        return repository.findById(StoreInfo.ID).orElseThrow();
    }

    @Transactional
    public void update(StoreInfoRequest form) {
        repository.findById(StoreInfo.ID).orElseThrow().apply(form);
    }

    @Transactional
    public void saveLogo(byte[] data) {
        if (data.length == 0 || data.length > MAX_LOGO_BYTES) {
            throw new BadRequestException("Логотип должен быть не больше 1 МБ");
        }
        String type = ProductPhotoService.detectType(data);
        if (type == null) {
            throw new BadRequestException("Подойдёт PNG, JPEG или WebP");
        }
        jdbc.sql("""
                        INSERT INTO store_logo (content_type, data) VALUES (:type, :data)
                        ON CONFLICT (id) DO UPDATE SET content_type = :type, data = :data""")
                .param("type", type).param("data", data)
                .update();
        repository.findById(StoreInfo.ID).orElseThrow().logoChanged(System.currentTimeMillis());
    }

    @Transactional
    public void deleteLogo() {
        jdbc.sql("DELETE FROM store_logo").update();
        repository.findById(StoreInfo.ID).orElseThrow().logoChanged(null);
    }

    @Transactional(readOnly = true)
    public Optional<ProductPhotoService.Photo> logo() {
        return jdbc.sql("SELECT content_type, data FROM store_logo")
                .query((rs, n) -> new ProductPhotoService.Photo(rs.getString(1), rs.getBytes(2)))
                .optional();
    }
}
