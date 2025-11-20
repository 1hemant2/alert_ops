package com.AlertOps.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.AlertOps.dto.Schedular.SheduleReq;
import com.AlertOps.service.SchedularService;

import java.util.List;


@RestController
@RequestMapping("/api/v1/scheduler")
public class ScheduleController {
    private final SchedularService schedularService;

    public ScheduleController(SchedularService schedularService) {
        this.schedularService = schedularService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> scheduleBulk(@RequestBody List<SheduleReq> list) {
        schedularService.startScheduleBulk(list);
        return ResponseEntity.ok().build();
    }
}
