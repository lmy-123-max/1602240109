package com.dental.clinic.controller;

import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Schedule;
import com.dental.clinic.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 排班管理：管理端创建/发布/停用，患者端查询可约号源 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 患者端：查询已发布号源 */
    @GetMapping("/published")
    public Result<List<Schedule>> published(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(scheduleService.listPublished(storeId, date));
    }

    /** 管理端：全部排班 */
    @GetMapping("/all")
    public Result<List<Schedule>> all(@RequestParam(required = false) Long storeId) {
        return Result.ok(scheduleService.listAll(storeId));
    }

    /** 管理端：新建排班（草稿，含冲突校验） */
    @PostMapping("/create")
    public Result<Schedule> create(@RequestBody Schedule schedule) {
        return Result.ok(scheduleService.create(schedule));
    }

    /** 管理端：发布排班 */
    @PostMapping("/{id}/publish")
    public Result<Schedule> publish(@PathVariable Long id) {
        return Result.ok(scheduleService.publish(id));
    }

    /** 管理端：停用排班 */
    @PostMapping("/{id}/stop")
    public Result<Schedule> stop(@PathVariable Long id) {
        return Result.ok(scheduleService.stop(id));
    }
}
