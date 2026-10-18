package com.acme.salarymanagement.analytics.controller;

import com.acme.salarymanagement.analytics.dto.CompensationResponse;
import com.acme.salarymanagement.analytics.service.CompensationAnalyticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CompensationController {

    private final CompensationAnalyticsService analytics;

    CompensationController(CompensationAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping(value = "/api/v1/analytics/compensation", produces = MediaType.APPLICATION_JSON_VALUE)
    CompensationResponse analyze(@RequestParam MultiValueMap<String, String> parameters) {
        return CompensationResponse.from(analytics.analyze(CompensationParameters.parse(parameters)));
    }
}
