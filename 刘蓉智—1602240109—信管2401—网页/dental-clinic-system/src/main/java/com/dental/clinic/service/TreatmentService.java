package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗服务：过敏史强制校验 → 病历 + 诊疗明细保存 → 按价目库自动计价生成电子账单
 */
@Service
@RequiredArgsConstructor
public class TreatmentService {

    private final TreatmentRecordMapper recordMapper;
    private final TreatmentDetailMapper detailMapper;
    private final TreatmentItemMapper itemMapper;
    private final AllergyRecordMapper allergyMapper;
    private final AppointmentMapper appointmentMapper;
    private final BillMapper billMapper;
    private final BillDetailMapper billDetailMapper;
    private final ScheduleMapper scheduleMapper;

    /** 提交诊疗：状态机 2已到诊 → 3诊疗中 → 4已完成，并自动生成账单 */
    @Transactional
    public TreatmentRecord submit(TreatmentRecord record, List<TreatmentDetail> details) {
        Appointment appt = appointmentMapper.selectById(record.getAppointmentId());
        if (appt == null) {
            throw new BusinessException("预约单不存在");
        }
        if (appt.getStatus() != 2 && appt.getStatus() != 3) {
            throw new BusinessException("预约未到诊，不可录入诊疗（请先核销）");
        }
        // 从预约单回填病历归属（医生/患者）
        record.setDoctorId(appt.getDoctorId());
        record.setPatientId(appt.getPatientId());
        // 医疗安全：处方/麻醉前过敏史强制校验（青霉素等过敏原与药品比对演示）
        List<AllergyRecord> allergies = allergyMapper.selectList(new LambdaQueryWrapper<AllergyRecord>()
                .eq(AllergyRecord::getPatientId, appt.getPatientId())
                .eq(AllergyRecord::getStatus, 1));
        StringBuilder conflict = new StringBuilder();
        for (TreatmentDetail d : details) {
            TreatmentItem item = itemMapper.selectById(d.getItemId());
            if (item == null) continue;
            for (AllergyRecord al : allergies) {
                if (item.getItemName().contains(al.getAllergenName())) {
                    conflict.append(item.getItemName()).append("与过敏原[").append(al.getAllergenName()).append("]冲突；");
                }
            }
        }
        if (conflict.length() > 0) {
            record.setAllergyCheck(0);
            recordMapper.insert(record);
            throw new BusinessException("过敏校验拦截：" + conflict + "处方已留存拦截记录");
        }
        record.setAllergyCheck(1);
        recordMapper.insert(record);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cover = BigDecimal.ZERO;
        List<BillDetail> billDetails = new ArrayList<>();
        for (TreatmentDetail d : details) {
            TreatmentItem item = itemMapper.selectById(d.getItemId());
            if (item == null) continue;
            d.setRecordId(record.getRecordId());
            if (d.getQuantity() == null || d.getQuantity() < 1) d.setQuantity(1);
            d.setUnitPrice(item.getPrice());
            detailMapper.insert(d);
            BillDetail bd = new BillDetail();
            bd.setItemName(item.getItemName());
            bd.setQuantity(d.getQuantity());
            BigDecimal amt = item.getPrice().multiply(BigDecimal.valueOf(d.getQuantity()));
            bd.setAmount(amt);
            billDetails.add(bd);
            total = total.add(amt);
            // 医保核算：甲类全额报销、乙类按比例、自费不报
            BigDecimal rate = item.getInsuranceRate() == null ? BigDecimal.ZERO : item.getInsuranceRate();
            cover = cover.add(amt.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // 自动生成电子账单（预付挂号费抵扣 + 医保统筹报销/个人自付自动拆分）
        Bill bill = new Bill();
        bill.setBillNo("B" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", record.getRecordId()));
        bill.setRecordId(record.getRecordId());
        bill.setPatientId(appt.getPatientId());
        bill.setStoreId(appt.getStoreId());
        bill.setTotalAmount(total);
        bill.setDiscount(appt.getDepositAmount() != null ? appt.getDepositAmount() : BigDecimal.ZERO);
        bill.setPayable(total.subtract(bill.getDiscount()).max(BigDecimal.ZERO));
        bill.setInsuranceCover(cover.min(bill.getPayable()));
        bill.setSelfPay(bill.getPayable().subtract(bill.getInsuranceCover()));
        bill.setStatus(0);
        billMapper.insert(bill);
        for (BillDetail bd : billDetails) {
            bd.setBillId(bill.getBillId());
            billDetailMapper.insert(bd);
        }

        appt.setStatus(4); // 已完成
        appointmentMapper.updateById(appt);
        return record;
    }

    /** 病历详情（含明细） */
    public TreatmentRecord detail(Long recordId) {
        return recordMapper.selectById(recordId);
    }

    public List<TreatmentDetail> details(Long recordId) {
        return detailMapper.selectList(new LambdaQueryWrapper<TreatmentDetail>()
                .eq(TreatmentDetail::getRecordId, recordId));
    }
}
