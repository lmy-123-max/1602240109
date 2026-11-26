package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 过敏记录（患者一对多，医学高风险事项结构化记录） */
@Data
@TableName("allergy_record")
public class AllergyRecord {
    @TableId(type = IdType.AUTO)
    private Long allergyId;
    private Long patientId;
    private String allergenName;    // 过敏原（青霉素/乳胶等）
    /** 1药物 2食物 3材料 */
    private Integer allergyType;
    /** 1轻度 2中度 3重度 */
    private Integer severity;
    private String reactionDesc;
    /** 1患者自述 2医生问诊录入 */
    private Integer source;
    private LocalDate recordDate;
    /** 1有效 0已失效 */
    private Integer status;
}
