package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Bill;
import com.dental.clinic.mapper.BillMapper;
import com.dental.clinic.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 门店运营统计：取消率（目标 ≤8%）、爽约率、营收、号源利用率、营收趋势 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final BillMapper billMapper;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Long storeId) {
        return Result.ok(statsService.overview(storeId));
    }

    @GetMapping("/today")
    public Result<Map<String, Object>> today(@RequestParam(required = false) Long storeId) {
        return Result.ok(statsService.today(storeId));
    }

    /** 近 N 天营收趋势（报表，参照 OpenDental Reports） */
    @GetMapping("/revenue-trend")
    public Result<List<Map<String, Object>>> revenueTrend(
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "7") Integer days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<Bill>()
                .in(Bill::getStatus, 1, 2)
                .between(Bill::getPayTime, LocalDateTime.of(start, LocalTime.MIN),
                        LocalDateTime.of(end, LocalTime.MAX));
        if (storeId != null) {
            qw.eq(Bill::getStoreId, storeId);
        }
        List<Bill> bills = billMapper.selectList(qw);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            LocalDate day = d;
            BigDecimal sum = bills.stream()
                    .filter(b -> b.getPayTime() != null && b.getPayTime().toLocalDate().equals(day))
                    .map(b -> b.getPayable() == null ? BigDecimal.ZERO : b.getPayable())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("revenue", sum);
            out.add(m);
        }
        return Result.ok(out);
    }
}
