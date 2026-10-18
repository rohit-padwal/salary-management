package com.acme.salarymanagement.referencedata.dto;

import java.util.List;

import com.acme.salarymanagement.referencedata.model.ReferenceDataResult;

public record ReferenceDataResponse(
        List<DepartmentOption> departments,
        List<CountryOption> countries,
        List<CurrencyOption> currencies) {

    public static ReferenceDataResponse from(ReferenceDataResult data) {
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

    public record DepartmentOption(String id, String name) {
    }

    public record CountryOption(String code, String name) {
    }

    public record CurrencyOption(String code, String name, int minorUnits) {
    }
}
