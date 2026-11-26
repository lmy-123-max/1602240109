package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 诊疗明细（随病历组合，生命周期绑定） */
@Data
@TableName("treatment_detail")
public class TreatmentDetail {
    @TableId(type = IdType.AUTO)
    private Long detailId;
    private Long recordId;
    private Long itemId;            // 关联价目库
    private String toothPosition;
    private Integer quantity;
    private BigDecimal unitPrice;   // 成交单价（快照）
    private Long materialId;
}
