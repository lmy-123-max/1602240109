package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Appointment;
import com.dental.clinic.entity.Doctor;
import com.dental.clinic.entity.Schedule;
import com.dental.clinic.entity.TreatmentRecord;
import com.dental.clinic.entity.User;
import com.dental.clinic.mapper.AllergyRecordMapper;
import com.dental.clinic.mapper.AppointmentMapper;
import com.dental.clinic.mapper.DoctorMapper;
import com.dental.clinic.mapper.ScheduleMapper;
import com.dental.clinic.mapper.TreatmentRecordMapper;
import com.dental.clinic.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排队叫号（参照湘雅"排队叫号"）与复诊提醒（参照 OpenDental Recall）
 *
 * 队列为门店内"共享池"：
 *  - 任意已排班医生都可以叫号 / 接诊门店内任意已到诊患者；
 *  - 接诊时可携带 doctorId 改派预约单（先完成治疗的医生可接手其他医生名下排队的患者）；
 *  - /doctors-status 返回今日出诊医生团队实时状态，便于工作台协同调度。
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final AppointmentMapper appointmentMapper;
    private final TreatmentRecordMapper recordMapper;
    private final UserMapper userMapper;
    private final AllergyRecordMapper allergyMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;

    public QueueController(AppointmentMapper appointmentMapper, TreatmentRecordMapper recordMapper,
                           UserMapper userMapper, AllergyRecordMapper allergyMapper,
                           ScheduleMapper scheduleMapper, DoctorMapper doctorMapper) {
        this.appointmentMapper = appointmentMapper;
        this.recordMapper = recordMapper;
        this.userMapper = userMapper;
        this.allergyMapper = allergyMapper;
        this.scheduleMapper = scheduleMapper;
        this.doctorMapper = doctorMapper;
    }

    /** 今日叫号队列：已到诊(2)/诊疗中(3) 按时段排序（门店共享池，含接诊医生姓名） */
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getVisitDate, LocalDate.now())
                .in(Appointment::getStatus, 2, 3)
                .orderByAsc(Appointment::getTimeSlot)
                .orderByAsc(Appointment::getAppointmentId);
        if (storeId != null) {
            qw.eq(Appointment::getStoreId, storeId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int seq = 0;
        for (Appointment a : appointmentMapper.selectList(qw)) {
            seq++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", seq);
            m.put("appointmentId", a.getAppointmentId());
            m.put("appointmentNo", a.getAppointmentNo());
            m.put("patientName", patientName(a.getPatientId()));
            m.put("timeSlot", a.getTimeSlot());
            m.put("status", a.getStatus());
            m.put("doctorId", a.getDoctorId());
            m.put("doctorName", a.getDoctorId() == null ? null : userName(a.getDoctorId()));
            // 过敏提醒：候诊/叫号时同步展示患者过敏史，避免用药事故
            List<com.dental.clinic.entity.AllergyRecord> als = allergyMapper.selectList(
                    new LambdaQueryWrapper<com.dental.clinic.entity.AllergyRecord>()
                            .eq(com.dental.clinic.entity.AllergyRecord::getPatientId, a.getPatientId())
                            .eq(com.dental.clinic.entity.AllergyRecord::getStatus, 1));
            m.put("allergens", als.stream().map(com.dental.clinic.entity.AllergyRecord::getAllergenName).toList());
            out.add(m);
        }
        return Result.ok(out);
    }

    /**
     * 叫号开始诊疗：已到诊(2) → 诊疗中(3)。
     * 可选 doctorId：接诊医生（默认沿用预约单原医生；不同则改派，
     * 支持"先完成治疗的医生接手其他医生名下排队的患者"）。
     */
    @PostMapping("/{id}/call")
    public Result<Appointment> call(@PathVariable Long id,
                                    @RequestParam(required = false) Long doctorId) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException("预约单不存在");
        }
        if (a.getStatus() != 2) {
            throw new BusinessException("仅已到诊患者可叫号（当前状态码 " + a.getStatus() + "）");
        }
        if (doctorId != null && !doctorId.equals(a.getDoctorId())) {
            a.setDoctorId(doctorId); // 改派：空闲医生接手排队患者
        }
        a.setStatus(3);
        appointmentMapper.updateById(a);
        return Result.ok(a);
    }

    /** 今日出诊医生团队状态：谁在诊疗中、谁空闲、各自名下候诊人数（工作台协同调度用） */
    @GetMapping("/doctors-status")
    public Result<List<Map<String, Object>>> doctorsStatus(@RequestParam(required = false) Long storeId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Schedule> schQw = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getWorkDate, today)
                .eq(Schedule::getStatus, 1)
                .orderByAsc(Schedule::getStoreId)
                .orderByAsc(Schedule::getDoctorId);
        if (storeId != null) {
            schQw.eq(Schedule::getStoreId, storeId);
        }
        List<Schedule> schedules = scheduleMapper.selectList(schQw);

        // 今日全部活跃预约，按医生归类
        List<Appointment> todayAppts = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getVisitDate, today)
                .in(Appointment::getStatus, 2, 3));

        List<Map<String, Object>> out = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (Schedule s : schedules) {
            Long docId = s.getDoctorId();
            if (docId == null || seen.contains(docId)) {
                continue;
            }
            seen.add(docId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doctorId", docId);
            m.put("doctorName", userName(docId));
            Doctor d = doctorMapper.selectById(docId);
            m.put("title", d == null ? null : d.getTitle());
            m.put("specialty", d == null ? null : d.getSpecialty());
            m.put("storeId", s.getStoreId());

            List<Appointment> mine = todayAppts.stream()
                    .filter(a -> docId.equals(a.getDoctorId())).toList();
            Appointment serving = mine.stream()
                    .filter(a -> a.getStatus() == 3).findFirst().orElse(null);
            long waiting = mine.stream().filter(a -> a.getStatus() == 2).count();

            m.put("servingPatient", serving == null ? null : patientName(serving.getPatientId()));
            m.put("waitingCount", waiting);
            m.put("busy", serving != null); // true=诊疗中 false=空闲可接诊
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 复诊提醒：未来 days 天内到期（next_visit）的病历列表 */
    @GetMapping("/recalls")
    public Result<List<Map<String, Object>>> recalls(@RequestParam(defaultValue = "30") Integer days) {
        LocalDate today = LocalDate.now();
        List<TreatmentRecord> recs = recordMapper.selectList(new LambdaQueryWrapper<TreatmentRecord>()
                .isNotNull(TreatmentRecord::getNextVisit)
                .ge(TreatmentRecord::getNextVisit, today)
                .le(TreatmentRecord::getNextVisit, today.plusDays(days))
                .orderByAsc(TreatmentRecord::getNextVisit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TreatmentRecord r : recs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("patientId", r.getPatientId());
            m.put("patientName", patientName(r.getPatientId()));
            m.put("diagnosis", r.getDiagnosis());
            m.put("toothPosition", r.getToothPosition());
            m.put("nextVisit", r.getNextVisit());
            long leftDays = java.time.temporal.ChronoUnit.DAYS.between(today, r.getNextVisit());
            m.put("daysLeft", leftDays);
            m.put("urgent", leftDays <= 3);
            out.add(m);
        }
        return Result.ok(out);
    }

    private String patientName(Long patientId) {
        User u = patientId == null ? null : userMapper.selectById(patientId);
        return u == null ? ("患者" + patientId) : u.getRealName();
    }

    private String userName(Long userId) {
        User u = userId == null ? null : userMapper.selectById(userId);
        return u == null ? ("用户" + userId) : u.getRealName();
    }
}
