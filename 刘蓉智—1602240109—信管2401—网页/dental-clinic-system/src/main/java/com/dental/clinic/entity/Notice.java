package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 公告表（门店/总部发布就诊通知） */
@Data
@TableName("notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    private Long noticeId;
    private String title;
    private String content;
    private Long publisherId;
    private LocalDateTime publishTime;
    /** 1已发布 0已下架 */
    private Integer status;
}
