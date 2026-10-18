package com.acme.salarymanagement.referencedata.repository;

import java.util.List;
import java.util.Optional;

import com.acme.salarymanagement.referencedata.entity.Country;
import org.springframework.data.repository.Repository;

public interface CountryRepository extends Repository<Country, String> {
    Optional<Country> findById(String code);

    List<Country> findAllByOrderByCodeAsc();
}
