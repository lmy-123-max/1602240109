package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 工作日历：法定节假日 / 调休 / 门店营业时间 */
@Data
@TableName("work_calendar")
public class WorkCalendar {
    @TableId(type = IdType.AUTO)
    private Long calendarId;
    private Long storeId;
    private LocalDate calDate;
    /** 1工作日 2法定节假日 3调休补班 */
    private Integer dayType;
    private LocalTime openTime;
    private LocalTime closeTime;
    private String remark;
}
