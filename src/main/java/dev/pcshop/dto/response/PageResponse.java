package dev.pcshop.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/** Страница списка без лишних полей Spring Data. */
public record PageResponse<T>(List<T> items, int page, int totalPages, long total) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }
}
