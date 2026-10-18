package com.acme.salarymanagement.employee;

import java.math.BigDecimal;
import java.time.Instant;

import com.acme.salarymanagement.referencedata.Country;
import com.acme.salarymanagement.referencedata.Currency;
import com.acme.salarymanagement.referencedata.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Entity
@Table(name = "employees")
class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_number", nullable = false, length = 32)
    private String employeeNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    @Column(name = "salary_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal salaryAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_code", nullable = false)
    private Currency currency;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    Employee(ResolvedEmployeeData data, Instant changedAt) {
        replaceWith(data, changedAt);
    }

    void requireVersion(long originalVersion) {
        if (version != originalVersion) {
            throw new StaleEmployeeException();
        }
    }

    boolean hasSameDetails(ResolvedEmployeeData data) {
        return employeeNumber.equals(data.input().employeeNumber())
                && fullName.equals(data.input().fullName())
                && department.getId().equals(data.department().getId())
                && country.getCode().equals(data.country().getCode())
                && currency.getCode().equals(data.currency().getCode())
                && salaryAmount.compareTo(data.salary()) == 0;
    }

    void replaceWith(ResolvedEmployeeData data, Instant changedAt) {
        employeeNumber = data.input().employeeNumber();
        fullName = data.input().fullName();
        department = data.department();
        country = data.country();
        currency = data.currency();
        salaryAmount = data.salary();
        updatedAt = changedAt;
    }

    EmployeeResult toResult() {
        return new EmployeeResult(id, employeeNumber, fullName, department.getId(), department.getName(),
                country.getCode(), country.getName(), salaryAmount.setScale(currency.getMinorUnits()),
                currency.getCode(), version, createdAt, updatedAt);
    }
}
