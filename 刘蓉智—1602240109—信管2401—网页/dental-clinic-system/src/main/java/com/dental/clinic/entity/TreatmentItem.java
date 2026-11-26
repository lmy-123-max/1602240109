package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 治疗项目价目库（含材料成本） */
@Data
@TableName("treatment_item")
public class TreatmentItem {
    @TableId(type = IdType.AUTO)
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String category;        // 补牙/种植/正畸/洁牙等
    private String unit;
    private BigDecimal price;       // 标准价格
    private BigDecimal materialCost;// 材料成本（耗材进价）
    /** 1启用 0停用 */
    private Integer status;
    /** 医保属性：甲类（全额纳入报销）/ 乙类（部分报销）/ 自费 */
    private String insuranceType;
    /** 医保报销比例（0~1）：甲类 1.00、乙类 0.70、自费 0 */
    private BigDecimal insuranceRate;
}
