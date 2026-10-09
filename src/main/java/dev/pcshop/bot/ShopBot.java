package dev.pcshop.bot;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.dto.request.CustomerRequest;
import dev.pcshop.dto.request.OrderLineRequest;
import dev.pcshop.dto.request.ProductSearchRequest;
import dev.pcshop.dto.response.CandidateResponse;
import dev.pcshop.dto.response.PresetResponse;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Order;
import dev.pcshop.entity.Product;
import dev.pcshop.enums.Channel;
import dev.pcshop.enums.OrderStatus;
import dev.pcshop.enums.Slot;
import dev.pcshop.exception.OrderException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.model.Build;
import dev.pcshop.model.Issue;
import dev.pcshop.repository.BotCartRepository;
import dev.pcshop.service.BotCartService;
import dev.pcshop.service.BuildService;
import dev.pcshop.service.CatalogService;
import dev.pcshop.service.CompatibilityService;
import dev.pcshop.service.OrderService;
import dev.pcshop.util.Attributes;
import dev.pcshop.util.Formats;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Конфигуратор: b · bs:{слот}:{page} · bp:{слот}:{id} · br:{слот} · bpl · bpre:{n} · bcart · bclr
 */
@Component
@ConditionalOnExpression("!'${shop.telegram.bot-token:}'.isBlank()")
@Slf4j
@RequiredArgsConstructor
public class ShopBot implements LongPollingSingleThreadUpdateConsumer {

    private static final int PAGE_SIZE = 6;

    public static final String BTN_CATALOG = "🛒 Каталог";
    public static final String BTN_CART = "🧺 Корзина";
    public static final String BTN_ORDERS = "📦 Мои заказы";
    public static final String BTN_SITE = "🌐 Сайт магазина";
    public static final String BTN_BUILD = "🛠 Собрать ПК";
    public static final String BTN_CANCEL = "Отменить оформление";

    private final TelegramClient telegram;
    private final CatalogService catalog;
    private final OrderService orders;
    private final BotCartService cart;
    private final BuildService builds;
    private final CompatibilityService compatibility;
    private final ShopProperties props;
    @Value("${shop.orders.payment-timeout:30m}")
    private final Duration paymentTimeout;

    /** Незаконченные оформления заказа. В памяти: при перезапуске покупатель просто начнёт заново. */
    private final Map<Long, Checkout> checkouts = new ConcurrentHashMap<>();

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
                    Привет! Это <b>Сборка</b>, магазин комплектующих для ПК.

