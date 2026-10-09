package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.exception.MailDeliveryException;
import dev.pcshop.service.MailService;
import dev.pcshop.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Что подключено у магазина: оплата, почта, бот. Сами ключи и пароли задаются только в .env —
 * здесь их можно проверить, но не увидеть и не поменять.
 */
@PreAuthorize("hasRole('ADMIN')")
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final ShopProperties props;
    private final PaymentService payments;
    private final MailService mail;
    @Value("${spring.mail.host}")
    private final String mailHost;
    @Value("${spring.mail.port}")
    private final int mailPort;
    @Value("${shop.mail.from}")
    private final String mailFrom;

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("props", props);
        model.addAttribute("webhookUrl", props.baseUrl().replaceAll("/+$", "") + "/api/payments/yookassa");
        model.addAttribute("mailServer", mailHost + ":" + mailPort);
        model.addAttribute("mailFrom", mailFrom);
        return "admin/settings";
    }

    @PostMapping("/yookassa")
    public String checkYookassa(RedirectAttributes flash) {
        if (!payments.online()) {
            flash.addFlashAttribute("error", "Ключи ЮKassa не заданы");
            return "redirect:/admin/settings";
        }
        try {
            var shop = payments.shop();
            flash.addFlashAttribute("message", "Ключи работают: магазин " + shop.accountId()
                    + (shop.test() ? ", тестовый — деньги не настоящие" : ", боевой — платежи настоящие"));
        } catch (RestClientException e) {
            flash.addFlashAttribute("error", "ЮKassa не приняла ключи: " + e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/test-mail")
    public String testMail(@RequestParam String email, RedirectAttributes flash) {
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            flash.addFlashAttribute("error", "Укажите адрес почты, например you@example.ru");
            return "redirect:/admin/settings";
        }
        try {
            mail.send(email, "Проверка почты магазина", "Это тестовое письмо из админки. Раз оно дошло, письма покупателям тоже дойдут.");
            flash.addFlashAttribute("message", "Письмо отправлено на " + email + ". Если его нет во входящих, загляните в спам");
        } catch (MailDeliveryException e) {
            // Админу — настоящая причина: неверный пароль, порт закрыт, сервер не найден
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            flash.addFlashAttribute("error", "Письмо не ушло: " + cause.getMessage());
        }
        return "redirect:/admin/settings";
    }
}
