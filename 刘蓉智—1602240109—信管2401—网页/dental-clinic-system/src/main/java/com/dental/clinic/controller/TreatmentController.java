package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.TreatmentDetail;
import com.dental.clinic.entity.TreatmentRecord;
import com.dental.clinic.mapper.TreatmentRecordMapper;
import com.dental.clinic.service.TreatmentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 诊疗：提交病历（含过敏校验 + 自动生成账单） */
@RestController
@RequestMapping("/api/treatment")
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;
    private final TreatmentRecordMapper recordMapper;

    @Data
    public static class SubmitReq {
        private TreatmentRecord record;
        private List<TreatmentDetail> details;
    }

    /** 提交病历：过敏史强制校验 -> 记录+明细 -> 自动生成电子账单 */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody SubmitReq req) {
        TreatmentRecord rec = treatmentService.submit(req.getRecord(), req.getDetails());
        return Result.ok(Map.of(
                "recordId", rec.getRecordId(),
                "allergyCheck", rec.getAllergyCheck() == null ? 0 : rec.getAllergyCheck(),
                "message", rec.getAllergyCheck() != null && rec.getAllergyCheck() == 1
                        ? "过敏校验通过，病历与账单已生成"
                        : "过敏校验未通过，已拦截（记录留存备查）"));
    }

    @GetMapping("/record/{id}")
    public Result<TreatmentRecord> record(@PathVariable Long id) {
        return Result.ok(treatmentService.detail(id));
    }

    @GetMapping("/record/{id}/details")
    public Result<List<TreatmentDetail>> recordDetails(@PathVariable Long id) {
        return Result.ok(treatmentService.details(id));
    }

    /** 按患者查病历 */
    @GetMapping("/by-patient")
    public Result<List<TreatmentRecord>> byPatient(@RequestParam Long patientId) {
        return Result.ok(recordMapper.selectList(new LambdaQueryWrapper<TreatmentRecord>()
                .eq(TreatmentRecord::getPatientId, patientId)
                .orderByDesc(TreatmentRecord::getRecordId)));
    }
}
