package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 账单明细（项目名称快照，保证历史可追溯） */
@Data
@TableName("bill_detail")
public class BillDetail {
    @TableId(type = IdType.AUTO)
    private Long billDetailId;
    private Long billId;
    private String itemName;
    private Integer quantity;
    private BigDecimal amount;
}
