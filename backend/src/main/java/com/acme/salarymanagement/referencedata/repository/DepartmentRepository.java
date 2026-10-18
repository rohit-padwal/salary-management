package com.acme.salarymanagement.referencedata.repository;

import java.util.List;
import java.util.Optional;

import com.acme.salarymanagement.referencedata.entity.Department;
import org.springframework.data.repository.Repository;

public interface DepartmentRepository extends Repository<Department, Long> {
    Optional<Department> findById(Long id);

    List<Department> findAllByOrderByNameAscIdAsc();
}
