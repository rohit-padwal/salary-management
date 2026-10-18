package com.acme.salarymanagement.employee;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Override
    @EntityGraph(attributePaths = {"department", "country", "currency"})
    Optional<Employee> findById(Long id);

    @Lock(LockModeType.OPTIMISTIC)
    Optional<Employee> findForEditById(Long id);
}
