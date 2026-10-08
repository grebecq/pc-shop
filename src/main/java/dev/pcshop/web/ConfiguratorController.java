package dev.pcshop.web;

import dev.pcshop.account.AccountException;
import dev.pcshop.account.ShopUser;
import dev.pcshop.build.Build;
import dev.pcshop.build.BuildService;
import dev.pcshop.build.Compatibility;
import dev.pcshop.build.Issue;
import dev.pcshop.build.Slot;
import dev.pcshop.catalog.NotFoundException;
import dev.pcshop.config.ShopProperties;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
class ConfiguratorController {

    private final BuildService builds;
    private final ShopProperties props;

    ConfiguratorController(BuildService builds, ShopProperties props) {
        this.builds = builds;
        this.props = props;
    }

    @GetMapping("/configurator")
    String configurator(@RequestParam Map<String, String> params, Model model) {
        Build build = builds.fromParams(params);
        List<Issue> issues = Compatibility.check(build);

        model.addAttribute("build", build);
        model.addAttribute("slots", Slot.values());
        model.addAttribute("errors", issues.stream().filter(Issue::isError).toList());
        model.addAttribute("warnings", issues.stream().filter(Issue::isWarning).toList());
        model.addAttribute("missing", issues.stream().filter(Issue::isMissing).toList());
        model.addAttribute("complete", Compatibility.complete(issues));
        model.addAttribute("draw", Compatibility.estimatedDraw(build));
        model.addAttribute("recommendedPsu", Compatibility.recommendedPsu(build));
        model.addAttribute("presets", builds.presets());
        model.addAttribute("shareUrl", props.baseUrl() + build.link());
        model.addAttribute("cartIds", build.products().stream().map(p -> p.getId().toString()).collect(Collectors.joining(",")));
        // Слоты с проблемами подсвечиваем прямо в списке
        model.addAttribute("badSlots", issues.stream().filter(Issue::isError)
                .flatMap(i -> i.slots().stream()).filter(build::has).collect(Collectors.toSet()));
        return "build/configurator";
    }

    @GetMapping("/configurator/{slotKey}")
    String pick(@PathVariable String slotKey, @RequestParam Map<String, String> params, Model model) {
        Slot slot = Slot.byKey(slotKey).orElseThrow(() -> new NotFoundException("Нет такого слота: " + slotKey));
        Build build = builds.fromParams(params);
        var candidates = builds.candidates(build, slot);

        model.addAttribute("build", build);
        model.addAttribute("slot", slot);
        model.addAttribute("current", build.part(slot));
        model.addAttribute("candidates", candidates);
        model.addAttribute("fitCount", candidates.stream().filter(c -> c.fits()).count());
        return "build/pick";
    }

    @PostMapping("/configurator/save")
    String save(@AuthenticationPrincipal ShopUser user, @RequestParam Map<String, String> params,
                @RequestParam(defaultValue = "") String name, RedirectAttributes flash) {
        Build build = builds.fromParams(params);
        try {
            builds.save(user.getAccountId(), name, build);
            flash.addFlashAttribute("message", "Сборка сохранена");
            return "redirect:/account/builds";
        } catch (AccountException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:" + build.link();
        }
    }

    @GetMapping("/account/builds")
    String saved(@AuthenticationPrincipal ShopUser user, Model model) {
        model.addAttribute("builds", builds.saved(user.getAccountId()));
        model.addAttribute("tab", "builds");
        return "account/builds";
    }

    @PostMapping("/account/builds/{id}/delete")
    String delete(@AuthenticationPrincipal ShopUser user, @PathVariable long id, RedirectAttributes flash) {
        builds.deleteSaved(user.getAccountId(), id);
        flash.addFlashAttribute("message", "Сборка удалена");
        return "redirect:/account/builds";
    }
}
