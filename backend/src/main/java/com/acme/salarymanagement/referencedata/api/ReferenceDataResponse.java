package com.acme.salarymanagement.referencedata.api;

import java.util.List;

import com.acme.salarymanagement.referencedata.ReferenceDataResult;

record ReferenceDataResponse(
        List<DepartmentOption> departments,
        List<CountryOption> countries,
        List<CurrencyOption> currencies) {

    static ReferenceDataResponse from(ReferenceDataResult data) {
        return new ReferenceDataResponse(
                data.departments().stream()
                        .map(department -> new DepartmentOption(department.id().toString(), department.name()))
                        .toList(),
                data.countries().stream()
                        .map(country -> new CountryOption(country.code(), country.name()))
                        .toList(),
                data.currencies().stream()
                        .map(currency -> new CurrencyOption(currency.code(), currency.name(), currency.minorUnits()))
                        .toList());
    }

    record DepartmentOption(String id, String name) {
    }

    record CountryOption(String code, String name) {
    }

    record CurrencyOption(String code, String name, int minorUnits) {
    }
}
