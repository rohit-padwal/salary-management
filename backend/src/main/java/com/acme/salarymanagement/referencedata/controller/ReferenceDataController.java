package com.acme.salarymanagement.referencedata.controller;

import com.acme.salarymanagement.referencedata.dto.ReferenceDataResponse;
import com.acme.salarymanagement.referencedata.service.ReferenceDataQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference-data")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ReferenceDataController {

    private final ReferenceDataQueryService queries;

    ReferenceDataController(ReferenceDataQueryService queries) {
        this.queries = queries;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ReferenceDataResponse getSupportedCatalogs() {
        return ReferenceDataResponse.from(queries.getSupportedCatalogs());
    }
}
