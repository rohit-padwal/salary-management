package com.acme.salarymanagement.analytics.service;

import com.acme.salarymanagement.analytics.model.CompensationAnalyticsResult;
import com.acme.salarymanagement.analytics.model.CompensationQuery;
import com.acme.salarymanagement.analytics.repository.CompensationAggregateRepository;
import com.acme.salarymanagement.employee.validation.EmployeeCriteriaValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompensationAnalyticsService {

    private final CompensationAggregateRepository aggregates;
    private final EmployeeCriteriaValidator criteriaValidator;

    public CompensationAnalyticsService(CompensationAggregateRepository aggregates, EmployeeCriteriaValidator criteriaValidator) {
        this.aggregates = aggregates;
        this.criteriaValidator = criteriaValidator;
    }

    @Transactional(readOnly = true)
    public CompensationAnalyticsResult analyze(CompensationQuery query) {
        criteriaValidator.validate(query.criteria());
        // Both result levels use this one grouped read, even if another transaction commits afterward.
        return CompensationRollup.summarize(query.groupBy(), aggregates.aggregate(query.groupBy(), query.criteria()));
    }
}
