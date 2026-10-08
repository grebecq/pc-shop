package dev.pcshop.media;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
class MediaController {

    private final ProductPhotos photos;

    MediaController(ProductPhotos photos) {
        this.photos = photos;
    }

    @GetMapping("/media/products/{id}")
    ResponseEntity<byte[]> productPhoto(@PathVariable long id) {
        return photos.find(id)
                .map(p -> ResponseEntity.ok()
                        // В ссылке есть версия (?v=...), поэтому кешировать можно надолго
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(MediaType.parseMediaType(p.contentType()))
                        .body(p.data()))
                .orElse(ResponseEntity.notFound().build());
    }
}
