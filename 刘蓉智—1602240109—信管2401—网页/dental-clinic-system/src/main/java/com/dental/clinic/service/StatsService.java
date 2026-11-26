package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店运营统计服务：
 * 关键指标 —— 预约取消率（目标 ≤ 8%）、爽约率、营收、牙椅利用率
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AppointmentMapper appointmentMapper;
    private final BillMapper billMapper;
    private final DentalChairMapper chairMapper;
    private final ScheduleMapper scheduleMapper;
    private final StoreMapper storeMapper;
    private final DoctorMapper doctorMapper;

    /** 门店运营总览 */
    public Map<String, Object> overview(Long storeId) {
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("stores", storeMapper.selectCount(null));
        out.put("doctors", doctorMapper.selectCount(
                storeId == null ? null : new LambdaQueryWrapper<Doctor>().eq(Doctor::getHomeStoreId, storeId)));

        // 预约状态分布
        LambdaQueryWrapper<Appointment> apptQw = storeId == null ? null
                : new LambdaQueryWrapper<Appointment>().eq(Appointment::getStoreId, storeId);
        List<Appointment> appts = appointmentMapper.selectList(apptQw);
        Map<String, Long> statusDist = new LinkedHashMap<>();
        String[] names = {"已提交", "已确认", "已到诊", "诊疗中", "已完成", "已取消", "爽约"};
        for (int i = 0; i <= 6; i++) {
            final int st = i;
            statusDist.put(names[i], appts.stream().filter(a -> a.getStatus() == st).count());
        }
        out.put("appointmentStatus", statusDist);

        // 取消率 = (取消 + 爽约) / 终态单量；目标 ≤ 8%
        long cancelled = statusDist.get("已取消") + statusDist.get("爽约");
        long finished = statusDist.get("已完成") + statusDist.get("已取消") + statusDist.get("爽约");
        BigDecimal cancelRate = finished == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelled * 100L).divide(BigDecimal.valueOf(finished), 1, RoundingMode.HALF_UP);
        out.put("cancelRate", cancelRate);
        out.put("cancelRateTarget", "≤8%");
        out.put("cancelRateAchieved", cancelRate.doubleValue() <= 8.0);

        // 累计营收（已支付 + 已开票）
        List<Bill> bills = billMapper.selectList(storeId == null ? null
                : new LambdaQueryWrapper<Bill>().eq(Bill::getStoreId, storeId));
        BigDecimal revenue = bills.stream()
                .filter(b -> b.getStatus() != null && (b.getStatus() == 1 || b.getStatus() == 2))
                .map(b -> b.getPayable() == null ? BigDecimal.ZERO : b.getPayable())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        out.put("paidRevenue", revenue);
        out.put("refundAmount", bills.stream()
                .filter(b -> b.getStatus() != null && (b.getStatus() == 3 || b.getStatus() == 4))
                .map(b -> b.getPayable() == null ? BigDecimal.ZERO : b.getPayable())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 医保收入（统筹报销）与个人自付收入（已支付 + 已开票口径）
        List<Bill> paidBills = bills.stream()
                .filter(b -> b.getStatus() != null && (b.getStatus() == 1 || b.getStatus() == 2))
                .toList();
        out.put("insuranceRevenue", paidBills.stream()
                .map(b -> b.getInsuranceCover() == null ? BigDecimal.ZERO : b.getInsuranceCover())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("selfRevenue", paidBills.stream()
                .map(b -> b.getSelfPay() == null ? BigDecimal.ZERO : b.getSelfPay())
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 牙椅资源
        LambdaQueryWrapper<DentalChair> chairQw = new LambdaQueryWrapper<DentalChair>().eq(DentalChair::getStatus, 1);
        if (storeId != null) {
            chairQw.eq(DentalChair::getStoreId, storeId);
        }
        out.put("chairsAvailable", chairMapper.selectCount(chairQw));

        // 已发布号源
        LambdaQueryWrapper<Schedule> schQw = new LambdaQueryWrapper<Schedule>().eq(Schedule::getStatus, 1);
        if (storeId != null) {
            schQw.eq(Schedule::getStoreId, storeId);
        }
        List<Schedule> schedules = scheduleMapper.selectList(schQw);
        int quotaTotal = schedules.stream().mapToInt(s -> s.getQuotaTotal() == null ? 0 : s.getQuotaTotal()).sum();
        int quotaUsed = schedules.stream().mapToInt(s -> s.getQuotaUsed() == null ? 0 : s.getQuotaUsed()).sum();
        out.put("quotaTotal", quotaTotal);
        out.put("quotaUsed", quotaUsed);
        out.put("quotaUsageRate", quotaTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(quotaUsed * 100L).divide(BigDecimal.valueOf(quotaTotal), 1, RoundingMode.HALF_UP));

        return out;
    }

    /** 今日到诊概览 */
    public Map<String, Object> today(Long storeId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getVisitDate, today);
        if (storeId != null) {
            qw.eq(Appointment::getStoreId, storeId);
        }
        List<Appointment> list = appointmentMapper.selectList(qw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("total", list.size());
        out.put("checkedIn", list.stream().filter(a -> a.getStatus() >= 2 && a.getStatus() <= 4).count());
        out.put("pendingConfirm", list.stream().filter(a -> a.getStatus() == 0).count());
        out.put("completed", list.stream().filter(a -> a.getStatus() == 4).count());
        return out;
    }
}
