package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Appointment;
import com.dental.clinic.mapper.AppointmentMapper;
import com.dental.clinic.service.AppointmentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 预约挂号：创建（预付挂号费）/ 取消（违约金）/ 签到 / 爽约扫描 */
@RestController
@RequestMapping("/api/appointment")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;
    private final com.dental.clinic.mapper.UserMapper userMapper;

    /** 联查姓名（患者/医生） */
    private Map<String, Object> enrich(Appointment a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("appointmentId", a.getAppointmentId());
        m.put("appointmentNo", a.getAppointmentNo());
        m.put("patientId", a.getPatientId());
        m.put("doctorId", a.getDoctorId());
        m.put("storeId", a.getStoreId());
        m.put("visitDate", a.getVisitDate());
        m.put("timeSlot", a.getTimeSlot());
        m.put("status", a.getStatus());
        m.put("cancelReason", a.getCancelReason());
        var pu = userMapper.selectById(a.getPatientId());
        m.put("patientName", pu == null ? ("患者#" + a.getPatientId()) : pu.getRealName());
        var du = a.getDoctorId() == null ? null : userMapper.selectById(a.getDoctorId());
        m.put("doctorName", du == null ? "-" : du.getRealName());
        return m;
    }

    /** 患者预约挂号 */
    @Data
    public static class BookReq {
        private Long patientId;
        private Long scheduleId;
    }

    @PostMapping("/book")
    public Result<Appointment> book(@RequestBody BookReq req) {
        return Result.ok(appointmentService.create(req.getPatientId(), req.getScheduleId()));
    }

    /** 患者取消预约（2 小时内收取 50% 违约金） */
    @Data
    public static class CancelReq {
        private Long patientId;
        private String reason;
    }

    @PostMapping("/{id}/cancel")
    public Result<Appointment> cancel(@PathVariable Long id, @RequestBody(required = false) CancelReq req) {
        Long pid = req == null ? null : req.getPatientId();
        String reason = req == null ? null : req.getReason();
        return Result.ok(appointmentService.cancel(id, pid, reason));
    }

    /** 到诊核销签到 */
    @PostMapping("/{id}/checkin")
    public Result<Appointment> checkIn(@PathVariable Long id) {
        return Result.ok(appointmentService.checkIn(id));
    }

    /** 爽约自动扫描（定时任务手动触发） */
    @PostMapping("/sweep-noshow")
    public Result<Map<String, Integer>> sweep() {
        int n = appointmentService.sweepNoShow();
        return Result.ok(Map.of("closed", n));
    }

    /** 我的预约列表 */
    @GetMapping("/my")
    public Result<List<Appointment>> my(@RequestParam Long patientId) {
        return Result.ok(appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getPatientId, patientId)
                .orderByDesc(Appointment::getCreateTime)));
    }

    /** 预约单详情 */
    @GetMapping("/{id}")
    public Result<Appointment> detail(@PathVariable Long id) {
        return Result.ok(appointmentMapper.selectById(id));
    }

    /** 今日全部预约（前台/牙医工作台队列，联出患者与医生姓名） */
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today() {
        List<Appointment> list = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getVisitDate, java.time.LocalDate.now())
                .orderByAsc(Appointment::getTimeSlot));
        return Result.ok(list.stream().map(this::enrich).toList());
    }

    /** 门店确认预约（已提交 → 已确认） */
    @PostMapping("/{id}/confirm")
    public Result<Appointment> confirm(@PathVariable Long id) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            return Result.fail("预约单不存在");
        }
        if (a.getStatus() != 0) {
            return Result.fail("仅「已提交」状态的预约可确认，当前状态码：" + a.getStatus());
        }
        a.setStatus(1);
        appointmentMapper.updateById(a);
        return Result.ok(a);
    }
}
