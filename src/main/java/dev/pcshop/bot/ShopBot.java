package dev.pcshop.bot;

import dev.pcshop.catalog.Attributes;
import dev.pcshop.catalog.CatalogService;
import dev.pcshop.catalog.Category;
import dev.pcshop.catalog.NotFoundException;
import dev.pcshop.catalog.Product;
import dev.pcshop.catalog.ProductQuery;
import dev.pcshop.config.Formats;
import dev.pcshop.config.ShopProperties;
import dev.pcshop.order.Channel;
import dev.pcshop.order.Customer;
import dev.pcshop.order.Order;
import dev.pcshop.order.OrderException;
import dev.pcshop.order.OrderLine;
import dev.pcshop.order.OrderService;
import dev.pcshop.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Telegram-бот магазина: каталог, корзина, оформление и оплата заказа.
 *
 * Навигация по каталогу редактирует одно и то же сообщение, чтобы не засорять чат.
 * Формат callback_data (лимит Telegram — 64 байта):
 * cats · c:{slug}:{page} · p:{id} · a:{id} · cart · i:{id} · d:{id} · clr · co · pay:{uuid}
 */
@Component
@ConditionalOnExpression("!'${shop.telegram.bot-token:}'.isBlank()")
class ShopBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShopBot.class);
    private static final int PAGE_SIZE = 6;

    static final String BTN_CATALOG = "🛒 Каталог";
    static final String BTN_CART = "🧺 Корзина";
    static final String BTN_ORDERS = "📦 Мои заказы";
    static final String BTN_SITE = "🌐 Сайт магазина";
    static final String BTN_CANCEL = "Отменить оформление";

    private final TelegramClient telegram;
    private final CatalogService catalog;
    private final OrderService orders;
    private final BotCart cart;
    private final ShopProperties props;
    private final Duration paymentTimeout;

    /** Незаконченные оформления заказа. В памяти: при перезапуске покупатель просто начнёт заново. */
    private final Map<Long, Checkout> checkouts = new ConcurrentHashMap<>();

    ShopBot(TelegramClient telegram, CatalogService catalog, OrderService orders, BotCart cart, ShopProperties props,
            @Value("${shop.orders.payment-timeout:30m}") Duration paymentTimeout) {
        this.paymentTimeout = paymentTimeout;
        this.telegram = telegram;
        this.catalog = catalog;
        this.orders = orders;
        this.cart = cart;
        this.props = props;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasPreCheckoutQuery()) {
                onPreCheckout(update.getPreCheckoutQuery());
            } else if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                onMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки update {}", update.getUpdateId(), e);
        }
    }

    // ---------- Сообщения ----------

    private void onMessage(Message msg) throws TelegramApiException {
        long chatId = msg.getChatId();

        if (msg.hasSuccessfulPayment()) {
            Order order = orders.markPaid(UUID.fromString(msg.getSuccessfulPayment().getInvoicePayload()));
            if (order.getPaidAt() != null) {
                send(chatId, "✅ Оплата получена. Заказ " + order.number() + " передан в сборку.", mainMenu());
            } else {
                // Деньги пришли, но заказ успел отмениться по таймауту: нужен возврат вручную
                log.warn("Оплата пришла для отменённого заказа {}", order.number());
                send(chatId, "Оплата пришла, но заказ " + order.number() + " уже был отменён. Мы вернём деньги, менеджер свяжется с вами.", mainMenu());
            }
            return;
        }

        String text = msg.hasText() ? msg.getText().trim() : "";
        if (text.equals("/start") || text.equals(BTN_CANCEL)) {
            checkouts.remove(chatId);
            send(chatId, """
                    Привет! Это <b>Сборка</b>, демо-магазин комплектующих для ПК.

                    Здесь можно выбрать товар, положить его в корзину и оформить заказ с <b>тестовой оплатой</b>. \
                    Те же товары и заказы видны на сайте и в админке: всё работает на одном сервере.""", mainMenu());
            return;
        }

        Checkout checkout = checkouts.get(chatId);
        if (checkout != null) {
            continueCheckout(msg, checkout);
            return;
        }

        switch (text) {
            case BTN_CATALOG, "/catalog" -> send(chatId, "Выберите категорию:", categoriesKeyboard());
            case BTN_CART, "/cart" -> send(chatId, cartText(chatId), cartKeyboard(chatId));
            case BTN_ORDERS, "/orders" -> send(chatId, ordersText(chatId), mainMenu());
            case BTN_SITE -> send(chatId, "Сайт магазина: " + props.baseUrl(), mainMenu());
            default -> send(chatId, "Не понял команду. Выберите раздел в меню ниже 👇", mainMenu());
        }
    }

    // ---------- Кнопки под сообщениями ----------

    private void onCallback(CallbackQuery cq) throws TelegramApiException {
        String toast;
        try {
            toast = handleCallback(cq);
        } catch (NotFoundException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // Старая кнопка (товар сняли с продажи) или подделанные данные
            toast = "Кнопка устарела";
        } catch (RuntimeException | TelegramApiException e) {
            log.error("Ошибка обработки кнопки {}", cq.getData(), e);
            toast = "Что-то пошло не так, попробуйте ещё раз";
        }
        // Отвечаем всегда, иначе у кнопки бесконечно крутятся «часики»
        telegram.execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).text(toast).build());
    }

    private String handleCallback(CallbackQuery cq) throws TelegramApiException {
        long chatId = cq.getMessage().getChatId();
        int messageId = cq.getMessage().getMessageId();
        String[] parts = cq.getData().split(":");
        String toast = null;

        switch (parts[0]) {
            case "cats" -> edit(chatId, messageId, "Выберите категорию:", categoriesKeyboard());
            case "c" -> showCategory(chatId, messageId, parts[1], Integer.parseInt(parts[2]));
            case "p" -> showProduct(chatId, messageId, Long.parseLong(parts[1]));
            case "a" -> toast = addToCart(chatId, Long.parseLong(parts[1])) ? "Добавлено в корзину" : "Товара нет в наличии";
            case "cart" -> edit(chatId, messageId, cartText(chatId), cartKeyboard(chatId));
            case "i", "d" -> {
                long productId = Long.parseLong(parts[1]);
                if (parts[0].equals("i")) {
                    if (!addToCart(chatId, productId)) {
                        toast = "Товара нет в наличии";
                    }
                } else {
                    cart.decrement(chatId, productId);
                }
                edit(chatId, messageId, cartText(chatId), cartKeyboard(chatId));
            }
            case "clr" -> {
                cart.clear(chatId);
                edit(chatId, messageId, cartText(chatId), cartKeyboard(chatId));
            }
            case "co" -> {
                if (cart.lines(chatId).isEmpty()) {
                    toast = "Корзина пуста";
                } else {
                    checkouts.put(chatId, new Checkout());
                    send(chatId, "Оформляем заказ. Как вас зовут?", cancelKeyboard());
                }
            }
            case "pay" -> {
                Order order = orders.markPaid(UUID.fromString(parts[1]));
                if (order.getPaidAt() != null) {
                    edit(chatId, messageId, "✅ Заказ " + order.number() + " оплачен (тестовая оплата). Мы начали сборку.", null);
                } else {
                    edit(chatId, messageId, "Заказ " + order.number() + " отменён: оплата не поступила вовремя. Оформите его заново.", null);
                }
            }
            default -> toast = "Кнопка устарела";
        }
        return toast;
    }

    /** Кладёт в корзину только то, что продаётся и есть на складе. */
    private boolean addToCart(long chatId, long productId) {
        boolean available = catalog.productsByIds(List.of(productId)).stream().anyMatch(Product::inStock);
        if (available) {
            cart.add(chatId, productId);
        }
        return available;
    }

    private void onPreCheckout(PreCheckoutQuery q) throws TelegramApiException {
        // Telegram спрашивает, можно ли принять оплату: проверяем, что заказ ещё ждёт её
        boolean ok;
        try {
            ok = orders.byPublicId(UUID.fromString(q.getInvoicePayload())).getStatus() == OrderStatus.AWAITING_PAYMENT;
        } catch (NotFoundException | IllegalArgumentException e) {
            ok = false;
        }
        var answer = AnswerPreCheckoutQuery.builder().preCheckoutQueryId(q.getId()).ok(ok);
        if (!ok) {
            answer.errorMessage("Заказ уже оплачен или отменён");
        }
        telegram.execute(answer.build());
    }

    // ---------- Каталог ----------

    private InlineKeyboardMarkup categoriesKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (Category c : catalog.categories()) {
            row.add(button(c.getName(), "c:" + c.getSlug() + ":0"));
            if (row.size() == 2) {
                rows.add(row);
                row = new InlineKeyboardRow();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(new InlineKeyboardRow(button(BTN_CART, "cart")));
        return new InlineKeyboardMarkup(rows);
    }

    private void showCategory(long chatId, int messageId, String slug, int page) throws TelegramApiException {
        Category category = catalog.category(slug);
        var result = catalog.search(new ProductQuery(slug, Map.of(), null, null, false,
                ProductQuery.Sort.POPULAR, page, PAGE_SIZE));

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Product p : result.getContent()) {
            String label = (p.inStock() ? "" : "⌛ ") + p.getName() + " · " + Formats.price(p.getPrice());
            rows.add(new InlineKeyboardRow(button(label, "p:" + p.getId())));
        }
        if (result.getTotalPages() > 1) {
            InlineKeyboardRow nav = new InlineKeyboardRow();
            if (result.hasPrevious()) {
                nav.add(button("◀", "c:" + slug + ":" + (page - 1)));
            }
            nav.add(button((page + 1) + " / " + result.getTotalPages(), "c:" + slug + ":" + page));
            if (result.hasNext()) {
                nav.add(button("▶", "c:" + slug + ":" + (page + 1)));
            }
            rows.add(nav);
        }
        rows.add(new InlineKeyboardRow(button("« Категории", "cats"), button(BTN_CART, "cart")));

        String text = "<b>" + htmlEscape(category.getName()) + "</b>\n"
                + result.getTotalElements() + " товаров. ⌛ — нет в наличии.";
        edit(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }

    private void showProduct(long chatId, int messageId, long productId) throws TelegramApiException {
        Product p = catalog.productsByIds(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Товар не найден"));

        StringBuilder text = new StringBuilder()
                .append("<b>").append(htmlEscape(p.getName())).append("</b>\n")
                .append(Formats.price(p.getPrice())).append(" · ")
                .append(p.inStock() ? "в наличии " + p.getStock() + " шт." : "нет в наличии").append("\n\n")
                .append(htmlEscape(p.getDescription())).append("\n\n");
        Attributes.readable(p).forEach((k, v) -> text.append(htmlEscape(k)).append(": ").append(htmlEscape(v)).append('\n'));

        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (p.inStock()) {
            rows.add(new InlineKeyboardRow(button("🧺 В корзину", "a:" + p.getId())));
        }
        // Telegram принимает в кнопках-ссылках только публичные адреса, localhost отклоняет
        if (props.baseUrl().startsWith("https://")) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("Открыть на сайте").url(props.baseUrl() + "/p/" + p.getSlug()).build()));
        }
        rows.add(new InlineKeyboardRow(button("« " + p.getCategory().getName(), "c:" + p.getCategory().getSlug() + ":0"),
                button(BTN_CART, "cart")));
        edit(chatId, messageId, text.toString(), new InlineKeyboardMarkup(rows));
    }

    // ---------- Корзина ----------

    private String cartText(long chatId) {
        var lines = cart.lines(chatId);
        if (lines.isEmpty()) {
            return "🧺 Корзина пуста. Загляните в каталог.";
        }
        Map<Long, Product> products = productsFor(lines);
        StringBuilder text = new StringBuilder("<b>Корзина</b>\n\n");
        long total = 0;
        int n = 1;
        for (BotCart.Line line : lines) {
            Product p = products.get(line.productId());
            if (p == null) {
                continue;
            }
            long sum = (long) p.getPrice() * line.quantity();
            total += sum;
            text.append(n++).append(". ").append(htmlEscape(p.getName())).append('\n')
                    .append("    ").append(line.quantity()).append(" × ").append(Formats.price(p.getPrice()))
                    .append(" = ").append(Formats.price(sum)).append('\n');
        }
        return text.append("\n<b>Итого: ").append(Formats.price(total)).append("</b>").toString();
    }

    private InlineKeyboardMarkup cartKeyboard(long chatId) {
        var lines = cart.lines(chatId);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        Map<Long, Product> products = productsFor(lines);
        for (BotCart.Line line : lines) {
            Product p = products.get(line.productId());
            if (p == null) {
                continue;
            }
            rows.add(new InlineKeyboardRow(
                    button("➖", "d:" + p.getId()),
                    button(shorten(p.getName(), 24) + " · " + line.quantity(), "p:" + p.getId()),
                    button("➕", "i:" + p.getId())));
        }
        if (!lines.isEmpty()) {
            rows.add(new InlineKeyboardRow(button("Очистить", "clr"), button("Оформить заказ ✅", "co")));
        }
        rows.add(new InlineKeyboardRow(button("« Каталог", "cats")));
        return new InlineKeyboardMarkup(rows);
    }

    private Map<Long, Product> productsFor(List<BotCart.Line> lines) {
        return catalog.productsByIds(lines.stream().map(BotCart.Line::productId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    // ---------- Оформление заказа: имя → телефон → адрес ----------

    private void continueCheckout(Message msg, Checkout checkout) throws TelegramApiException {
        long chatId = msg.getChatId();
        String text = msg.hasText() ? msg.getText().trim() : "";

        switch (checkout.step) {
            case NAME -> {
                if (text.isEmpty() || text.length() > 128) {
                    send(chatId, "Напишите, пожалуйста, имя текстом.", cancelKeyboard());
                    return;
                }
                checkout.name = text;
                checkout.step = Checkout.Step.PHONE;
                send(chatId, "Телефон для связи? Нажмите кнопку ниже или напишите номер.", phoneKeyboard());
            }
            case PHONE -> {
                String phone = msg.getContact() != null ? "+" + msg.getContact().getPhoneNumber().replace("+", "") : text;
                if (!phone.matches("^[+0-9 ()-]{6,32}$")) {
                    send(chatId, "Не похоже на телефон. Пример: +7 900 000-00-00", phoneKeyboard());
                    return;
                }
                checkout.phone = phone;
                checkout.step = Checkout.Step.ADDRESS;
                send(chatId, "Адрес доставки: город, улица, дом, квартира.", cancelKeyboard());
            }
            case ADDRESS -> {
                if (text.length() < 5 || text.length() > 512) {
                    send(chatId, "Напишите адрес полностью: город, улица, дом.", cancelKeyboard());
                    return;
                }
                checkouts.remove(chatId);
                placeOrder(chatId, new Customer(checkout.name, checkout.phone, text, null));
            }
        }
    }

    private void placeOrder(long chatId, Customer customer) throws TelegramApiException {
        List<OrderLine> lines = cart.lines(chatId).stream()
                .map(l -> new OrderLine(l.productId(), l.quantity())).toList();
        Order order;
        try {
            order = orders.create(Channel.TELEGRAM, customer, lines, chatId);
        } catch (OrderException e) {
            send(chatId, "Не получилось оформить заказ: " + htmlEscape(e.getMessage()) + "\nПоправьте корзину и попробуйте ещё раз.", mainMenu());
            return;
        }
        cart.clear(chatId);

        StringBuilder text = new StringBuilder("<b>Заказ ").append(order.number()).append(" создан</b>\n\n");
        order.getItems().forEach(i -> text.append("• ").append(htmlEscape(i.getName())).append(" × ").append(i.getQuantity()).append('\n'));
        text.append("\nИтого: <b>").append(Formats.price(order.getTotal())).append("</b>\nДоставка: ")
                .append(htmlEscape(order.getAddress()));
        send(chatId, text.toString(), mainMenu());

        if (props.telegram().realPayments()) {
            sendInvoice(chatId, order);
        } else {
            var payButton = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(
                    button("💳 Оплатить " + Formats.price(order.getTotal()) + " (тест)", "pay:" + order.getPublicId()))));
            send(chatId, "Это демо-магазин, поэтому оплата тестовая: деньги не списываются.\n"
                    + "Товар зарезервирован до " + Formats.timeOfDay(order.payBefore(paymentTimeout)) + " по Москве.", payButton);
        }
    }

    private void sendInvoice(long chatId, Order order) throws TelegramApiException {
        String description = order.getItems().stream()
                .map(i -> i.getName() + " × " + i.getQuantity())
                .collect(Collectors.joining(", "));
        telegram.execute(SendInvoice.builder()
                .chatId(chatId)
                .title("Заказ " + order.number())
                .description(shorten(description, 255))
                .payload(order.getPublicId().toString())
                .providerToken(props.telegram().paymentToken())
                .currency("RUB")
                // Telegram ждёт сумму в копейках
                .prices(List.of(new LabeledPrice("Товары", order.getTotal() * 100)))
                .build());
    }

    // ---------- Мои заказы ----------

    private String ordersText(long chatId) {
        List<Order> recent = orders.recentForChat(chatId);
        if (recent.isEmpty()) {
            return "Заказов пока нет.";
        }
        StringBuilder text = new StringBuilder("<b>Ваши последние заказы</b>\n\n");
        for (Order o : recent) {
            text.append(o.number()).append(" от ").append(Formats.dateTime(o.getCreatedAt())).append('\n')
                    .append("    ").append(Formats.price(o.getTotal())).append(" · ").append(o.getStatus().label()).append('\n');
        }
        return text.toString();
    }

    // ---------- Клавиатуры и отправка ----------

    private ReplyKeyboardMarkup mainMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(BTN_CATALOG, BTN_CART))
                .keyboardRow(new KeyboardRow(BTN_ORDERS, BTN_SITE))
                .resizeKeyboard(true)
                .build();
    }

    private ReplyKeyboardMarkup cancelKeyboard() {
        return ReplyKeyboardMarkup.builder().keyboardRow(new KeyboardRow(BTN_CANCEL)).resizeKeyboard(true).build();
    }

    private ReplyKeyboardMarkup phoneKeyboard() {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(new KeyboardRow(KeyboardButton.builder().text("📱 Отправить мой номер").requestContact(true).build()))
                .keyboardRow(new KeyboardRow(BTN_CANCEL))
                .resizeKeyboard(true)
                .build();
    }

    private static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void send(long chatId, String html, ReplyKeyboard keyboard) throws TelegramApiException {
        telegram.execute(SendMessage.builder().chatId(chatId).text(html).parseMode("HTML").replyMarkup(keyboard).build());
    }

    private void edit(long chatId, int messageId, String html, InlineKeyboardMarkup keyboard) throws TelegramApiException {
        try {
            telegram.execute(EditMessageText.builder().chatId(chatId).messageId(messageId)
                    .text(html).parseMode("HTML").replyMarkup(keyboard).build());
        } catch (TelegramApiException e) {
            // Повторное нажатие той же кнопки: Telegram отвечает «message is not modified», это не ошибка
            if (e.getMessage() == null || !e.getMessage().contains("message is not modified")) {
                throw e;
            }
        }
    }

    static final class Checkout {
        enum Step { NAME, PHONE, ADDRESS }

        Step step = Step.NAME;
        String name;
        String phone;
    }
}
