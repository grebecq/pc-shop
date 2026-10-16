package dev.pcshop.controller.web;

import dev.pcshop.dto.request.CategoryRequest;
import dev.pcshop.dto.request.ProductRequest;
import dev.pcshop.dto.request.StockRequest;
import dev.pcshop.entity.Category;
import dev.pcshop.entity.Product;
import dev.pcshop.entity.StockMovement;
import dev.pcshop.enums.ProductListFilter;
import dev.pcshop.enums.Slot;
import dev.pcshop.enums.SpecType;
import dev.pcshop.enums.StockReason;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.service.CatalogAdminService;
import dev.pcshop.service.ProductPhotoService;
import dev.pcshop.service.StockService;
import dev.pcshop.util.Strings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@PreAuthorize("hasRole('ADMIN')")
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private static final List<StockReason> STOCK_OPERATIONS = List.of(StockReason.RECEIPT, StockReason.WRITE_OFF, StockReason.COUNT);

    private final CatalogAdminService catalog;
    private final StockService stock;
    private final ProductPhotoService photos;

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String q, @RequestParam(required = false) Long category,
                           @RequestParam(defaultValue = "ALL") ProductListFilter show, @RequestParam(defaultValue = "0") int page,
                           Model model) {
        model.addAttribute("products", catalog.products(q, category, show, page));
        model.addAttribute("q", q);
        model.addAttribute("categoryId", category);
        model.addAttribute("show", show);
        model.addAttribute("filters", ProductListFilter.values());
        model.addAttribute("categories", catalog.categories());
        return "admin/products";
    }

    @GetMapping("/products/new")
    public String newProduct(@RequestParam(required = false) Long category, @RequestParam(required = false) Long copy,
                             Model model, RedirectAttributes flash) {
        List<Category> categories = catalog.categories();
        if (categories.isEmpty()) {
            flash.addFlashAttribute("error", "Сначала добавьте категорию");
            return "redirect:/admin/categories";
        }
        ProductRequest form = copy != null ? ProductRequest.of(catalog.product(copy))
                : ProductRequest.empty(category != null ? category : categories.getFirst().getId());
        return productForm(model, null, form, null);
    }

    @PostMapping("/products")
    public String create(@Valid @ModelAttribute("form") ProductRequest form, BindingResult binding,
                         @RequestParam(required = false) MultipartFile photo, @RequestParam(required = false) String changeCategory,
                         Model model, RedirectAttributes flash) {
        if (changeCategory != null) {
            return productForm(model, null, form, null);
        }
        if (binding.hasErrors()) {
            return productForm(model, null, form, firstError(binding));
        }
        Product product;
        try {
            product = catalog.create(form);
        } catch (BadRequestException e) {
            return productForm(model, null, form, e.getMessage());
        }
        flash.addFlashAttribute("message", "Товар создан");
        savePhoto(product.getId(), photo, flash);
        return "redirect:/admin/products/" + product.getId();
    }

    @GetMapping("/products/{id}")
    public String product(@PathVariable long id, Model model) {
        Product product = catalog.product(id);
        return productForm(model, product, ProductRequest.of(product), null);
    }

    @PostMapping("/products/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") ProductRequest form, BindingResult binding,
                         @RequestParam(required = false) MultipartFile photo, @RequestParam(required = false) String changeCategory,
                         Model model, RedirectAttributes flash) {
        Product product = catalog.product(id);
        if (changeCategory != null) {
            return productForm(model, product, form, null);
        }
        if (binding.hasErrors()) {
            return productForm(model, product, form, firstError(binding));
        }
        try {
            catalog.update(id, form);
        } catch (BadRequestException e) {
            return productForm(model, product, form, e.getMessage());
        }
        flash.addFlashAttribute("message", "Сохранено");
        savePhoto(id, photo, flash);
        return "redirect:/admin/products/" + id;
    }

    private String productForm(Model model, Product product, ProductRequest form, String error) {
        Category category = form.categoryId() != null ? catalog.category(form.categoryId())
                : product != null ? product.getCategory() : catalog.categories().getFirst();
        model.addAttribute("product", product);
        model.addAttribute("form", form);
        model.addAttribute("category", category);
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("suggestions", catalog.suggestions(category));
        model.addAttribute("needed", Slot.byCategory(category.getSlug()).map(Slot::specs).orElse(Set.of()));
        if (error != null) {
            model.addAttribute("error", error);
        }
        if (product != null) {
            model.addAttribute("history", stock.history(product.getId()));
            model.addAttribute("operations", STOCK_OPERATIONS);
        }
        return "admin/product";
    }

    @PostMapping("/products/{id}/stock")
    public String changeStock(@PathVariable long id, @Valid @ModelAttribute StockRequest request, BindingResult binding,
                              RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("stockError", firstError(binding));
            return "redirect:/admin/products/" + id + "#stock";
        }
        String comment = Strings.blankToNull(request.comment());
        try {
            StockMovement movement = switch (request.operation()) {
                case RECEIPT -> stock.receive(id, request.quantity(), comment);
                case WRITE_OFF -> stock.writeOff(id, request.quantity(), comment);
                case COUNT -> stock.count(id, request.quantity(), comment);
                case SALE, RETURN -> throw new BadRequestException("Продажи и возвраты магазин записывает сам");
            };
            flash.addFlashAttribute("stockMessage", movement == null ? "Остаток не изменился"
                    : "Готово, на складе теперь " + movement.getStockAfter() + " шт. Операция записана в журнал");
        } catch (BadRequestException e) {
            flash.addFlashAttribute("stockError", e.getMessage());
        }
        return "redirect:/admin/products/" + id + "#stock";
    }

    @PostMapping("/products/{id}/photo")
    public String uploadPhoto(@PathVariable long id, @RequestParam("photo") MultipartFile file, RedirectAttributes flash) {
        if (savePhoto(id, file, flash)) {
            flash.addFlashAttribute("message", "Фото обновлено");
        }
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/products/{id}/photo/delete")
    public String deletePhoto(@PathVariable long id, RedirectAttributes flash) {
        photos.delete(id);
        flash.addFlashAttribute("message", "Фото удалено");
        return "redirect:/admin/products/" + id;
    }

    private boolean savePhoto(long productId, MultipartFile file, RedirectAttributes flash) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        try {
            photos.save(productId, file.getBytes());
            return true;
        } catch (BadRequestException | IOException e) {
            flash.addFlashAttribute("error", "Фото не загрузилось: " + e.getMessage());
            return false;
        }
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", catalog.categories());
        model.addAttribute("counts", catalog.productCounts());
        return "admin/categories";
    }

    @GetMapping("/categories/new")
    public String newCategory(Model model) {
        int last = catalog.categories().stream().mapToInt(Category::getSortOrder).max().orElse(0);
        return categoryForm(model, null, CategoryRequest.empty(last + 1), null);
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("form") CategoryRequest form, BindingResult binding,
                                 Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            return categoryForm(model, null, form, firstError(binding));
        }
        try {
            Category category = catalog.createCategory(form);
            flash.addFlashAttribute("message", "Категория создана. Теперь в неё можно добавлять товары");
            return "redirect:/admin/categories/" + category.getId();
        } catch (BadRequestException e) {
            return categoryForm(model, null, form, e.getMessage());
        }
    }

    @GetMapping("/categories/{id}")
    public String category(@PathVariable long id, Model model) {
        Category category = catalog.category(id);
        return categoryForm(model, category, CategoryRequest.of(category), null);
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable long id, @Valid @ModelAttribute("form") CategoryRequest form, BindingResult binding,
                                 Model model, RedirectAttributes flash) {
        Category category = catalog.category(id);
        if (binding.hasErrors()) {
            return categoryForm(model, category, form, firstError(binding));
        }
        try {
            catalog.updateCategory(id, form);
        } catch (BadRequestException e) {
            return categoryForm(model, category, form, e.getMessage());
        }
        flash.addFlashAttribute("message", "Сохранено");
        return "redirect:/admin/categories/" + id;
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable long id, RedirectAttributes flash) {
        try {
            catalog.deleteCategory(id);
            flash.addFlashAttribute("message", "Категория удалена");
            return "redirect:/admin/categories";
        } catch (BadRequestException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/categories/" + id;
        }
    }

    private String categoryForm(Model model, Category category, CategoryRequest form, String error) {
        model.addAttribute("category", category);
        model.addAttribute("form", form);
        model.addAttribute("types", SpecType.values());
        model.addAttribute("needed", category == null ? Set.of()
                : Slot.byCategory(category.getSlug()).map(Slot::specs).orElse(Set.of()));
        model.addAttribute("configurator", category != null && Slot.byCategory(category.getSlug()).isPresent());
        model.addAttribute("productCount", category == null ? 0 : catalog.productCounts().getOrDefault(category.getId(), 0L));
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "admin/category";
    }

    private static String firstError(BindingResult binding) {
        return binding.getAllErrors().getFirst().getDefaultMessage();
    }
}
