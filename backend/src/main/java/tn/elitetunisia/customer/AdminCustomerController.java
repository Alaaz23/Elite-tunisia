package tn.elitetunisia.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasAuthority('customers:read')")
@Validated
public class AdminCustomerController {
    private final CustomerRepository customers;

    public AdminCustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    @GetMapping
    public Page<AdminCustomerSummary> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return customers.searchForAdministration(search.strip(), PageRequest.of(page, size));
    }
}
