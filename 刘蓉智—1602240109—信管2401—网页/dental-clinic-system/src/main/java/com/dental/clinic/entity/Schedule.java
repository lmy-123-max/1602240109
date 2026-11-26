package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 排班（门店 + 牙医 + 日期 + 时段，号源容量不超过可用牙椅数） */
@Data
@TableName("schedule")
public class Schedule {
    @TableId(type = IdType.AUTO)
    private Long scheduleId;
    private Long doctorId;
    private Long storeId;
    private LocalDate workDate;
    /** 1上午 2下午 3晚间 */
    private Integer timeSlot;
    private Long chairId;
    private Integer quotaTotal;
    private Integer quotaUsed;
    /** 0草稿 1已发布 2已停用 */
    private Integer status;
}
