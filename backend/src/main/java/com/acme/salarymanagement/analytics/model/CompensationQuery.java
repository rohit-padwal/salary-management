package com.acme.salarymanagement.analytics.model;
import com.acme.salarymanagement.employee.model.EmployeeReadCriteria;

public record CompensationQuery(CompensationGrouping groupBy, EmployeeReadCriteria criteria) {

    public CompensationQuery {
        groupBy = groupBy == null ? CompensationGrouping.DEPARTMENT : groupBy;
        criteria = criteria == null ? EmployeeReadCriteria.EMPTY : criteria;
    }
}
