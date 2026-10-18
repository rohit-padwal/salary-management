package com.acme.salarymanagement.employee.seed;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.acme.salarymanagement.compensation.SalaryRules;
import com.acme.salarymanagement.employee.model.EmployeeDetails;
import com.acme.salarymanagement.employee.validation.EmployeeInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeSeedGeneratorTest {

    private static final Map<String, Long> DEPARTMENTS = Map.of("Engineering", 1L, "Finance", 2L, "HR", 3L,
            "Sales", 4L, "Marketing", 5L, "Operations", 6L, "Customer Support", 7L, "Legal", 8L);
    private static final Map<String, Integer> PRECISION = Map.of("INR", 2, "USD", 2, "GBP", 2, "EUR", 2, "JPY", 0, "KWD", 3);

    @Test
    void producesExactlyTenThousandReproducibleValidEmployeesAcrossAllCatalogs() {
        List<EmployeeDetails> rows = EmployeeSeedGenerator.generate(DEPARTMENTS, PRECISION);
        assertEquals(10_000, rows.size());
        assertEquals(rows, EmployeeSeedGenerator.generate(DEPARTMENTS, PRECISION));
        Set<String> numbers = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Long> departments = new HashSet<>();
        Set<String> countries = new HashSet<>();
        Set<String> currencies = new HashSet<>();
        for (EmployeeDetails row : rows) {
            EmployeeInput input = EmployeeInput.validate(row);
            assertTrue(numbers.add(input.employeeNumber()), "Employee numbers must be unique");
            names.add(input.fullName());
            departments.add(input.departmentId());
            countries.add(input.countryCode());
            currencies.add(input.currencyCode());
            BigDecimal salary = SalaryRules.validate(input.salaryAmount(), PRECISION.get(input.currencyCode()));
            assertTrue(salary.signum() > 0);
            assertEquals(PRECISION.get(input.currencyCode()).intValue(), salary.scale());
            assertEquals(salary.toPlainString(), input.salaryAmount());
        }
        assertEquals(Set.copyOf(DEPARTMENTS.values()), departments);
        assertEquals(Set.of("IN", "US", "GB", "DE", "JP", "KW"), countries);
        assertEquals(PRECISION.keySet(), currencies);
        assertTrue(names.size() > 1_000, "Use varied synthetic names rather than numbered placeholders");
        assertTrue(names.stream().anyMatch(name -> name.contains("李") || name.contains("佐藤") || name.contains("أحمد")));
    }

    @Test
    void catalogIterationOrderDoesNotChangeDataAndCurrencyPrecisionComesFromMetadata() {
        Map<String, Long> reversed = new LinkedHashMap<>();
        DEPARTMENTS.keySet().stream().sorted(java.util.Comparator.reverseOrder()).forEach(key -> reversed.put(key, DEPARTMENTS.get(key)));
        assertEquals(EmployeeSeedGenerator.generate(DEPARTMENTS, PRECISION), EmployeeSeedGenerator.generate(reversed, PRECISION));
        Map<String, Integer> customPrecision = new LinkedHashMap<>(PRECISION);
        customPrecision.put("USD", 4);
        for (EmployeeDetails row : EmployeeSeedGenerator.generate(DEPARTMENTS, customPrecision)) {
            if (row.currencyCode().equals("USD")) {
                assertEquals(4, SalaryRules.validate(row.salaryAmount(), 4).scale());
            }
        }
    }

    @Test
    void rejectsIncompleteOrUnsupportedCatalogMetadata() {
        Map<String, Long> missingDepartment = new LinkedHashMap<>(DEPARTMENTS);
        missingDepartment.remove("Legal");
        assertThrows(IllegalStateException.class, () -> EmployeeSeedGenerator.generate(missingDepartment, PRECISION));
        Map<String, Integer> missingCurrency = new LinkedHashMap<>(PRECISION);
        missingCurrency.remove("KWD");
        assertThrows(IllegalStateException.class, () -> EmployeeSeedGenerator.generate(DEPARTMENTS, missingCurrency));
        missingCurrency.put("KWD", 5);
        assertThrows(IllegalStateException.class, () -> EmployeeSeedGenerator.generate(DEPARTMENTS, missingCurrency));
    }
}
