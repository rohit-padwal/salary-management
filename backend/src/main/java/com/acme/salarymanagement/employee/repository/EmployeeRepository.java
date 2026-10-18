package com.acme.salarymanagement.employee.repository;

import java.util.List;
import java.util.Optional;

import com.acme.salarymanagement.employee.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Explicit casts keep a missing search pattern usable by PostgreSQL lower().
    String MATCH_PREDICATE = "(:pattern is null or (lower(e.employeeNumber) like lower(cast(:pattern as String)) escape '!' "
            + "or lower(e.fullName) like lower(cast(:pattern as String)) escape '!')) "
            + "and (:departmentId is null or e.department.id = :departmentId) "
            + "and (:countryCode is null or e.country.code = :countryCode) "
            + "and (:currencyCode is null or e.currency.code = :currencyCode)";

    @Override
    @EntityGraph(attributePaths = {"department", "country", "currency"})
    Optional<Employee> findById(Long id);

    @EntityGraph(attributePaths = {"department", "country", "currency"})
    List<Employee> findAllBy(Pageable pageable);

    @Query("select count(e) from Employee e where " + MATCH_PREDICATE)
    long countMatching(@Param("pattern") String pattern, @Param("departmentId") Long departmentId,
            @Param("countryCode") String countryCode, @Param("currencyCode") String currencyCode);

    @EntityGraph(attributePaths = {"department", "country", "currency"})
    @Query("select e from Employee e where " + MATCH_PREDICATE)
    List<Employee> findMatching(@Param("pattern") String pattern, @Param("departmentId") Long departmentId,
            @Param("countryCode") String countryCode, @Param("currencyCode") String currencyCode, Pageable pageable);

    // Lock only the versioned employee; fetching lookups here would also lock unversioned entities.
    @Lock(LockModeType.OPTIMISTIC)
    Optional<Employee> findForEditById(Long id);
}
