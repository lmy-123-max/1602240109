package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 患者（子表，主键同步 sys_user） */
@Data
@TableName("patient")
public class Patient {
    /** 患者 ID，同步 sys_user.user_id */
    @TableId(type = IdType.INPUT)
    private Long patientId;
    private String healthCardNo;
    private Long defaultStoreId;
    private LocalDate birthday;
    private String idCard;
    private String emergencyPhone;
}
