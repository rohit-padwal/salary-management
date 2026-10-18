package com.acme.salarymanagement.referencedata;

import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface CountryRepository extends Repository<Country, String> {
    Optional<Country> findById(String code);
}
