package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录日志（sys_login_log）：记录每一次用户进入系统（成功或失败），支持审计追溯 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long userId;        // 成功时为用户ID，失败时可为空
    private String account;     // 尝试登录的账号
    private String realName;    // 姓名（成功时冗余）
    private String roleName;    // 身份（患者/牙医/门店管理员/总部管理员），失败时为空
    private String ip;          // 客户端 IP
    private String userAgent;   // 浏览器/设备标识（截断存储）
    private Integer success;    // 1 成功 0 失败
    private String failReason;  // 失败原因
    private LocalDateTime loginTime;
}
