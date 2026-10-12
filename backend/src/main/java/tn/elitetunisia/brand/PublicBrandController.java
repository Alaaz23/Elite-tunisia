package tn.elitetunisia.brand;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tn.elitetunisia.catalogue.dto.BrandSummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;

@RestController
@RequestMapping("/api/brands")
public class PublicBrandController {
    private final BrandService brands;
    public PublicBrandController(BrandService brands) { this.brands = brands; }

    @GetMapping
    public PagedResponse<BrandSummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return brands.publicList(page, size); }

    @GetMapping("/{id}")
    public BrandSummary get(@PathVariable UUID id) { return brands.getPublic(id); }
}