package com.acme.salarymanagement.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "currencies")
@Immutable
public class Currency {

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "minor_units", nullable = false)
    private short minorUnits;

    protected Currency() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getMinorUnits() {
        return minorUnits;
    }
}
