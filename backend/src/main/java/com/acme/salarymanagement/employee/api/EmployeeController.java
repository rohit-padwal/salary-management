package com.acme.salarymanagement.employee.api;

import java.net.URI;

import com.acme.salarymanagement.employee.EmployeeCreationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class EmployeeController {

    private final EmployeeCreationService service;

    EmployeeController(EmployeeCreationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse employee = EmployeeResponse.from(service.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.id())).body(employee);
    }
}
