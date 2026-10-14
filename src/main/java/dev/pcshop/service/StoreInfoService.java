package dev.pcshop.service;

import dev.pcshop.dto.request.StoreInfoRequest;
import dev.pcshop.entity.StoreInfo;
import dev.pcshop.repository.StoreInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Контакты и реквизиты продавца: читает каждая страница сайта (подвал), меняет владелец в админке. */
@Service
@RequiredArgsConstructor
public class StoreInfoService {

    private final StoreInfoRepository repository;

    @Transactional(readOnly = true)
    public StoreInfo get() {
        // Строку создаёт миграция V16, удалить её из приложения нельзя
        return repository.findById(StoreInfo.ID).orElseThrow();
    }

    @Transactional
    public void update(StoreInfoRequest form) {
        repository.findById(StoreInfo.ID).orElseThrow().apply(form);
    }
}
