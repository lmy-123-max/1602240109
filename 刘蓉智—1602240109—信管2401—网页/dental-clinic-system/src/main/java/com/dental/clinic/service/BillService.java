package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.Appointment;
import com.dental.clinic.entity.Bill;
import com.dental.clinic.mapper.AppointmentMapper;
import com.dental.clinic.mapper.BillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 账单服务：电子账单生成后金额不可改，退款采用红冲方式
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillMapper billMapper;
    private final AppointmentMapper appointmentMapper;
    private final com.dental.clinic.mapper.PatientMapper patientMapper;
    private final com.dental.clinic.mapper.UserMapper userMapper;
    private final com.dental.clinic.service.OpLogService opLogService;

    /** 支付：待支付 -> 已支付，挂号费预付款抵扣诊疗费 */
    @Transactional
    public Bill pay(Long billId, String payChannel) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        if (bill.getStatus() != 0) {
            throw new BusinessException("账单当前状态不可支付（status=" + bill.getStatus() + "）");
        }
        bill.setPayChannel(payChannel);
        bill.setPayTradeNo("T" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        bill.setPayTime(LocalDateTime.now());
        bill.setStatus(1);
        billMapper.updateById(bill);

        // 预付挂号费在结算时抵扣诊疗费
        Appointment appt = appointmentMapper.selectById(bill.getRecordId() == null ? null
                : recordToAppointment(bill.getRecordId()));
        if (appt != null && appt.getDepositStatus() != null && appt.getDepositStatus() == 1) {
            appt.setDepositStatus(3);
            appointmentMapper.updateById(appt);
        }
        return bill;
    }

    private Long recordToAppointment(Long recordId) {
        com.dental.clinic.entity.TreatmentRecord rec =
                recordMapper.selectById(recordId);
        return rec == null ? null : rec.getAppointmentId();
    }

    private final com.dental.clinic.mapper.TreatmentRecordMapper recordMapper;

    /** 退款（红冲）：已支付 -> 全额退款，原账单保留不改金额 */
    @Transactional
    public Bill refund(Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        if (bill.getStatus() != 1 && bill.getStatus() != 2) {
            throw new BusinessException("仅已支付/已开票账单可退款红冲");
        }
        bill.setStatus(4);
        billMapper.updateById(bill);
        com.dental.clinic.entity.User payer = userMapper.selectById(bill.getPatientId());
        opLogService.record(bill.getPatientId(), payer == null || payer.getRealName() == null ? "患者" : payer.getRealName(),
                "REFUND", "账单", bill.getBillNo(),
                "退款红冲：实收 ¥" + bill.getPayable() + "（医保报销部分按原渠道退回统筹基金）");

        // 退回预付挂号费（如尚未抵扣）
        if (bill.getRecordId() != null) {
            com.dental.clinic.entity.TreatmentRecord rec = recordMapper.selectById(bill.getRecordId());
            if (rec != null) {
                Appointment appt = appointmentMapper.selectById(rec.getAppointmentId());
                if (appt != null && appt.getDepositStatus() != null && appt.getDepositStatus() == 1) {
                    appt.setDepositStatus(2);
                    appointmentMapper.updateById(appt);
                }
            }
        }
        return bill;
    }

    public Bill detail(Long billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        return bill;
    }

    public Bill findByNo(String billNo) {
        Bill bill = billMapper.selectOne(new LambdaQueryWrapper<Bill>().eq(Bill::getBillNo, billNo));
        if (bill == null) {
            throw new BusinessException("账单不存在");
        }
        return bill;
    }
}
