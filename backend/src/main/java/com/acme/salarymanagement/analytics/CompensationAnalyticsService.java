package com.acme.salarymanagement.analytics;

import com.acme.salarymanagement.employee.EmployeeCriteriaValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompensationAnalyticsService {

    private final CompensationAggregateRepository aggregates;
    private final EmployeeCriteriaValidator criteriaValidator;

    CompensationAnalyticsService(CompensationAggregateRepository aggregates, EmployeeCriteriaValidator criteriaValidator) {
        this.aggregates = aggregates;
        this.criteriaValidator = criteriaValidator;
    }

    @Transactional(readOnly = true)
    public CompensationAnalyticsResult analyze(CompensationQuery query) {
        criteriaValidator.validate(query.criteria());
        return CompensationRollup.summarize(query.groupBy(), aggregates.aggregate(query.groupBy(), query.criteria()));
    }
}
