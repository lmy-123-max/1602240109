package com.dental.clinic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 权限点表（菜单/按钮/接口） */
@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long permId;
    private String permName;
    private String permType;
    private Long parentId;
}
