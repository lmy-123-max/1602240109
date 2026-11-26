package com.dental.clinic.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据备份与恢复服务（生产可用版核心组件）
 *
 * 设计要点：
 *  - 数据库为文件型 H2（./data/dental.mv.db），备份即快照复制该文件；
 *  - 每天凌晨 02:30 自动备份一次，启动时若发现超过 20 小时没有备份也会补一次；
 *  - 只保留最近 N 份（默认 30），自动清理更早的，避免磁盘膨胀；
 *  - 备份文件可直接下载保存到 U 盘/网盘；
 *  - 恢复采用“暂存 + 重启生效”策略：把备份复制为 data/restore-pending.mv.db，
 *    下次启动前由 RestoreHelper 原子替换，避免运行中替换数据库文件造成损坏。
 */
@Service
@Order(3)
public class BackupService implements ApplicationRunner {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NAME_PATTERN = Pattern.compile("^dental-\\d{8}-\\d{6}\\.zip$");

    private final JdbcTemplate jdbcTemplate;
    private final Path dataDir;
    private final Path backupDir;
    private final int keep;

    public BackupService(JdbcTemplate jdbcTemplate,
                         @Value("${app.data-dir:./data}") String dataDir,
                         @Value("${app.backup-dir:./backup}") String backupDir,
                         @Value("${app.backup-keep:30}") int keep) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
        this.backupDir = Paths.get(backupDir).toAbsolutePath().normalize();
        this.keep = keep;
    }

    /* ==================== 启动补备份 ==================== */

    @Override
    public void run(ApplicationArguments args) {
        try {
            Files.createDirectories(backupDir);
            Optional<LocalDateTime> latest = listBackups().stream()
                    .map(b -> (LocalDateTime) b.get("time"))
                    .max(Comparator.naturalOrder());
            if (latest.isEmpty() || latest.get().isBefore(LocalDateTime.now().minusHours(20))) {
                Map<String, Object> b = createBackup();
                System.out.println("[备份] 已完成启动备份：" + b.get("name"));
            }
        } catch (Exception e) {
            System.err.println("[备份] 启动备份失败：" + e.getMessage());
        }
    }

    /* ==================== 自动备份 ==================== */

    @Scheduled(cron = "${app.backup-cron:0 30 2 * * ?}")
    public void scheduledBackup() {
        try {
            Map<String, Object> b = createBackup();
            System.out.println("[备份] 定时备份完成：" + b.get("name"));
        } catch (Exception e) {
            System.err.println("[备份] 定时备份失败：" + e.getMessage());
        }
    }

    /* ==================== 备份 ==================== */

    public synchronized Map<String, Object> createBackup() throws IOException {
        Files.createDirectories(backupDir);
        String name = "dental-" + LocalDateTime.now().format(STAMP) + ".zip";
        Path target = backupDir.resolve(name);
        // H2 运行期间独占数据库文件，不能直接复制；
        // 使用 H2 官方 BACKUP TO 命令生成一致性快照（zip 内含 dental.mv.db）
        String sqlPath = target.toString().replace('\\', '/').replace("'", "''");
        jdbcTemplate.execute("BACKUP TO '" + sqlPath + "'");
        if (!Files.exists(target)) {
            throw new IllegalStateException("备份文件未生成，请检查备份目录权限");
        }
        prune();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("size", Files.size(target));
        m.put("time", LocalDateTime.now());
        m.put("path", target.toString());
        return m;
    }

    private void prune() {
        try (Stream<Path> s = Files.list(backupDir)) {
            List<Path> files = s.filter(p -> NAME_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .collect(Collectors.toList());
            for (int i = keep; i < files.size(); i++) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (IOException e) {
            System.err.println("[备份] 清理旧备份失败：" + e.getMessage());
        }
    }

    /* ==================== 查询与下载 ==================== */

    public List<Map<String, Object>> listBackups() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(backupDir)) return out;
        try (Stream<Path> s = Files.list(backupDir)) {
            List<Path> files = s.filter(p -> NAME_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .collect(Collectors.toList());
            for (Path p : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("size", Files.size(p));
                m.put("time", LocalDateTime.parse(p.getFileName().toString()
                        .replace("dental-", "").replace(".zip", ""), STAMP));
                out.add(m);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取备份目录失败：" + e.getMessage());
        }
        return out;
    }

    public Path resolve(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("备份文件名不合法");
        }
        Path p = backupDir.resolve(name).normalize();
        if (!p.startsWith(backupDir) || !Files.exists(p)) {
            throw new IllegalArgumentException("备份不存在");
        }
        return p;
    }

    /* ==================== 恢复（重启后生效） ==================== */

    public synchronized Map<String, Object> stageRestore(String name) throws IOException {
        Path src = resolve(name);
        Files.createDirectories(dataDir);
        Path pending = dataDir.resolve("restore-pending.mv.db");
        boolean found = false;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(src))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().endsWith(".mv.db")) {
                    Files.copy(zis, pending, StandardCopyOption.REPLACE_EXISTING);
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new IllegalStateException("备份包中未找到数据库文件，可能已损坏");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("pending", pending.toString());
        m.put("message", "备份已就绪。请关闭系统后重新启动，系统会自动用该备份替换当前数据库。");
        return m;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getBackupDir() {
        return backupDir;
    }
}
