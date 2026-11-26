package com.dental.clinic.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库引导器（生产可用版核心组件）
 *
 * 与演示版的关键区别：
 *  - 演示版每次启动清空内存库、重建表并重灌种子数据；
 *  - 本版使用文件型 H2（./data/dental.mv.db），数据永久保存。
 *
 * 启动逻辑：
 *  1. 检查 PUBLIC 模式下是否已有业务表；
 *  2. 无表（首次部署）→ 执行 schema-h2.sql 建表 + data-h2.sql 初始化基础数据（门店/医生/价目/账号）；
 *  3. 已有表 → 完全跳过初始化，历史业务数据原样保留。
 *
 * 因此重启、升级 jar 都不会丢数据；需要恢复初始状态时，删除 data 目录即可（系统会重新初始化）。
 */
@Component
@Order(1)
public class DatabaseBootstrap implements ApplicationRunner {

    private final DataSource dataSource;

    public DatabaseBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean firstRun = isDatabaseEmpty();
        if (firstRun) {
            System.out.println("[数据库] 首次部署，开始建表并写入基础数据 ...");
            try (Connection conn = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(conn,
                        new EncodedResource(new ClassPathResource("sql/schema-h2.sql"), StandardCharsets.UTF_8));
                ScriptUtils.executeSqlScript(conn,
                        new EncodedResource(new ClassPathResource("sql/data-h2.sql"), StandardCharsets.UTF_8));
            }
            System.out.println("[数据库] 初始化完成：25 张表 + 基础数据（门店、医生、价目、账号）。");
        } else {
            System.out.println("[数据库] 检测到已有数据，跳过初始化，历史数据继续保留。");
        }
        // 幂等迁移：无论新老库，缺哪张新表就补哪张（升级不丢历史数据）
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS sys_login_log (" +
                    "log_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT, account VARCHAR(50), real_name VARCHAR(50), role_name VARCHAR(30)," +
                    "ip VARCHAR(64), user_agent VARCHAR(300), success TINYINT DEFAULT 1," +
                    "fail_reason VARCHAR(200), login_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
            // 操作留痕表（第 26 张表）：关键业务增减动作全量留痕
            st.execute("CREATE TABLE IF NOT EXISTS sys_op_log (" +
                    "log_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "operator_id BIGINT, operator_name VARCHAR(50)," +
                    "action VARCHAR(20), target_type VARCHAR(30), target_desc VARCHAR(200)," +
                    "detail VARCHAR(500), op_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
            // 医保改版迁移：价目表补医保属性列，账单补医保报销/自付列（老库自动升级）
            if (!columnExists(conn, "TREATMENT_ITEM", "INSURANCE_TYPE")) {
                st.execute("ALTER TABLE treatment_item ADD COLUMN insurance_type VARCHAR(10) DEFAULT '自费'");
                st.execute("ALTER TABLE treatment_item ADD COLUMN insurance_rate DECIMAL(4,2) DEFAULT 0");
                st.execute("UPDATE treatment_item SET insurance_type='甲类', insurance_rate=1.00 WHERE item_code IN " +
                        "('D001','D002','D003','D007','D010','D012','D013','D020','D021')");
                st.execute("UPDATE treatment_item SET insurance_type='乙类', insurance_rate=0.70 WHERE item_code IN " +
                        "('D005','D008','D009','D011')");
                st.execute("UPDATE treatment_item SET insurance_type='自费', insurance_rate=0 WHERE insurance_type IS NULL");
            }
            if (!columnExists(conn, "BILL", "INSURANCE_COVER")) {
                st.execute("ALTER TABLE bill ADD COLUMN insurance_cover DECIMAL(12,2) DEFAULT 0");
                st.execute("ALTER TABLE bill ADD COLUMN self_pay DECIMAL(12,2) DEFAULT 0");
            }
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, "PUBLIC", table, column)) {
            return rs.next();
        }
    }

    private boolean isDatabaseEmpty() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                             "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'")) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }
}
