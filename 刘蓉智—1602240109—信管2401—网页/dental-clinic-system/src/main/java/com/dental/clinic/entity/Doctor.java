package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 牙医（子表，主键同步 sys_user） */
@Data
@TableName("doctor")
public class Doctor {
    @TableId(type = IdType.INPUT)
    private Long doctorId;
    private String jobNo;
    private String title;           // 主任医师/副主任医师/主治医师/住院医师
    private String specialty;       // 执业范围
    private String practiceNo;
    private Long homeStoreId;
    /** 激励提成比例，默认 0.10 */
    private BigDecimal incentiveRate;
}
