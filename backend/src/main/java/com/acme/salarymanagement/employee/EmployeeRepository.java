package com.acme.salarymanagement.employee;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    String SEARCH_PREDICATE = "lower(e.employeeNumber) like lower(:pattern) escape '!' "
            + "or lower(e.fullName) like lower(:pattern) escape '!'";

    @Override
    @EntityGraph(attributePaths = {"department", "country", "currency"})
    Optional<Employee> findById(Long id);

    @EntityGraph(attributePaths = {"department", "country", "currency"})
    List<Employee> findAllBy(Pageable pageable);

    @Query("select count(e) from Employee e where " + SEARCH_PREDICATE)
    long countMatching(@Param("pattern") String pattern);

    @EntityGraph(attributePaths = {"department", "country", "currency"})
    @Query("select e from Employee e where " + SEARCH_PREDICATE)
    List<Employee> findMatching(@Param("pattern") String pattern, Pageable pageable);

    @Lock(LockModeType.OPTIMISTIC)
    Optional<Employee> findForEditById(Long id);
}
