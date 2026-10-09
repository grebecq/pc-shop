package dev.pcshop.controller.web;

import dev.pcshop.config.ShopProperties;
import dev.pcshop.enums.Slot;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.ResourceNotFoundException;
import dev.pcshop.model.Build;
import dev.pcshop.model.Issue;
import dev.pcshop.security.ShopUser;
import dev.pcshop.service.BuildService;
import dev.pcshop.service.CompatibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Конфигуратор сборки. Сборка целиком живёт в ссылке (/configurator?cpu=1&mb=5),
 * поэтому ей можно поделиться, а гостю не нужна ни сессия, ни база.
 */
@Controller
@RequiredArgsConstructor
public class ConfiguratorController {

    private final CompatibilityService compatibility;
    private final BuildService builds;
    private final ShopProperties props;

    @GetMapping("/configurator")
    public String configurator(@RequestParam Map<String, String> params, Model model) {
        Build build = builds.fromParams(params);
        List<Issue> issues = compatibility.check(build);

        model.addAttribute("build", build);
        model.addAttribute("slots", Slot.values());
        model.addAttribute("errors", issues.stream().filter(Issue::isError).toList());
        model.addAttribute("warnings", issues.stream().filter(Issue::isWarning).toList());
        model.addAttribute("missing", issues.stream().filter(Issue::isMissing).toList());
        model.addAttribute("complete", compatibility.complete(issues));
        model.addAttribute("draw", compatibility.estimatedDraw(build));
        model.addAttribute("recommendedPsu", compatibility.recommendedPsu(build));
        model.addAttribute("presets", builds.presets());
        model.addAttribute("shareUrl", props.baseUrl() + build.link());
        model.addAttribute("cartIds", build.products().stream().map(p -> p.getId().toString()).collect(Collectors.joining(",")));
        // Слоты с проблемами подсвечиваем прямо в списке
        model.addAttribute("badSlots", issues.stream().filter(Issue::isError)
                .flatMap(i -> i.slots().stream()).filter(build::has).collect(Collectors.toSet()));
        return "build/configurator";
    }

    @GetMapping("/configurator/{slotKey}")
    public String pick(@PathVariable String slotKey, @RequestParam Map<String, String> params, Model model) {
        Slot slot = Slot.byKey(slotKey).orElseThrow(() -> new ResourceNotFoundException("Нет такого слота: " + slotKey));
        Build build = builds.fromParams(params);
        var candidates = builds.candidates(build, slot);

        model.addAttribute("build", build);
        model.addAttribute("slot", slot);
        model.addAttribute("current", build.part(slot));
        model.addAttribute("candidates", candidates);
        model.addAttribute("fitCount", candidates.stream().filter(c -> c.fits()).count());
        return "build/pick";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/configurator/save")
    public String save(@AuthenticationPrincipal ShopUser user, @RequestParam Map<String, String> params,
                @RequestParam(defaultValue = "") String name, RedirectAttributes flash) {
        Build build = builds.fromParams(params);
        try {
            builds.save(user.getAccountId(), name, build);
            flash.addFlashAttribute("message", "Сборка сохранена");
            return "redirect:/account/builds";
        } catch (BadRequestException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:" + build.link();
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/account/builds")
    public String saved(@AuthenticationPrincipal ShopUser user, Model model) {
        model.addAttribute("builds", builds.saved(user.getAccountId()));
        model.addAttribute("tab", "builds");
        return "account/builds";
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/account/builds/{id}/delete")
    public String delete(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        builds.deleteSaved(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Сборка удалена");
        return "redirect:/account/builds";
    }
}
