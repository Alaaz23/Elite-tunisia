package tn.elitetunisia.customer;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tn.elitetunisia.user.dto.UserSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/customer/profile")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {
    private final AccountService accounts;

    public CustomerProfileController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public UserSummary get(Authentication authentication) {
        return accounts.customerProfile(authentication.getName());
    }

    @PutMapping
    public UserSummary update(Authentication authentication, @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return accounts.updateCustomerProfile(authentication.getName(), request);
    }
}