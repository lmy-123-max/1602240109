package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.AllergyRecord;
import com.dental.clinic.entity.Patient;
import com.dental.clinic.mapper.AllergyRecordMapper;
import com.dental.clinic.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 患者档案与过敏史管理（过敏史为独立实体，1 名患者 0..n 条过敏记录） */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientMapper patientMapper;
    private final AllergyRecordMapper allergyMapper;

    @GetMapping("/{id}")
    public Result<Patient> detail(@PathVariable Long id) {
        return Result.ok(patientMapper.selectById(id));
    }

    @GetMapping("/{id}/allergies")
    public Result<List<AllergyRecord>> allergies(@PathVariable Long id) {
        return Result.ok(allergyMapper.selectList(new LambdaQueryWrapper<AllergyRecord>()
                .eq(AllergyRecord::getPatientId, id)
                .eq(AllergyRecord::getStatus, 1)));
    }

    /** 新增过敏记录（就诊时前台/医生补录） */
    @PostMapping("/{id}/allergies")
    public Result<AllergyRecord> addAllergy(@PathVariable Long id, @RequestBody AllergyRecord allergy) {
        allergy.setPatientId(id);
        if (allergy.getStatus() == null) {
            allergy.setStatus(1);
        }
        allergyMapper.insert(allergy);
        return Result.ok(allergy);
    }

    /** 停用（删除）过敏记录 */
    @PostMapping("/allergies/{allergyId}/disable")
    public Result<String> disableAllergy(@PathVariable Long allergyId) {
        AllergyRecord rec = allergyMapper.selectById(allergyId);
        if (rec == null) {
            return Result.fail("过敏记录不存在");
        }
        rec.setStatus(0);
        allergyMapper.updateById(rec);
        return Result.ok("已停用");
    }
}
