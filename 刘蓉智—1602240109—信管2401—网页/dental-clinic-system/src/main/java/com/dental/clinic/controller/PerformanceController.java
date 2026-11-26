package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Performance;
import com.dental.clinic.mapper.PerformanceMapper;
import com.dental.clinic.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 业绩核算：净利润提成制（净利润 = 收入 − 直接耗材成本 − 分摊运营成本；奖金 = 净利润 × 10%） */
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final PerformanceMapper performanceMapper;

    /** 试算并保存（未确认可反复重算） */
    @PostMapping("/trial-calc")
    public Result<Performance> trialCalc(@RequestParam(required = false) Long doctorId,
                                         @RequestParam Long storeId,
                                         @RequestParam Integer year,
                                         @RequestParam Integer quarter) {
        return Result.ok(performanceService.trialCalc(doctorId, storeId, year, quarter));
    }

    /** 确认核算结果 */
    @PostMapping("/{id}/confirm")
    public Result<Performance> confirm(@PathVariable Long id) {
        return Result.ok(performanceService.confirm(id));
    }

    @GetMapping("/by-doctor")
    public Result<List<Performance>> byDoctor(@RequestParam Long doctorId) {
        return Result.ok(performanceMapper.selectList(new LambdaQueryWrapper<Performance>()
                .eq(Performance::getDoctorId, doctorId)
                .orderByDesc(Performance::getYear)
                .orderByDesc(Performance::getQuarter)));
    }

    @GetMapping("/by-store")
    public Result<List<Performance>> byStore(@RequestParam Long storeId) {
        return Result.ok(performanceMapper.selectList(new LambdaQueryWrapper<Performance>()
                .eq(Performance::getStoreId, storeId)
                .orderByDesc(Performance::getYear)
                .orderByDesc(Performance::getQuarter)));
    }

    /** 全部医生业绩一览（总部核算确认用） */
    @GetMapping("/all")
    public Result<List<Performance>> all() {
        return Result.ok(performanceMapper.selectList(new LambdaQueryWrapper<Performance>()
                .orderByDesc(Performance::getYear)
                .orderByDesc(Performance::getQuarter)
                .orderByDesc(Performance::getPerformanceId)));
    }
}
