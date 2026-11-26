package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 电子账单（生成后金额不可改，退款红冲） */
@Data
@TableName("bill")
public class Bill {
    @TableId(type = IdType.AUTO)
    private Long billId;
    private String billNo;
    private Long recordId;
    private Long patientId;
    private Long storeId;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal payable;
    private String payChannel;      // 微信/支付宝/现金
    private String payTradeNo;
    /** 0待支付 1已支付 2已开票 3部分退款 4全额退款 */
    private Integer status;
    private LocalDateTime payTime;
    /** 医保统筹报销金额（按项目医保比例自动核算） */
    private BigDecimal insuranceCover;
    /** 个人自付金额（实付 - 医保报销） */
    private BigDecimal selfPay;
}
