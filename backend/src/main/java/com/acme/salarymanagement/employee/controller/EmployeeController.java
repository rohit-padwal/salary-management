package com.acme.salarymanagement.employee.controller;

import java.net.URI;

import com.acme.salarymanagement.api.validation.PositiveId;
import com.acme.salarymanagement.employee.dto.CreateEmployeeRequest;
import com.acme.salarymanagement.employee.dto.EmployeePageResponse;
import com.acme.salarymanagement.employee.dto.EmployeeResponse;
import com.acme.salarymanagement.employee.dto.UpdateEmployeeRequest;
import com.acme.salarymanagement.employee.service.EmployeeCreationService;
import com.acme.salarymanagement.employee.service.EmployeeQueryService;
import com.acme.salarymanagement.employee.service.EmployeeUpdateService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class EmployeeController {

    private final EmployeeCreationService creation;
    private final EmployeeQueryService queries;
    private final EmployeeUpdateService updates;

    EmployeeController(EmployeeCreationService creation, EmployeeQueryService queries, EmployeeUpdateService updates) {
        this.creation = creation;
        this.queries = queries;
        this.updates = updates;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse employee = EmployeeResponse.from(creation.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.id())).body(employee);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    EmployeePageResponse list(@RequestParam MultiValueMap<String, String> parameters) {
        return EmployeePageResponse.from(queries.list(EmployeePageParameters.parse(parameters)));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    EmployeeResponse getById(@PathVariable String id) {
        return EmployeeResponse.from(queries.getById(PositiveId.parse(id, "id", "Employee ID")));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    EmployeeResponse update(@PathVariable String id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return EmployeeResponse.from(updates.update(PositiveId.parse(id, "id", "Employee ID"), request.toCommand()));
    }
}
