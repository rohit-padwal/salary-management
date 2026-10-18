package com.acme.salarymanagement.employee;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Override
    @EntityGraph(attributePaths = {"department", "country", "currency"})
    Optional<Employee> findById(Long id);
}
