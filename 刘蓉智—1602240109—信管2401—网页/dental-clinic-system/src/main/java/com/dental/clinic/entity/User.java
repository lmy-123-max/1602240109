package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户（抽象基类，对应 sys_user，混合建表方案的父表） */
@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String account;
    private String password;
    private String realName;
    private Integer gender;     // 0女 1男
    private String phone;
    private Integer status;     // 0禁用 1正常
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String role;        // patient/doctor/storeAdmin/hqAdmin，登录时由服务层填充
    @TableField(exist = false)
    private Long roleId;        // 对应子表主键（patient_id / doctor_id 等）
}
