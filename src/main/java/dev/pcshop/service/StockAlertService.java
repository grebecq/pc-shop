package dev.pcshop.service;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.entity.Account;
import dev.pcshop.entity.Product;
import dev.pcshop.entity.StockAlert;
import dev.pcshop.event.ProductEvents;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.MailDeliveryException;
import dev.pcshop.repository.ProductRepository;
import dev.pcshop.repository.StockAlertRepository;
import dev.pcshop.util.Formats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// Сообщить о поступлении
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertService {

    private static final int MAX_ALERTS = 50;

    private final StockAlertRepository alerts;
    private final ProductRepository products;
    private final MailService mail;
    private final ShopProperties props;
    private final StoreInfoService store;

    @Transactional(readOnly = true)
    public boolean subscribed(long accountId, long productId) {
        return alerts.existsById(new StockAlert.Key(accountId, productId));
    }

    @Transactional
    public boolean toggle(long accountId, long productId) {
        var key = new StockAlert.Key(accountId, productId);
        if (alerts.existsById(key)) {
            alerts.deleteById(key);
            return false;
        }
        Product product = products.findAllByIdInAndActiveTrue(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Этот товар больше не продаётся"));
        if (product.inStock()) {
            throw new BadRequestException("Товар уже в наличии, его можно заказать");
        }
        if (alerts.countByAccountId(accountId) >= MAX_ALERTS) {
            throw new BadRequestException("Вы уже ждёте " + MAX_ALERTS + " товаров, отпишитесь от лишних");
        }
        alerts.save(new StockAlert(accountId, productId));
        return true;
    }

    @Async
    @TransactionalEventListener
    public void onRestocked(ProductEvents.Restocked event) {
        List<Account> waiting = alerts.subscribers(event.productId());
        if (waiting.isEmpty()) {
            return;
        }
        Product product = products.findById(event.productId()).orElse(null);

        if (product == null || !product.isActive() || !product.inStock()) {
            return;
        }
        String link = props.siteUrl() + "/p/" + product.getSlug();
        String shopName = store.get().getName();
        for (Account account : waiting) {
            String text = "Здравствуйте, " + account.getName() + "!\n\n"
                    + "Товар, о котором вы просили сообщить, снова в наличии:\n"
                    + product.getName() + " — " + Formats.price(product.getPrice()) + "\n" + link + "\n\n"
                    + "Товара может быть немного, поэтому не откладывайте покупку.\n\nМагазин «" + shopName + "»";
            try {
                mail.send(account.getEmail(), "Снова в наличии: " + product.getName(), text);
                alerts.deleteById(new StockAlert.Key(account.getId(), product.getId()));
            } catch (MailDeliveryException e) {
                log.warn("Письмо о поступлении товара {} не отправлено, подписка остаётся", product.getId());
            }
        }
    }
}
