package com.dental.clinic;

import com.dental.clinic.config.RestoreHelper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 牙科连锁诊所管理系统启动类
 * 《系统分析与设计》课程项目 —— 规约驱动开发（SBD）
 *
 * 生产可用版：数据库改为文件型 H2（./data/dental.mv.db），重启不丢数据；
 * 每日自动备份至 ./backup，支持手动备份、下载与恢复。
 */
@SpringBootApplication
@MapperScan("com.dental.clinic.mapper")
@EnableScheduling
public class DentalClinicApplication {
    public static void main(String[] args) {
        // 若上次在后台点击了“恢复备份”，在打开数据库连接前完成文件替换
        RestoreHelper.applyPendingRestore();
        SpringApplication.run(DentalClinicApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  牙科连锁诊所管理系统启动成功: http://localhost:8080");
        System.out.println("  数据目录: ./data    备份目录: ./backup");
        System.out.println("==============================================");
    }
}
