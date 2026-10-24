package tn.elitetunisia.customer;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import tn.elitetunisia.auth.entity.UserEntity;

@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private UserEntity user;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "customer_level", nullable = false, length = 20)
    private String customerLevel = "STANDARD";

    protected CustomerEntity() { }

    public CustomerEntity(UserEntity user, String firstName, String lastName, String phone) {
        this.user = user;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getCustomerLevel() { return customerLevel; }
    public void update(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }
}