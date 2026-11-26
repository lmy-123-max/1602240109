package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 采购记录（设备/耗材/药品，进价链路） */
@Data
@TableName("purchase_record")
public class PurchaseRecord {
    @TableId(type = IdType.AUTO)
    private Long purchaseId;
    private Long storeId;
    private Long supplierId;
    /** 1设备 2耗材 3药品 */
    private Integer itemType;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;   // 进价
    private BigDecimal totalAmount;
    private LocalDate purchaseDate;
    private Long operatorId;
}
