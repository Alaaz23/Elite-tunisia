package tn.elitetunisia.category;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tn.elitetunisia.catalogue.dto.CategorySummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;

@RestController
@RequestMapping("/api/categories")
public class PublicCategoryController {
    private final CategoryService categories;
    public PublicCategoryController(CategoryService categories) { this.categories = categories; }

    @GetMapping
    public PagedResponse<CategorySummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return categories.publicList(page, size); }

    @GetMapping("/{id}")
    public CategorySummary get(@PathVariable UUID id) { return categories.getPublic(id); }
}