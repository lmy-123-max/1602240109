package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业绩核算服务：净利润提成制
 * 净利润 = 经办收入 − 直接耗材成本 − 分摊运营成本
 * 激励奖金 = 净利润 × 10%
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private static final BigDecimal INCENTIVE_RATE = new BigDecimal("0.10");

    private final PerformanceMapper performanceMapper;
    private final BillMapper billMapper;
    private final TreatmentRecordMapper recordMapper;
    private final TreatmentDetailMapper detailMapper;
    private final TreatmentItemMapper itemMapper;
    private final OperationCostMapper costMapper;

    /** 试算并保存（未确认可反复重算） */
    @Transactional
    public Performance trialCalc(Long doctorId, Long storeId, Integer year, Integer quarter) {
        LocalDate qStart = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        LocalDate qEnd = qStart.plusMonths(3).minusDays(1);

        // 1. 经办收入：该医生（或全店）本季度已支付账单合计
        List<TreatmentRecord> records = recordMapper.selectList(new LambdaQueryWrapper<TreatmentRecord>()
                .eq(TreatmentRecord::getDoctorId, doctorId));
        Map<Long, TreatmentRecord> byId = records.stream()
                .collect(Collectors.toMap(TreatmentRecord::getRecordId, r -> r));

        BigDecimal revenue = BigDecimal.ZERO;
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getStoreId, storeId)
                .in(Bill::getStatus, 1, 2)
                .between(Bill::getPayTime, LocalDateTime.of(qStart, LocalTime.MIN),
                        LocalDateTime.of(qEnd, LocalTime.MAX)));
        for (Bill b : bills) {
            if (doctorId == null || byId.containsKey(b.getRecordId())) {
                revenue = revenue.add(b.getPayable() == null ? BigDecimal.ZERO : b.getPayable());
            }
        }

        // 2. 直接耗材成本：Σ 诊疗明细数量 × 项目耗材成本
        BigDecimal directCost = BigDecimal.ZERO;
        for (TreatmentRecord rec : records) {
            List<TreatmentDetail> details = detailMapper.selectList(new LambdaQueryWrapper<TreatmentDetail>()
                    .eq(TreatmentDetail::getRecordId, rec.getRecordId()));
            for (TreatmentDetail d : details) {
                if (d.getItemId() == null || d.getQuantity() == null) {
                    continue;
                }
                TreatmentItem item = itemMapper.selectById(d.getItemId());
                if (item != null && item.getMaterialCost() != null) {
                    directCost = directCost.add(item.getMaterialCost()
                            .multiply(BigDecimal.valueOf(d.getQuantity())));
                }
            }
        }

        // 3. 分摊运营成本：门店季度运营成本单合计
        BigDecimal shareCost = BigDecimal.ZERO;
        List<OperationCost> costs = costMapper.selectList(new LambdaQueryWrapper<OperationCost>()
                .eq(OperationCost::getStoreId, storeId)
                .eq(OperationCost::getYear, year)
                .eq(OperationCost::getQuarter, quarter));
        for (OperationCost c : costs) {
            shareCost = shareCost.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
        }

        // 4. 净利润与激励奖金
        BigDecimal netProfit = revenue.subtract(directCost).subtract(shareCost);
        BigDecimal incentive = netProfit.multiply(INCENTIVE_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. 保存（同医生同店同季度覆盖草稿）
        Performance old = performanceMapper.selectOne(new LambdaQueryWrapper<Performance>()
                .eq(Performance::getDoctorId, doctorId)
                .eq(Performance::getStoreId, storeId)
                .eq(Performance::getYear, year)
                .eq(Performance::getQuarter, quarter));
        if (old != null && old.getStatus() == 1) {
            throw new BusinessException("该季度业绩已确认，不允许重算");
        }
        Performance p = old == null ? new Performance() : old;
        p.setDoctorId(doctorId);
        p.setStoreId(storeId);
        p.setYear(year);
        p.setQuarter(quarter);
        p.setRevenue(revenue.setScale(2, RoundingMode.HALF_UP));
        p.setDirectCost(directCost.setScale(2, RoundingMode.HALF_UP));
        p.setShareCost(shareCost.setScale(2, RoundingMode.HALF_UP));
        p.setNetProfit(netProfit.setScale(2, RoundingMode.HALF_UP));
        p.setIncentiveAmount(incentive);
        p.setStatus(0);
        if (old == null) {
            performanceMapper.insert(p);
        } else {
            performanceMapper.updateById(p);
        }
        return p;
    }

    /** 确认核算结果（确认后锁定） */
    @Transactional
    public Performance confirm(Long performanceId) {
        Performance p = performanceMapper.selectById(performanceId);
        if (p == null) {
            throw new BusinessException("业绩核算单不存在");
        }
        if (p.getStatus() == 1) {
            throw new BusinessException("该业绩核算单已确认，请勿重复操作");
        }
        p.setStatus(1);
        performanceMapper.updateById(p);
        return p;
    }
}
