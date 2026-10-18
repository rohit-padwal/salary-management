package com.acme.salarymanagement.employee.api;

import java.net.URI;

import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.EmployeeCreationService;
import com.acme.salarymanagement.employee.EmployeeQueryService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class EmployeeController {

    private final EmployeeCreationService creation;
    private final EmployeeQueryService queries;

    EmployeeController(EmployeeCreationService creation, EmployeeQueryService queries) {
        this.creation = creation;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse employee = EmployeeResponse.from(creation.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.id())).body(employee);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    EmployeeResponse getById(@PathVariable String id) {
        return EmployeeResponse.from(queries.getById(PositiveId.parse(id, "id", "Employee ID")));
    }
}
