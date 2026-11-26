package com.dental.clinic.config;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 启动前恢复助手。
 *
 * 恢复流程（避免运行中替换数据库文件）：
 *  1. 管理后台点击“恢复” → BackupService 把所选备份复制为 data/restore-pending.mv.db；
 *  2. 用户关闭系统并重新启动；
 *  3. 本类在 Spring 启动之前（DataSource 尚未打开文件）完成替换：
 *     当前库先改名留档为 data/dental-before-restore-<时间>.mv.db，再启用备份文件。
 */
public final class RestoreHelper {

    private RestoreHelper() {
    }

    public static void applyPendingRestore() {
        Path dataDir = Paths.get(System.getProperty("app.data-dir", "./data")).toAbsolutePath().normalize();
        Path pending = dataDir.resolve("restore-pending.mv.db");
        if (!Files.exists(pending)) {
            return;
        }
        try {
            Path current = dataDir.resolve("dental.mv.db");
            if (Files.exists(current)) {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path backup = dataDir.resolve("dental-before-restore-" + stamp + ".mv.db");
                Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[恢复] 当前数据库已留档：" + backup.getFileName());
            }
            Files.deleteIfExists(dataDir.resolve("dental.trace.db"));
            Files.move(pending, current, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[恢复] 已使用备份恢复数据库：" + current);
        } catch (IOException e) {
            System.err.println("[恢复] 恢复失败：" + e.getMessage());
        }
    }
}
