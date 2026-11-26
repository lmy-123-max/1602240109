package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 牙医资质证书表（执业资格，含审核状态） */
@Data
@TableName("doctor_qualification")
public class DoctorQualification {
    @TableId(type = IdType.AUTO)
    private Long qualificationId;
    private Long doctorId;
    private String qualName;
    private String certNo;
    private String issueOrg;
    private LocalDate expireDate;
    /** 0待初审 1初审通过 2终审通过 3已驳回 */
    private Integer auditStatus;
    private Long auditorId;
}
