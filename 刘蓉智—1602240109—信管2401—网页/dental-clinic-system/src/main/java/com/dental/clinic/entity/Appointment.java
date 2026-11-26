package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 预约单（状态机 + 预付挂号费 + 取消违约金） */
@Data
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Long appointmentId;
    private String appointmentNo;
    private Long patientId;
    private Long doctorId;
    private Long scheduleId;
    private Long storeId;
    private LocalDate visitDate;
    private String timeSlot;
    private BigDecimal depositAmount;   // 预付挂号费（20 元）
    /** 0未预付 1已预付 2已退回 3抵扣诊疗费 */
    private Integer depositStatus;
    private BigDecimal cancelFee;       // 取消违约金
    private LocalDateTime remindTime;
    /** 0已提交 1已确认 2已到诊 3诊疗中 4已完成 5已取消 6爽约 */
    private Integer status;
    private String cancelReason;
    private LocalDateTime createTime;
}
