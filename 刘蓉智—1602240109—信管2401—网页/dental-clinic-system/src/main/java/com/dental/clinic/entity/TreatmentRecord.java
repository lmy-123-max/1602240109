package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 诊疗记录（病历主档） */
@Data
@TableName("treatment_record")
public class TreatmentRecord {
    @TableId(type = IdType.AUTO)
    private Long recordId;
    private Long appointmentId;
    private Long doctorId;
    private Long patientId;
    private String chiefComplaint;
    private String diagnosis;
    /** 过敏史校验：0未通过 1通过 */
    private Integer allergyCheck;
    private String toothPosition;
    private String visitStatus;
    private LocalDate nextVisit;
}
