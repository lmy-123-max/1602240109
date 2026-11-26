package com.dental.clinic.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.entity.Appointment;
import com.dental.clinic.entity.Doctor;
import com.dental.clinic.entity.DentalChair;
import com.dental.clinic.entity.Schedule;
import com.dental.clinic.entity.Store;
import com.dental.clinic.entity.WorkCalendar;
import com.dental.clinic.mapper.AppointmentMapper;
import com.dental.clinic.mapper.DoctorMapper;
import com.dental.clinic.mapper.DentalChairMapper;
import com.dental.clinic.mapper.ScheduleMapper;
import com.dental.clinic.mapper.StoreMapper;
import com.dental.clinic.mapper.WorkCalendarMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 演示数据自动排班器：
 * 系统启动时自动为未来 7 天生成"工作日历 + 已发布排班"，
 * 保证任意日期打开系统都有号可约（种子 SQL 中的排班是固定历史日期，
 * 若不做此处理，演示时会出现"今天无号源"的尴尬）。
 *
 * 并在当天没有任何预约时，自动生成一份"今日演示预约"（覆盖两家门店、
 * 多位医生与多位患者，状态混合：已提交/已确认/已到诊/诊疗中），
 * 保证牙医工作台、排队叫号与总部驾驶舱每天演示都有鲜活的业务数据。
 *
 * 规则与 ScheduleService 一致：
 *  - 法定节假日（工作日历 day_type=2）不排班；
 *  - 同一医生同一时段只排一个门店（跨店冲突校验）；
 *  - 每门店每时段排班数 ≤ 该门店在用牙椅数，牙椅循环分配；
 *  - 号源容量 3（≤ 可用牙椅数），状态直接为已发布（患者可见）。
 */
@Component
public class DemoScheduleInitializer implements ApplicationRunner {

    private static final int DAYS_AHEAD = 7;
    private static final int QUOTA_PER_SLOT = 3;

    /**
     * 是否自动生成“今日演示预约”。
     * 课程演示建议 true（工作台一打开就有排队患者）；
     * 门诊正式使用请设为 false，避免每天自动产生演示病人数据。
     * 配置方式：application.yml 中 app.demo-appointments，或启动参数 --app.demo-appointments=false
     */
    @org.springframework.beans.factory.annotation.Value("${app.demo-appointments:true}")
    private boolean demoAppointments;

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final StoreMapper storeMapper;
    private final DentalChairMapper chairMapper;
    private final WorkCalendarMapper calendarMapper;
    private final AppointmentMapper appointmentMapper;

    public DemoScheduleInitializer(ScheduleMapper scheduleMapper, DoctorMapper doctorMapper,
                                   StoreMapper storeMapper, DentalChairMapper chairMapper,
                                   WorkCalendarMapper calendarMapper, AppointmentMapper appointmentMapper) {
        this.scheduleMapper = scheduleMapper;
        this.doctorMapper = doctorMapper;
        this.storeMapper = storeMapper;
        this.chairMapper = chairMapper;
        this.calendarMapper = calendarMapper;
        this.appointmentMapper = appointmentMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now();
        int calendars = 0;
        int schedules = 0;

        List<Store> stores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().eq(Store::getStatus, 1));

        /* ---------- 1. 补齐未来 7 天工作日历（不覆盖已有记录，如节假日） ---------- */
        for (Store store : stores) {
            for (int i = 0; i <= DAYS_AHEAD; i++) {
                LocalDate date = today.plusDays(i);
                WorkCalendar exist = calendarMapper.selectOne(new LambdaQueryWrapper<WorkCalendar>()
                        .eq(WorkCalendar::getStoreId, store.getStoreId())
                        .eq(WorkCalendar::getCalDate, date));
                if (exist == null) {
                    WorkCalendar cal = new WorkCalendar();
                    cal.setStoreId(store.getStoreId());
                    cal.setCalDate(date);
                    cal.setDayType(1);
                    cal.setOpenTime(java.time.LocalTime.of(9, 0));
                    cal.setCloseTime(java.time.LocalTime.of(18, 0));
                    calendarMapper.insert(cal);
                    calendars++;
                }
            }
        }