                    Выберите товар в каталоге или соберите целый компьютер в «🛠 Собрать ПК»: бот сам проверит, \
                    что детали подходят друг к другу. Заказ можно оформить и оплатить прямо здесь.""", mainMenu());
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
            case BTN_BUILD, "/build" -> {
                Build build = builds.botBuild(chatId);
                send(chatId, buildText(build), buildKeyboard(build));
            }
            default -> send(chatId, "Не понял команду. Выберите раздел в меню ниже 👇", mainMenu());
        }
    }

    // ---------- Кнопки под сообщениями ----------

    private void onCallback(CallbackQuery cq) throws TelegramApiException {
        String toast;
        try {
            toast = handleCallback(cq);
        } catch (ResourceNotFoundException | IllegalArgumentException | IndexOutOfBoundsException e) {
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
            case "b" -> showBuild(chatId, messageId, builds.botBuild(chatId));
            case "bs" -> showSlot(chatId, messageId, slot(parts[1]), Integer.parseInt(parts[2]));
            case "bp" -> {
                Slot slot = slot(parts[1]);
                Map<String, Long> ids = new java.util.LinkedHashMap<>(builds.botBuild(chatId).ids());
                ids.put(slot.key(), Long.parseLong(parts[2]));
                Build build = builds.fromParams(ids);
                if (!build.has(slot)) {
                    throw new ResourceNotFoundException("Товар не найден");
                }
                builds.saveBotBuild(chatId, build);
                showBuild(chatId, messageId, build);
            }
            case "br" -> {
                Build build = builds.botBuild(chatId).without(slot(parts[1]));
                builds.saveBotBuild(chatId, build);
                showBuild(chatId, messageId, build);
            }
            case "bpl" -> showPresets(chatId, messageId);
            case "bpre" -> {
                Build build = builds.presets().get(Integer.parseInt(parts[1])).build();
                builds.saveBotBuild(chatId, build);
                showBuild(chatId, messageId, build);
            }
            case "bclr" -> {
                builds.saveBotBuild(chatId, Build.empty());
                showBuild(chatId, messageId, Build.empty());
            }
            case "bcart" -> {
                List<Product> buildParts = builds.botBuild(chatId).products();
                int added = 0;
                for (Product p : buildParts) {
                    if (addToCart(chatId, p.getId())) {
                        added++;
                    }
                }
                toast = added == buildParts.size() ? "Сборка в корзине" : "Добавлено " + added + " из " + buildParts.size() + ": остального нет в наличии";
                edit(chatId, messageId, cartText(chatId), cartKeyboard(chatId));
            }
            default -> toast = "Кнопка устарела";
        }
        return toast;
    }

    private static Slot slot(String key) {
        return Slot.byKey(key).orElseThrow(() -> new IllegalArgumentException("Нет слота " + key));
    }

    // ---------- Конфигуратор ----------

    private String buildText(Build build) {
        StringBuilder text = new StringBuilder("<b>🛠 Сборка ПК</b>\n");
        if (build.isEmpty()) {
            text.append("Выбирайте детали по кнопкам ниже, бот покажет только совместимые. ")
                    .append("Или начните с готовой сборки.\n");
        }
        text.append('\n');
        for (Slot slot : Slot.values()) {
            text.append(htmlEscape(slot.title())).append(": ");
            build.get(slot).ifPresentOrElse(
                    p -> text.append(htmlEscape(p.getName())).append(" · ").append(Formats.price(p.getPrice())),
                    () -> text.append("—"));
            text.append('\n');
        }
        if (build.isEmpty()) {
            return text.toString();
        }
        List<Issue> issues = compatibility.check(build);
        text.append("\n<b>Итого: ").append(Formats.price(build.total())).append("</b>\n")
                .append("Потребление ≈ ").append(compatibility.estimatedDraw(build)).append(" Вт, блок питания от ")
                .append(compatibility.recommendedPsu(build)).append(" Вт\n\n");
        if (compatibility.complete(issues)) {
            text.append("✅ Сборка совместима и готова к заказу\n");
        }
        for (Issue i : issues) {
            text.append(i.isError() ? "❌ " : i.isWarning() ? "⚠️ " : "• ").append(htmlEscape(i.text())).append('\n');
        }
        return text.toString();
    }

    private InlineKeyboardMarkup buildKeyboard(Build build) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String label = build.get(slot).map(p -> slot.title() + ": " + shorten(p.getName(), 22))
                    .orElse("➕ " + slot.title());
            rows.add(new InlineKeyboardRow(button(label, "bs:" + slot.key() + ":0")));
        }
        if (!build.isEmpty()) {
            rows.add(new InlineKeyboardRow(button("🧺 Всё в корзину", "bcart")));
        }
        InlineKeyboardRow last = new InlineKeyboardRow(button("Готовые сборки", "bpl"));
        if (!build.isEmpty()) {
            last.add(button("Очистить", "bclr"));
        }
        rows.add(last);
        if (!build.isEmpty() && props.baseUrl().startsWith("https://")) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text("Открыть на сайте").url(props.baseUrl() + build.link()).build()));
        }
        return new InlineKeyboardMarkup(rows);
    }

    private void showBuild(long chatId, int messageId, Build build) throws TelegramApiException {
        edit(chatId, messageId, buildText(build), buildKeyboard(build));
    }

    /** Список деталей для слота: только совместимые и в наличии, чтобы в чате не было тупиков. */
    private void showSlot(long chatId, int messageId, Slot slot, int page) throws TelegramApiException {
        Build build = builds.botBuild(chatId);
        List<CandidateResponse> all = builds.candidates(build, slot);
        List<CandidateResponse> fit = all.stream().filter(c -> c.fits() && c.product().inStock()).toList();
        int pages = Math.max(1, (fit.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 0), pages - 1);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CandidateResponse c : fit.subList(current * PAGE_SIZE, Math.min(fit.size(), (current + 1) * PAGE_SIZE))) {
            Product p = c.product();
            String mark = build.get(slot).filter(x -> x.getId().equals(p.getId())).isPresent() ? "✅ "
                    : c.note() != null ? "⚠️ " : "";
            rows.add(new InlineKeyboardRow(button(mark + shorten(p.getName(), 30) + " · " + Formats.price(p.getPrice()),
                    "bp:" + slot.key() + ":" + p.getId())));
        }
        if (pages > 1) {
            InlineKeyboardRow nav = new InlineKeyboardRow();
            if (current > 0) {
                nav.add(button("◀", "bs:" + slot.key() + ":" + (current - 1)));
            }
            nav.add(button((current + 1) + " / " + pages, "bs:" + slot.key() + ":" + current));
            if (current < pages - 1) {
                nav.add(button("▶", "bs:" + slot.key() + ":" + (current + 1)));
            }
            rows.add(nav);
        }
        InlineKeyboardRow back = new InlineKeyboardRow();
        if (build.has(slot)) {
            back.add(button("Убрать", "br:" + slot.key()));
        }
        back.add(button("« К сборке", "b"));
        rows.add(back);

        String text = "<b>" + htmlEscape(slot.title()) + "</b>\n";
        text += fit.isEmpty()
                ? "С выбранными деталями ничего не подходит. Поменяйте другие детали или уберите их."
                : "Подходят " + fit.size() + " из " + all.size() + " с учётом уже выбранного. ⚠️ — подходит, но есть нюанс.";
        edit(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }

    private void showPresets(long chatId, int messageId) throws TelegramApiException {
        List<PresetResponse> presets = builds.presets();
        StringBuilder text = new StringBuilder("<b>Готовые сборки</b>\nВозьмите за основу и поменяйте то, что нужно.\n\n");
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < presets.size(); i++) {
            PresetResponse pr = presets.get(i);
            text.append("<b>").append(htmlEscape(pr.name())).append("</b> · ").append(Formats.price(pr.build().total()))
                    .append('\n').append(htmlEscape(pr.purpose())).append("\n\n");
            rows.add(new InlineKeyboardRow(button(pr.name() + " · " + Formats.price(pr.build().total()), "bpre:" + i)));
        }
        rows.add(new InlineKeyboardRow(button("« К сборке", "b")));
        edit(chatId, messageId, text.toString(), new InlineKeyboardMarkup(rows));
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
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
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
        var result = catalog.search(new ProductSearchRequest(slug, Map.of(), null, null, false,
                ProductSearchRequest.Sort.POPULAR, page, PAGE_SIZE));

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
                .orElseThrow(() -> new ResourceNotFoundException("Товар не найден"));

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
        for (BotCartRepository.Line line : lines) {
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
        for (BotCartRepository.Line line : lines) {
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

    private Map<Long, Product> productsFor(List<BotCartRepository.Line> lines) {
        return catalog.productsByIds(lines.stream().map(BotCartRepository.Line::productId).toList()).stream()
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
                placeOrder(chatId, new CustomerRequest(checkout.name, checkout.phone, text, null));
            }
        }
    }

    private void placeOrder(long chatId, CustomerRequest customer) throws TelegramApiException {
        List<OrderLineRequest> lines = cart.lines(chatId).stream()
                .map(l -> new OrderLineRequest(l.productId(), l.quantity())).toList();
        Order order;
        try {
            order = orders.create(Channel.TELEGRAM, customer, lines, chatId);
        } catch (OrderException e) {
            send(chatId, "Не получилось оформить заказ: " + htmlEscape(e.getMessage()) + "\nПоправьте корзину и попробуйте ещё раз.", mainMenu());
            return;
        } catch (ConstraintViolationException e) {
            // Сюда попадаем, только если проверки шагов выше разошлись с правилами CustomerRequest
            String reason = e.getConstraintViolations().stream().findFirst().map(ConstraintViolation::getMessage).orElse("проверьте данные");
            send(chatId, "Не получилось оформить заказ: " + htmlEscape(reason) + "\nНачните оформление заново.", mainMenu());
            return;
        }
        cart.clear(chatId);

        StringBuilder text = new StringBuilder("<b>Заказ ").append(order.number()).append(" создан</b>\n\n");
        order.getItems().forEach(i -> text.append("• ").append(htmlEscape(i.getName())).append(" × ").append(i.getQuantity()).append('\n'));
        text.append("\nДоставка: ").append(order.getDeliveryPrice() == 0 ? "бесплатно" : Formats.price(order.getDeliveryPrice()))
                .append("\nИтого: <b>").append(Formats.price(order.getTotal())).append("</b>\nАдрес: ")
                .append(htmlEscape(order.getAddress()));
        send(chatId, text.toString(), mainMenu());

        if (props.telegram().realPayments()) {
            sendInvoice(chatId, order);
        } else {
            var payButton = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(
                    button("💳 Оплатить " + Formats.price(order.getTotal()) + " (тест)", "pay:" + order.getPublicId()))));
            send(chatId, "Оплата пока тестовая: деньги не списываются.\n"
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
                .prices(invoicePrices(order))
                .build());
    }

    /** Строки счёта в Telegram: товары и, если платная, доставка. Сумма строк равна total заказа. */
    private static List<LabeledPrice> invoicePrices(Order order) {
        var prices = new ArrayList<LabeledPrice>();
        prices.add(new LabeledPrice("Товары", order.itemsTotal() * 100));
        if (order.getDeliveryPrice() > 0) {
            prices.add(new LabeledPrice("Доставка", order.getDeliveryPrice() * 100));
        }
        return prices;
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
                .keyboardRow(new KeyboardRow(BTN_BUILD, BTN_ORDERS))
                .keyboardRow(new KeyboardRow(BTN_SITE))
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
