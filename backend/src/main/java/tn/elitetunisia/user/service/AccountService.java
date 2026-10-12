package tn.elitetunisia.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.customer.CustomerEntity;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.customer.UpdateCustomerProfileRequest;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;
import tn.elitetunisia.user.dto.UserSummary;

@Service
public class AccountService {
    private final UserRepository users;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;

    public AccountService(UserRepository users, CustomerRepository customers, EmployeeRepository employees) {
        this.users = users;
        this.customers = customers;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public UserSummary current(String email) {
        UserEntity user = findByEmail(email);
        return toSummary(user, customers.findById(user.getId()).orElse(null), employees.findById(user.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public UserSummary customerProfile(String email) {
        UserEntity user = findByEmail(email);
        CustomerEntity customer = customers.findById(user.getId()).orElseThrow();
        return toSummary(user, customer, null);
    }

    @Transactional
    public UserSummary updateCustomerProfile(String email, UpdateCustomerProfileRequest request) {
        UserEntity user = findByEmail(email);
        CustomerEntity customer = customers.findById(user.getId()).orElseThrow();
        customer.update(request.firstName().trim(), request.lastName().trim(), request.phone().trim());
        return toSummary(user, customer, null);
    }

    @Transactional(readOnly = true)
    public UserSummary findById(UUID id) {
        UserEntity user = users.findById(id).orElseThrow(() -> new UserNotFoundException());
        return toSummary(user, customers.findById(id).orElse(null), employees.findById(id).orElse(null));
    }

    private UserEntity findByEmail(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new UserNotFoundException());
    }

    private UserSummary toSummary(UserEntity user, CustomerEntity customer, EmployeeEntity employee) {
        String firstName = customer != null ? customer.getFirstName() : employee == null ? null : employee.getFirstName();
        String lastName = customer != null ? customer.getLastName() : employee == null ? null : employee.getLastName();
        String phone = customer != null ? customer.getPhone() : employee == null ? null : employee.getPhone();
        String jobTitle = employee == null ? null : employee.getJobTitle();
        return new UserSummary(user.getId(), firstName, lastName, user.getEmail(), phone, jobTitle,
                user.getRoleKeys(), user.isEnabled() ? "ACTIVE" : "DISABLED");
    }
}