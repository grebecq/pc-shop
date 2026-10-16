package dev.pcshop.controller.web;

import dev.pcshop.service.ProductPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

// Картинки товаров
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final ProductPhotoService photos;

    @GetMapping("/media/products/{id}")
    public ResponseEntity<byte[]> productPhoto(@PathVariable long id) {
        return photos.find(id)
                .map(p -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(MediaType.parseMediaType(p.contentType()))
                        .body(p.data()))
                .orElse(ResponseEntity.notFound().build());
    }
}
