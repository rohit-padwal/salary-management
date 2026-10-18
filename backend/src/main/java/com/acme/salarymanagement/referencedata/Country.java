package com.acme.salarymanagement.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "countries")
@Immutable
public class Country {

    @Id
    @Column(length = 2)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected Country() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
