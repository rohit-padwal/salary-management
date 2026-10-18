package com.acme.salarymanagement.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "departments")
@Immutable
public class Department {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected Department() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
