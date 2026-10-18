package com.acme.salarymanagement.referencedata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataQueryService {

    private final DepartmentRepository departments;
    private final CountryRepository countries;
    private final CurrencyRepository currencies;

    ReferenceDataQueryService(DepartmentRepository departments, CountryRepository countries,
            CurrencyRepository currencies) {
        this.departments = departments;
        this.countries = countries;
        this.currencies = currencies;
    }

    @Transactional(readOnly = true)
    public ReferenceDataResult getSupportedCatalogs() {
        return new ReferenceDataResult(
                departments.findAllByOrderByNameAscIdAsc().stream()
                        .map(department -> new ReferenceDataResult.DepartmentOption(department.getId(), department.getName()))
                        .toList(),
                countries.findAllByOrderByCodeAsc().stream()
                        .map(country -> new ReferenceDataResult.CountryOption(country.getCode(), country.getName()))
                        .toList(),
                currencies.findAllByOrderByCodeAsc().stream()
                        .map(currency -> new ReferenceDataResult.CurrencyOption(
                                currency.getCode(), currency.getName(), currency.getMinorUnits()))
                        .toList());
    }
}
