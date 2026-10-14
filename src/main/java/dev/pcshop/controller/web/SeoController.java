package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Файлы для Яндекса и Google: какие страницы индексировать (robots.txt) и полный список страниц каталога (sitemap.xml). */
@Controller
@RequiredArgsConstructor
public class SeoController {

    // Корзина, кабинет, заказы, поиск и служебные адреса в поиске не нужны: там либо личные данные, либо дубли каталога
    private static final List<String> CLOSED = List.of("/admin", "/account", "/cart", "/order/", "/pay/", "/api/", "/login",
            "/register", "/password/", "/email/", "/search", "/compare", "/cards", "/swagger-ui", "/v3/");
    private static final List<String> INFO_PAGES = List.of("/", "/sale", "/configurator", "/delivery", "/warranty",
            "/contacts", "/offer", "/privacy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final ShopProperties props;
    private final CatalogService catalog;
    private final ProductRepository products;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        var sb = new StringBuilder("User-agent: *\n");
        CLOSED.forEach(path -> sb.append("Disallow: ").append(path).append('\n'));
        return sb.append("\nSitemap: ").append(props.siteUrl()).append("/sitemap.xml\n").toString();
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        var xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        INFO_PAGES.forEach(path -> url(xml, path, null));
        catalog.categories().forEach(c -> url(xml, "/c/" + c.getSlug(), null));
        products.findSitemapLinksByActiveTrueOrderById().forEach(p -> url(xml, "/p/" + p.slug(), p.updatedAt()));
        return xml.append("</urlset>\n").toString();
    }

    private void url(StringBuilder xml, String path, Instant lastModified) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(props.siteUrl() + path)).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(DAY.format(lastModified)).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}
