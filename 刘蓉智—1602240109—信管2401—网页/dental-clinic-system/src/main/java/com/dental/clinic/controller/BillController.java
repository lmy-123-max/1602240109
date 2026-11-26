package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Bill;
import com.dental.clinic.entity.BillDetail;
import com.dental.clinic.mapper.BillDetailMapper;
import com.dental.clinic.mapper.BillMapper;
import com.dental.clinic.service.BillService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 电子账单：查询 / 支付 / 退款红冲 */
@RestController
@RequestMapping("/api/bill")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;
    private final BillMapper billMapper;
    private final BillDetailMapper billDetailMapper;

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Bill bill = billService.detail(id);
        List<BillDetail> items = billDetailMapper.selectList(new LambdaQueryWrapper<BillDetail>()
                .eq(BillDetail::getBillId, id));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bill", bill);
        out.put("items", items);
        return Result.ok(out);
    }

    @GetMapping("/by-no")
    public Result<Bill> byNo(@RequestParam String billNo) {
        return Result.ok(billService.findByNo(billNo));
    }

    /** 按患者查账单 */
    @GetMapping("/by-patient")
    public Result<List<Bill>> byPatient(@RequestParam Long patientId) {
        return Result.ok(billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getPatientId, patientId)
                .orderByDesc(Bill::getBillId)));
    }

    @Data
    public static class PayReq {
        private String payChannel;   // 微信/支付宝/现金
    }

    /** 支付：挂号费预付款抵扣诊疗费 */
    @PostMapping("/{id}/pay")
    public Result<Bill> pay(@PathVariable Long id, @RequestBody(required = false) PayReq req) {
        String channel = req == null ? "微信" : req.getPayChannel();
        return Result.ok(billService.pay(id, channel));
    }

    /** 退款红冲 */
    @PostMapping("/{id}/refund")
    public Result<Bill> refund(@PathVariable Long id) {
        return Result.ok(billService.refund(id));
    }
}
