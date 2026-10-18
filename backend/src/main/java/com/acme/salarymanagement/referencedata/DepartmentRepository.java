package com.acme.salarymanagement.referencedata;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

public interface DepartmentRepository extends Repository<Department, Long> {
    Optional<Department> findById(Long id);

    List<Department> findAllByOrderByNameAscIdAsc();
}
