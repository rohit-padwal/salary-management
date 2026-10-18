package com.acme.salarymanagement.employee;

import java.sql.SQLException;
import java.util.Optional;

import com.acme.salarymanagement.referencedata.Country;
import com.acme.salarymanagement.referencedata.CountryRepository;
import com.acme.salarymanagement.referencedata.Currency;
import com.acme.salarymanagement.referencedata.CurrencyRepository;
import com.acme.salarymanagement.referencedata.Department;
import com.acme.salarymanagement.referencedata.DepartmentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeCreationServiceTest {

    @Mock
    private EmployeeRepository employees;
    @Mock
    private DepartmentRepository departments;
    @Mock
    private CountryRepository countries;
    @Mock
    private CurrencyRepository currencies;

    private EmployeeCreationService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeCreationService(employees, departments, countries, currencies);
        when(departments.findById(1L)).thenReturn(Optional.of(mock(Department.class)));
        when(countries.findById("IN")).thenReturn(Optional.of(mock(Country.class)));
        Currency currency = mock(Currency.class);
        when(currency.getMinorUnits()).thenReturn((short) 2);
        when(currencies.findById("USD")).thenReturn(Optional.of(currency));
    }

    @Test
    void translatesOnlyEmployeeNumberUniqueConstraintIntoBusinessConflict() {
        DataIntegrityViolationException failure = constraintFailure("23505", "uq_employees_employee_number");
        when(employees.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(EmployeeNumberConflictException.class, () -> service.create(validCommand())).getCause());
    }

    @ParameterizedTest
    @CsvSource({"23503,fk_employees_department", "23505,other_unique_constraint", "23514,ck_employees_salary_positive_finite"})
    void doesNotMisreportOtherIntegrityFailuresAsDuplicateNumbers(String state, String constraint) {
        DataIntegrityViolationException failure = constraintFailure(state, constraint);
        when(employees.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(validCommand())));
    }

    private CreateEmployeeCommand validCommand() {
        return new CreateEmployeeCommand("EMP-1", "Name", 1L, "IN", "1000", "USD");
    }

    private DataIntegrityViolationException constraintFailure(String state, String constraint) {
        return new DataIntegrityViolationException("Persistence failure",
                new ConstraintViolationException("Constraint failure", new SQLException("Database detail", state), constraint));
    }
}
