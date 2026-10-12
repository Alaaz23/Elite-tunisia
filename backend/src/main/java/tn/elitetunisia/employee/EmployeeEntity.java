package tn.elitetunisia.employee;

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
@Table(name = "employees")
public class EmployeeEntity {
    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private UserEntity user;

    @Column(name = "employee_number", nullable = false, unique = true, length = 40)
    private String employeeNumber;

    @Column(name = "job_title", nullable = false, length = 80)
    private String jobTitle;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 40)
    private String phone;

    protected EmployeeEntity() { }

    public EmployeeEntity(UserEntity user, String employeeNumber, String jobTitle, String firstName, String lastName, String phone) {
        this.user = user;
        this.employeeNumber = employeeNumber;
        this.jobTitle = jobTitle;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getJobTitle() { return jobTitle; }
    public void update(String firstName, String lastName, String phone, String jobTitle) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.jobTitle = jobTitle;
    }
}