        /* ---------- 2. 为全部医生生成未来 7 天已发布排班 ---------- */
        List<Doctor> doctors = doctorMapper.selectList(null);
        for (Doctor doc : doctors) {
            if (doc.getHomeStoreId() == null) {
                continue;
            }
            List<DentalChair> chairs = chairMapper.selectList(new LambdaQueryWrapper<DentalChair>()
                    .eq(DentalChair::getStoreId, doc.getHomeStoreId())
                    .eq(DentalChair::getStatus, 1)
                    .orderByAsc(DentalChair::getChairId));
            if (chairs.isEmpty()) {
                continue;
            }
            for (int i = 0; i <= DAYS_AHEAD; i++) {
                LocalDate date = today.plusDays(i);
                WorkCalendar cal = calendarMapper.selectOne(new LambdaQueryWrapper<WorkCalendar>()
                        .eq(WorkCalendar::getStoreId, doc.getHomeStoreId())
                        .eq(WorkCalendar::getCalDate, date));
                if (cal != null && cal.getDayType() != null && cal.getDayType() == 2) {
                    continue; // 法定节假日不排班
                }
                for (int slot = 1; slot <= 2; slot++) {
                    Long exists = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getDoctorId, doc.getDoctorId())
                            .eq(Schedule::getWorkDate, date)
                            .eq(Schedule::getTimeSlot, slot));
                    if (exists > 0) {
                        continue; // 医生该时段已排（跨店冲突保护）
                    }
                    // 同门店同时段排班数 ≤ 在用牙椅数，循环分配牙椅
                    long used = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getStoreId, doc.getHomeStoreId())
                            .eq(Schedule::getWorkDate, date)
                            .eq(Schedule::getTimeSlot, slot)
                            .ne(Schedule::getStatus, 2));
                    if (used >= chairs.size()) {
                        continue; // 牙椅已用满
                    }
                    Schedule s = new Schedule();
                    s.setDoctorId(doc.getDoctorId());
                    s.setStoreId(doc.getHomeStoreId());
                    s.setWorkDate(date);
                    s.setTimeSlot(slot);
                    s.setChairId(chairs.get((int) used).getChairId());
                    s.setQuotaTotal(QUOTA_PER_SLOT);
                    s.setQuotaUsed(0);
                    s.setStatus(1); // 已发布，患者可见
                    scheduleMapper.insert(s);
                    schedules++;
                }
            }
        }
        System.out.println("[DemoScheduleInitializer] 自动排班完成：新增工作日历 " + calendars
                + " 条，已发布排班 " + schedules + " 条（未来 " + DAYS_AHEAD + " 天）");

        seedTodayAppointments();
    }

    /* ---------- 3. 当天没有任何预约时，生成今日演示预约 ---------- */
    private void seedTodayAppointments() {
        if (!demoAppointments) {
            return; // 正式使用模式：不生成演示预约
        }
        LocalDate today = LocalDate.now();
        Long exist = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getVisitDate, today));
        if (exist != null && exist > 0) {
            return; // 当天已有业务数据，不重复注入
        }

        // 演示患者（与种子 SQL 的 patient_id 对应），状态：0已提交 1已确认 2已到诊 3诊疗中
        // 门店1：2 位候诊 + 1 位诊疗中 + 1 位已确认 + 1 位已提交；门店2：1 候诊 + 1 已确认 + 1 已提交
        // 覆盖多位医生，工作台可看到"其他医生诊疗中、自己可接手候诊患者"的协同场景
        Object[][] plan = {
                {1L, 15L, 2}, {1L, 16L, 3}, {1L, 17L, 2}, {1L, 18L, 1}, {1L, 19L, 0},
                {2L, 20L, 2}, {2L, 21L, 1}, {2L, 22L, 0}
        };
        String prefix = "A" + today.toString().replace("-", "");
        int seq = 0;

        for (Object[] row : plan) {
            long storeId = (Long) row[0];
            long patientId = (Long) row[1];
            int status = (Integer) row[2];
            // 找该门店今日一条已发布排班（依次取不同医生）
            List<Schedule> schs = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getStoreId, storeId)
                    .eq(Schedule::getWorkDate, today)
                    .eq(Schedule::getStatus, 1)
                    .orderByAsc(Schedule::getDoctorId)
                    .last("LIMIT 12"));
            if (schs.isEmpty()) {
                continue;
            }
            Schedule sch = schs.get((seq++) % schs.size());
            if (appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                    .eq(Appointment::getScheduleId, sch.getScheduleId())
                    .eq(Appointment::getVisitDate, today)) > 0) {
                continue; // 该排班已有患者，避免一号多占
            }
            Appointment a = new Appointment();
            a.setAppointmentNo(nextApptNo(prefix));
            a.setPatientId(patientId);
            a.setDoctorId(sch.getDoctorId());
            a.setScheduleId(sch.getScheduleId());
            a.setStoreId(storeId);
            a.setVisitDate(today);
            a.setTimeSlot(sch.getTimeSlot() != null && sch.getTimeSlot() == 2 ? "下午" : "上午");
            a.setDepositAmount(new BigDecimal("20.00"));
            a.setDepositStatus(1);
            a.setCancelFee(BigDecimal.ZERO);
            a.setStatus(status);
            a.setCreateTime(LocalDateTime.now());
            appointmentMapper.insert(a);
            seq++;
        }
        System.out.println("[DemoScheduleInitializer] 今日演示预约注入完成（如当天无预约）");
    }

    /** 生成唯一预约号：A + yyyyMMdd + 3 位流水 */
    private String nextApptNo(String prefix) {
        int i = 1;
        while (appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getAppointmentNo, prefix + String.format("%03d", i))) > 0) {
            i++;
        }
        return prefix + String.format("%03d", i);
    }
}
