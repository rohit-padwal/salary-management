package com.acme.salarymanagement.referencedata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface CurrencyRepository extends Repository<Currency, String> {
    Optional<Currency> findById(String code);

    List<Currency> findAllByOrderByCodeAsc();
}
