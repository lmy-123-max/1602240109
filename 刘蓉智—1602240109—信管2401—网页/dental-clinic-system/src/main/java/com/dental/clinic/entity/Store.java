package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 门店 */
@Data
@TableName("store")
public class Store {
    @TableId(type = IdType.AUTO)
    private Long storeId;
    private String storeCode;
    private String storeName;
    private String address;
    private String phone;
    private Long managerId;
    private LocalDate openDate;
    private Integer status;         // 1营业 0停业
}
