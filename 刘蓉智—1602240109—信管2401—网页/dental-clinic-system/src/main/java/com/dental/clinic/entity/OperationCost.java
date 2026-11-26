package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 门店季度运营成本单（房租/人工/设备折旧） */
@Data
@TableName("operation_cost")
public class OperationCost {
    @TableId(type = IdType.AUTO)
    private Long costId;
    private Long storeId;
    private Integer year;
    private Integer quarter;
    private BigDecimal rent;
    private BigDecimal labor;
    private BigDecimal depreciation;   // 牙椅月折旧额汇总
    private BigDecimal amount;
}
