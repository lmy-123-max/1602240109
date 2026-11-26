package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 供应商主数据 */
@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Long supplierId;
    private String supplierName;
    private String contactPerson;
    private String phone;
    private String address;
    private String supplyScope;    // 设备/耗材/药品
    private String settleCycle;
    private Integer status;
}
