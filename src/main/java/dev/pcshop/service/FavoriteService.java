package dev.pcshop.service;

import dev.pcshop.entity.Favorite;
import dev.pcshop.entity.Product;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.repository.FavoriteRepository;
import dev.pcshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Избранное покупателя на сайте. */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    /** Чтобы список не рос бесконечно. */
    private static final int MAX_FAVORITES = 200;

    private final FavoriteRepository favorites;
    private final ProductRepository products;

    /** Добавляет товар в избранное или убирает его оттуда. Возвращает true, если товар теперь в избранном. */
    @Transactional
    public boolean toggle(long accountId, long productId) {
        var key = new Favorite.Key(accountId, productId);
        if (favorites.existsById(key)) {
            favorites.deleteById(key);
            return false;
        }
        if (products.findAllByIdInAndActiveTrue(List.of(productId)).isEmpty()) {
            throw new BadRequestException("Этот товар больше не продаётся");
        }
        if (favorites.countByAccountId(accountId) >= MAX_FAVORITES) {
            throw new BadRequestException("В избранном уже " + MAX_FAVORITES + " товаров, уберите лишние");
        }
        favorites.save(new Favorite(accountId, productId));
        return true;
    }

    @Transactional(readOnly = true)
    public Set<Long> productIds(long accountId) {
        return favorites.productIds(accountId);
    }

    /** Товары из избранного, недавно добавленные первыми. Снятые с продажи не показываем. */
    @Transactional(readOnly = true)
    public List<Product> products(long accountId) {
        List<Long> ids = favorites.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream().map(Favorite::getProductId).toList();
        Map<Long, Product> byId = products.findAllByIdInAndActiveTrue(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
