package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 牙椅（诊疗设备）：稀缺资源，含折旧与单次分摊成本 */
@Data
@TableName("dental_chair")
public class DentalChair {
    @TableId(type = IdType.AUTO)
    private Long chairId;
    private Long storeId;
    private String chairNo;
    private String deviceName;
    private BigDecimal purchasePrice;
    private LocalDate purchaseDate;
    private Integer deprecYears;
    private BigDecimal monthlyDeprec;
    private BigDecimal perUseCost;
    private Long supplierId;
    /** 1在用 2维修 3报废 */
    private Integer status;
}
