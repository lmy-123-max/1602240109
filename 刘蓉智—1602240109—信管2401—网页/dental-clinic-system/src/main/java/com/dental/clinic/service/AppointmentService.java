package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.Appointment;
import com.dental.clinic.entity.DentalChair;
import com.dental.clinic.entity.Schedule;
import com.dental.clinic.entity.WorkCalendar;
import com.dental.clinic.mapper.AppointmentMapper;
import com.dental.clinic.mapper.DentalChairMapper;
import com.dental.clinic.mapper.ScheduleMapper;
import com.dental.clinic.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 预约核心服务：号源互斥校验、预付挂号费、取消违约金、到诊核销、爽约自动关闭
 * 状态机：0已提交 → 1已确认 → 2已到诊 → 3诊疗中 → 4已完成；5已取消 / 6爽约
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    public static final BigDecimal DEPOSIT = new BigDecimal("20.00"); // 预付挂号费 20 元

    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final DentalChairMapper chairMapper;
    private final WorkCalendarMapper calendarMapper;

    /** 患者自助预约：号源校验 → 营业日校验 → 锁定号源 → 生成预约单（待支付预付金） */
    @Transactional
    public Appointment create(Long patientId, Long scheduleId) {
        Schedule sch = scheduleMapper.selectById(scheduleId);
        if (sch == null || sch.getStatus() != 1) {
            throw new BusinessException("排班不存在或未发布");
        }
        if (sch.getWorkDate().isBefore(LocalDate.now())) {
            throw new BusinessException("该时段已过期，请选择其他时间");
        }
        if (sch.getQuotaUsed() >= sch.getQuotaTotal()) {
            throw new BusinessException("该时段号源已约满，请选择其他时段");
        }
        // 同一患者同时段重复预约校验
        Long dup = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getPatientId, patientId)
                .eq(Appointment::getScheduleId, scheduleId)
                .in(Appointment::getStatus, 0, 1, 2, 3));
        if (dup > 0) {
            throw new BusinessException("您已预约该时段，请勿重复预约");
        }
        // 工作日历校验：节假日不可预约
        Long cal = calendarMapper.selectCount(new LambdaQueryWrapper<WorkCalendar>()
                .eq(WorkCalendar::getStoreId, sch.getStoreId())
                .eq(WorkCalendar::getCalDate, sch.getWorkDate())
                .eq(WorkCalendar::getDayType, 2));
        if (cal > 0) {
            throw new BusinessException("该日为法定节假日，门店不开诊");
        }
        // 号源容量不超过可用牙椅数（在用状态）
        if (sch.getChairId() != null) {
            DentalChair chair = chairMapper.selectById(sch.getChairId());
            if (chair == null || chair.getStatus() != 1) {
                throw new BusinessException("绑定的牙椅不可用（维修中）");
            }
        }

        Appointment appt = new Appointment();
        appt.setAppointmentNo("A" + LocalDate.now().toString().replace("-", "")
                + String.format("%04d", appointmentMapper.selectCount(null) + 1));
        appt.setPatientId(patientId);
        appt.setDoctorId(sch.getDoctorId());
        appt.setScheduleId(scheduleId);
        appt.setStoreId(sch.getStoreId());
        appt.setVisitDate(sch.getWorkDate());
        appt.setTimeSlot(slotName(sch.getTimeSlot()));
        appt.setDepositAmount(DEPOSIT);
        appt.setDepositStatus(1); // 在线预付成功（演示环境模拟支付回调）
        appt.setStatus(0);
        appt.setCreateTime(LocalDateTime.now());
        appointmentMapper.insert(appt);

        sch.setQuotaUsed(sch.getQuotaUsed() + 1);
        scheduleMapper.updateById(sch);
        return appt;
    }

    /** 取消预约：24h 外免费退回；2h 内收取 50% 挂号费违约金；其余全额退回 */
    @Transactional
    public Appointment cancel(Long appointmentId, Long patientId, String reason) {
        Appointment appt = appointmentMapper.selectById(appointmentId);
        if (appt == null || !appt.getPatientId().equals(patientId)) {
            throw new BusinessException("预约单不存在");
        }
        if (appt.getStatus() >= 2) {
            throw new BusinessException("已到诊的预约不可取消");
        }
        if (appt.getStatus() == 5) {
            throw new BusinessException("该预约已取消");
        }
        Schedule sch = scheduleMapper.selectById(appt.getScheduleId());
        LocalDateTime visitStart = LocalDateTime.of(appt.getVisitDate(),
                java.time.LocalTime.of(sch.getTimeSlot() == 1 ? 9 : sch.getTimeSlot() == 2 ? 14 : 18, 0));
        long hours = LocalDateTime.now().until(visitStart, ChronoUnit.HOURS);

        BigDecimal fee = BigDecimal.ZERO;
        if (hours < 2) {
            fee = DEPOSIT.multiply(new BigDecimal("0.5")); // 2 小时内收取 50%
        }
        appt.setCancelFee(fee);
        appt.setDepositStatus(fee.compareTo(BigDecimal.ZERO) > 0 ? 1 : 2); // 有违约金则抵扣，否则退回
        appt.setStatus(5);
        appt.setCancelReason(reason == null ? "患者自主取消" : reason);
        appointmentMapper.updateById(appt);

        // 释放号源并二次投放
        sch.setQuotaUsed(Math.max(0, sch.getQuotaUsed() - 1));
        scheduleMapper.updateById(sch);
        return appt;
    }

    /** 到诊核销（前台/自助签到）：状态机 1已确认 → 2已到诊 */
    @Transactional
    public Appointment checkIn(Long appointmentId) {
        Appointment appt = appointmentMapper.selectById(appointmentId);
        if (appt == null) {
            throw new BusinessException("预约单不存在");
        }
        if (appt.getStatus() != 0 && appt.getStatus() != 1) {
            throw new BusinessException("当前状态不允许签到（状态码 " + appt.getStatus() + "）");
        }
        appt.setStatus(2);
        appointmentMapper.updateById(appt);
        return appt;
    }

    /** 超时未签到自动关闭（爽约），号源二次投放 */
    @Transactional
    public int sweepNoShow() {
        List<Appointment> pending = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getStatus, 1)
                .lt(Appointment::getVisitDate, LocalDate.now()));
        int n = 0;
        for (Appointment appt : pending) {
            appt.setStatus(6);
            appt.setDepositStatus(1); // 爽约不退预付金
            appointmentMapper.updateById(appt);
            Schedule sch = scheduleMapper.selectById(appt.getScheduleId());
            if (sch != null) {
                sch.setQuotaUsed(Math.max(0, sch.getQuotaUsed() - 1));
                scheduleMapper.updateById(sch);
            }
            n++;
        }
        return n;
    }

    public static String slotName(Integer slot) {
        return switch (slot) {
            case 1 -> "09:00-12:00";
            case 2 -> "14:00-17:00";
            default -> "18:00-20:30";
        };
    }
}
