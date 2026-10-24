package tn.elitetunisia.customer;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    @Query("""
            select new tn.elitetunisia.customer.AdminCustomerSummary(
                c.id, c.firstName, c.lastName, c.phone, c.customerLevel,
                c.user.email, c.user.enabled, c.user.createdAt)
            from CustomerEntity c
            where (:search = ''
                or lower(c.firstName) like lower(concat('%', :search, '%'))
                or lower(c.lastName) like lower(concat('%', :search, '%'))
                or lower(c.user.email) like lower(concat('%', :search, '%'))
                or c.phone like concat('%', :search, '%'))
            order by c.user.createdAt desc
            """)
    Page<AdminCustomerSummary> searchForAdministration(@Param("search") String search, Pageable pageable);
}