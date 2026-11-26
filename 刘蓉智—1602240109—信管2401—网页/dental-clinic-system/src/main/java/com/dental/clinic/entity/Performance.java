package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 季度业绩核算（净利润提成制） */
@Data
@TableName("performance")
public class Performance {
    @TableId(type = IdType.AUTO)
    private Long performanceId;
    private Long doctorId;
    private Long storeId;
    private Integer year;
    private Integer quarter;
    private BigDecimal revenue;        // 经办收入合计
    private BigDecimal directCost;     // 直接耗材成本
    private BigDecimal shareCost;      // 分摊运营成本
    private BigDecimal netProfit;      // 净利润 = 营收 − 成本
    private BigDecimal incentiveAmount;// 激励奖金 = 净利润 × 10%
    /** 0待确认 1已确认 */
    private Integer status;
}
