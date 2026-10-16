package dev.pcshop.service;

import dev.pcshop.dto.request.ReviewRequest;
import dev.pcshop.entity.Review;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.DuplicateResourceException;
import dev.pcshop.repository.OrderRepository;
import dev.pcshop.repository.ReviewRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class ReviewService {

    public enum Permission {
        CAN_WRITE, GUEST, NOT_BOUGHT, ALREADY_WROTE
    }

    private final ReviewRepository reviews;
    private final OrderRepository orders;
    private final AccountService accounts;

    @Transactional(readOnly = true)
    public List<Review> forProduct(long productId) {
        return reviews.findAllByProductIdOrderByCreatedAtDesc(productId);
    }

    @Transactional(readOnly = true)
    public Permission permission(Long accountId, long productId) {
        if (accountId == null) {
            return Permission.GUEST;
        }
        if (reviews.existsByProductIdAndAccountId(productId, accountId)) {
            return Permission.ALREADY_WROTE;
        }
        return orders.hasBought(accountId, productId) ? Permission.CAN_WRITE : Permission.NOT_BOUGHT;
    }

    @Transactional
    public Review add(long accountId, long productId, @NotNull @Valid ReviewRequest request) {
        switch (permission(accountId, productId)) {
            case ALREADY_WROTE -> throw new DuplicateResourceException("Вы уже оставили отзыв об этом товаре");
            case NOT_BOUGHT -> throw new BadRequestException("Отзыв можно оставить после покупки товара");
            default -> { }
        }
        Review review = reviews.save(new Review(productId, accountId, accounts.get(accountId).getName(), request));
        reviews.refreshRating(productId);
        log.info("Новый отзыв о товаре {}: {} из 5", productId, request.rating());
        return review;
    }
}
