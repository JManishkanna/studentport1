package com.academy.portal.web;

import com.academy.portal.dto.AlertsDto;
import com.academy.portal.dto.UpcomingDto;
import com.academy.portal.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Warnings and the next classes and deadlines, for every signed-in role. */
@RestController
@RequestMapping("/api")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/alerts")
    public AlertsDto alerts() {
        return insightService.alerts();
    }

    @GetMapping("/upcoming")
    public UpcomingDto upcoming(@RequestParam(required = false, defaultValue = "14") int days) {
        return insightService.upcoming(days);
    }
}
