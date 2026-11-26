package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基础数据：门店 / 牙医 / 诊疗项目价目 / 供应商 / 牙椅 */
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final StoreMapper storeMapper;
    private final DoctorMapper doctorMapper;
    private final UserMapper userMapper;
    private final TreatmentItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final DentalChairMapper chairMapper;
    private final WorkCalendarMapper calendarMapper;
    private final DoctorQualificationMapper qualificationMapper;

    @GetMapping("/stores")
    public Result<List<Store>> stores() {
        return Result.ok(storeMapper.selectList(
                new LambdaQueryWrapper<Store>().eq(Store::getStatus, 1)));
    }

    /** 牙医列表（doctor_id 同步 sys_user.user_id，联出姓名） */
    @GetMapping("/doctors")
    public Result<List<Map<String, Object>>> doctors(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<>();
        if (storeId != null) {
            qw.eq(Doctor::getHomeStoreId, storeId);
        }
        List<Doctor> doctors = doctorMapper.selectList(qw);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Doctor d : doctors) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doctorId", d.getDoctorId());
            User u = userMapper.selectById(d.getDoctorId());
            m.put("doctorName", u == null ? d.getJobNo() : u.getRealName());
            m.put("jobNo", d.getJobNo());
            m.put("title", d.getTitle());
            m.put("specialty", d.getSpecialty());
            m.put("homeStoreId", d.getHomeStoreId());
            out.add(m);
        }
        return Result.ok(out);
    }

    @GetMapping("/items")
    public Result<List<TreatmentItem>> items() {
        return Result.ok(itemMapper.selectList(
                new LambdaQueryWrapper<TreatmentItem>().eq(TreatmentItem::getStatus, 1)));
    }

    @GetMapping("/suppliers")
    public Result<List<Supplier>> suppliers() {
        return Result.ok(supplierMapper.selectList(null));
    }

    @GetMapping("/chairs")
    public Result<List<DentalChair>> chairs(@RequestParam Long storeId) {
        return Result.ok(chairMapper.selectList(new LambdaQueryWrapper<DentalChair>()
                .eq(DentalChair::getStoreId, storeId)));
    }

    @GetMapping("/calendar")
    public Result<List<WorkCalendar>> calendar(@RequestParam Long storeId) {
        return Result.ok(calendarMapper.selectList(new LambdaQueryWrapper<WorkCalendar>()
                .eq(WorkCalendar::getStoreId, storeId)
                .orderByAsc(WorkCalendar::getCalDate)));
    }

    /** 牙医资质证书列表（资质审核信息公示） */
    @GetMapping("/qualifications")
    public Result<List<DoctorQualification>> qualifications(@RequestParam(required = false) Long doctorId) {
        LambdaQueryWrapper<DoctorQualification> qw = new LambdaQueryWrapper<DoctorQualification>()
                .orderByAsc(DoctorQualification::getAuditStatus);
        if (doctorId != null) {
            qw.eq(DoctorQualification::getDoctorId, doctorId);
        }
        return Result.ok(qualificationMapper.selectList(qw));
    }

    /** 牙医提交新的执业资质证书（进入待初审队列，由门店初审/总部终审） */
    @PostMapping("/qualifications/submit")
    public Result<DoctorQualification> submitQualification(@RequestBody DoctorQualification q) {
        if (q.getDoctorId() == null || q.getQualName() == null || q.getCertNo() == null) {
            return Result.fail("医生、证书名称与证书编号必填");
        }
        q.setQualificationId(null);
        q.setAuditStatus(0);
        q.setAuditorId(null);
        qualificationMapper.insert(q);
        return Result.ok(q);
    }
}
