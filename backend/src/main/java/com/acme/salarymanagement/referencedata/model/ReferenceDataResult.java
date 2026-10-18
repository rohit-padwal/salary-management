package com.acme.salarymanagement.referencedata.model;

import java.util.List;

public record ReferenceDataResult(
        List<DepartmentOption> departments,
        List<CountryOption> countries,
        List<CurrencyOption> currencies) {

    public record DepartmentOption(Long id, String name) {
    }

    public record CountryOption(String code, String name) {
    }

    public record CurrencyOption(String code, String name, short minorUnits) {
    }
}
