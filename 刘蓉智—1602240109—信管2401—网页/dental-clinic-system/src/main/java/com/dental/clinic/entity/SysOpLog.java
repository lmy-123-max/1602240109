package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务操作留痕表（第 26 张表）：
 * 人员增减、价目增减、公告上下架、退款红冲、账户注销等关键操作全量留痕，
 * 满足《医疗保障基金使用监督管理条例》与内部审计要求：每一次增减都有数可查。
 */
@Data
@TableName("sys_op_log")
public class SysOpLog {
    @TableId(type = IdType.AUTO)
    private Long logId;
    private Long operatorId;
    private String operatorName;
    /** ADD 新增 / DELETE 删除 / UPDATE 变更 / OFF 下架 / REFUND 退款 / CANCEL 注销 */
    private String action;
    /** 对象类型：人员 / 医生 / 价目 / 供应商 / 公告 / 账单 / 账户 / 排班 ... */
    private String targetType;
    /** 对象摘要（名称、编码等） */
    private String targetDesc;
    private String detail;
    private LocalDateTime opTime;
}
