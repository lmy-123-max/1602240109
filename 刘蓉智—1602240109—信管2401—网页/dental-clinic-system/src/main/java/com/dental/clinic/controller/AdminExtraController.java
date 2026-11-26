package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import com.dental.clinic.service.AuthService;
import com.dental.clinic.service.BackupService;
import lombok.Data;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台扩展接口：账号 / 患者 / 门店 / 运营成本 / 角色授权 / 预约管理。
 * 供 admin.html（Vue3 管理控制台）调用，与 AdminController 互补。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminExtraController {

    private final UserMapper userMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final StoreMapper storeMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysPermissionMapper permMapper;
    private final AllergyRecordMapper allergyMapper;
    private final OperationCostMapper costMapper;
    private final AppointmentMapper appointmentMapper;
    private final DentalChairMapper chairMapper;
    private final BillMapper billMapper;
    private final BackupService backupService;
    private final SysLoginLogMapper loginLogMapper;
    private final SysOpLogMapper opLogMapper;
    private final com.dental.clinic.service.OpLogService opLogService;

    public AdminExtraController(UserMapper userMapper, PatientMapper patientMapper, DoctorMapper doctorMapper, StoreMapper storeMapper, SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper, SysRolePermissionMapper rolePermMapper, SysPermissionMapper permMapper, AllergyRecordMapper allergyMapper, OperationCostMapper costMapper, AppointmentMapper appointmentMapper, DentalChairMapper chairMapper, BillMapper billMapper, BackupService backupService, SysLoginLogMapper loginLogMapper, SysOpLogMapper opLogMapper, com.dental.clinic.service.OpLogService opLogService) {
        this.userMapper = userMapper;
        this.patientMapper = patientMapper;
        this.doctorMapper = doctorMapper;
        this.storeMapper = storeMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermMapper = rolePermMapper;
        this.permMapper = permMapper;
        this.allergyMapper = allergyMapper;
        this.costMapper = costMapper;
        this.appointmentMapper = appointmentMapper;
        this.chairMapper = chairMapper;
        this.billMapper = billMapper;
        this.backupService = backupService;
        this.loginLogMapper = loginLogMapper;
        this.opLogMapper = opLogMapper;
        this.opLogService = opLogService;
    }

    /** 操作者姓名（留痕用）：取不到时记为"管理员" */
    private String operatorName(Long operatorId) {
        if (operatorId == null) return "管理员";
        User u = userMapper.selectById(operatorId);
        return u == null || u.getRealName() == null ? "管理员" : u.getRealName();
    }

    /** 管理后台允许的角色：总部管理员、门店管理员 */
    private void requireAdmin(Long operatorId) {
        if (operatorId == null) throw new BusinessException("请先登录管理后台");
        List<String> codes = roleCodesOf(operatorId);
        if (!codes.contains("hqAdmin") && !codes.contains("storeAdmin")) {
            throw new BusinessException("仅管理员可导出或维护数据");
        }
    }

    /* ==================== 账号管理 ==================== */

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userMapper.selectList(null)) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getUserId());
            m.put("account", u.getAccount());
            m.put("realName", u.getRealName());
            m.put("gender", u.getGender());
            m.put("phone", u.getPhone());
            m.put("status", u.getStatus());
            m.put("createTime", u.getCreateTime() == null ? null : u.getCreateTime().toLocalDate().toString());
            List<String> roles = new ArrayList<>();
            for (SysUserRole ur : userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, u.getUserId()))) {
                SysRole r = roleMapper.selectById(ur.getRoleId());
                if (r != null) roles.add(r.getRoleName());
            }
            m.put("roleNames", roles);
            u.setPassword(null);
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/users/{id}/status")
    public Result<String> toggleUser(@PathVariable Long id, @RequestParam Integer status) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("账号不存在");
        if (status != null && status == 0) {
            // 管理员不能把自己所在系统唯一的管理账号禁掉：总部管理员账号禁止禁用
            List<String> codes = roleCodesOf(id);
            if (codes.contains("hqAdmin")) throw new BusinessException("总部管理员账号不允许禁用");
        }
        u.setStatus(status);
        userMapper.updateById(u);
        return Result.ok("已" + (status != null && status == 1 ? "启用" : "禁用"));
    }

    @PostMapping("/users/{id}/reset-pwd")
    public Result<String> resetPwd(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("账号不存在");
        u.setPassword(AuthService.md5("123456"));
        userMapper.updateById(u);
        return Result.ok("密码已重置为 123456");
    }

    /** 新增工作人员：创建 sys_user 并绑定角色（支持 门店管理员/总部管理员；牙医请走医生管理） */
    @Data
    public static class StaffReq {
        private String account;
        private String password;
        private String realName;
        private Integer gender;   // 0女 1男
        private String phone;
        private String roleCode;  // storeAdmin / hqAdmin
        private Long storeId;     // 门店管理员可关联所属门店（写入备注：账号以 store 开头即按门店管理员推导）
    }

    @PostMapping("/users")
    @Transactional
    public Result<String> addStaff(@RequestBody StaffReq req,
                                   @RequestParam(required = false) Long operatorId) {
        requireAdmin(operatorId);
        if (req.getAccount() == null || req.getAccount().isBlank()) throw new BusinessException("登录账号必填");
        if (req.getRealName() == null || req.getRealName().isBlank()) throw new BusinessException("姓名必填");
        String pwd = req.getPassword() == null || req.getPassword().isBlank() ? "123456" : req.getPassword();
        String roleCode = req.getRoleCode() == null ? "storeAdmin" : req.getRoleCode();
        if (!"storeAdmin".equals(roleCode) && !"hqAdmin".equals(roleCode)) {
            throw new BusinessException("此处只能新增 门店管理员 / 总部管理员；牙医请到「医生管理」新增，患者自助注册");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, req.getAccount()));
        if (exists > 0) throw new BusinessException("该账号已存在");
        // 门店管理员账号约定以 store 开头，保证登录角色推导一致
        String account = req.getAccount().trim();
        if ("storeAdmin".equals(roleCode) && !account.startsWith("store")) {
            account = "store" + account;
        }
        User user = new User();
        user.setAccount(account);
        user.setPassword(AuthService.md5(pwd));
        user.setRealName(req.getRealName().trim());
        user.setGender(req.getGender());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) throw new BusinessException("角色不存在：" + roleCode);
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getUserId());
        ur.setRoleId(role.getRoleId());
        userRoleMapper.insert(ur);
        opLogService.record(operatorId, operatorName(operatorId), "ADD", "人员",
                req.getRealName().trim() + "（" + account + "）",
                "新增工作人员，身份 " + roleCode + "，初始密码 123456");
        return Result.ok("人员已新增，账号 " + account + "，初始密码 " + pwd);
    }

    /** 删除人员：移除账号与角色绑定（患者/牙医账号不允许在此删除） */
    @PostMapping("/users/{id}/delete")
    @Transactional
    public Result<String> deleteStaff(@PathVariable Long id,
                                      @RequestParam(required = false) Long operatorId) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("账号不存在");
        if (operatorId != null && operatorId.equals(id)) {
            throw new BusinessException("不能删除当前登录的账号");
        }
        if (patientMapper.selectById(id) != null) {
            throw new BusinessException("患者账号不能在人员管理中删除");
        }
        if (doctorMapper.selectById(id) != null) {
            throw new BusinessException("牙医账号请到「医生管理」中删除（会连同执业档案处理）");
        }
        List<String> codes = roleCodesOf(id);
        if (codes.contains("hqAdmin")) {
            Long hqCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, 4L)); // 4 = 总部管理员
            if (hqCount <= 1) throw new BusinessException("系统至少需要保留一个总部管理员账号");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
        opLogService.record(operatorId, operatorName(operatorId), "DELETE", "人员",
                (u.getRealName() == null ? u.getAccount() : u.getRealName()) + "（" + u.getAccount() + "）",
                "删除工作人员账号与角色绑定，历史业务数据保留");
        return Result.ok("人员「" + (u.getRealName() == null ? u.getAccount() : u.getRealName()) + "」已删除，历史业务数据保留");
    }

    /** 登录日志：谁在什么时候进入了系统（最近 limit 条，倒序） */
    @GetMapping("/login-logs")
    public Result<Map<String, Object>> loginLogs(@RequestParam(defaultValue = "200") Integer limit) {
        List<SysLoginLog> logs = loginLogMapper.selectList(
                new LambdaQueryWrapper<SysLoginLog>().orderByDesc(SysLoginLog::getLogId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
        long total = loginLogMapper.selectCount(null);
        String today = LocalDate.now().toString();
        long todayCount = loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .apply("CAST(login_time AS DATE) = CURRENT_DATE"));
        long failCount = loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getSuccess, 0));
        Map<String, Object> out = new HashMap<>();
        out.put("total", total);
        out.put("todayCount", todayCount);
        out.put("failCount", failCount);
        out.put("items", logs);
        return Result.ok(out);
    }

    /** 操作留痕：人员/价目/公告/账单等关键增减动作（最近 limit 条，倒序）+ 增减统计 */
    @GetMapping("/op-logs")
    public Result<Map<String, Object>> opLogs(@RequestParam(defaultValue = "200") Integer limit) {
        List<SysOpLog> logs = opLogMapper.selectList(
                new LambdaQueryWrapper<SysOpLog>().orderByDesc(SysOpLog::getLogId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
        Map<String, Object> out = new HashMap<>();
        out.put("total", opLogMapper.selectCount(null));
        out.put("addCount", opLogMapper.selectCount(new LambdaQueryWrapper<SysOpLog>().eq(SysOpLog::getAction, "ADD")));
        out.put("deleteCount", opLogMapper.selectCount(new LambdaQueryWrapper<SysOpLog>().eq(SysOpLog::getAction, "DELETE")));
        out.put("cancelCount", opLogMapper.selectCount(new LambdaQueryWrapper<SysOpLog>().eq(SysOpLog::getAction, "CANCEL")));
        out.put("refundCount", opLogMapper.selectCount(new LambdaQueryWrapper<SysOpLog>().eq(SysOpLog::getAction, "REFUND")));
        out.put("items", logs);
        return Result.ok(out);
    }

    /* ==================== 患者档案 ==================== */

    @GetMapping("/patients")
    public Result<List<Map<String, Object>>> patients(@RequestParam(required = false) String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Patient p : patientMapper.selectList(null)) {
            Map<String, Object> m = new HashMap<>();
            m.put("patientId", p.getPatientId());
            m.put("healthCardNo", p.getHealthCardNo());
            m.put("birthday", p.getBirthday() == null ? null : p.getBirthday().toString());
            m.put("idCard", p.getIdCard());
            m.put("emergencyPhone", p.getEmergencyPhone());
            m.put("defaultStoreId", p.getDefaultStoreId());
            User u = userMapper.selectById(p.getPatientId());
            if (u != null) {
                m.put("realName", u.getRealName());
                m.put("phone", u.getPhone());
                m.put("gender", u.getGender());
                m.put("status", u.getStatus());
            }
            if (!kw.isEmpty()) {
                String name = u == null ? "" : String.valueOf(u.getRealName());
                String phone = u == null ? "" : String.valueOf(u.getPhone());
                String card = String.valueOf(p.getHealthCardNo());
                if (!(name.contains(kw) || phone.contains(kw) || card.contains(kw))) continue;
            }
            m.put("allergyCount", allergyMapper.selectCount(new LambdaQueryWrapper<AllergyRecord>()
                    .eq(AllergyRecord::getPatientId, p.getPatientId())
                    .eq(AllergyRecord::getStatus, 1)));
            Store s = p.getDefaultStoreId() == null ? null : storeMapper.selectById(p.getDefaultStoreId());
            m.put("storeName", s == null ? "-" : s.getStoreName());
            out.add(m);
        }
        return Result.ok(out);
    }

    /* ==================== 门店管理 ==================== */

    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> stores() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Store s : storeMapper.selectList(null)) {
            Map<String, Object> m = new HashMap<>();
            m.put("storeId", s.getStoreId());
            m.put("storeCode", s.getStoreCode());
            m.put("storeName", s.getStoreName());
            m.put("address", s.getAddress());
            m.put("phone", s.getPhone());
            m.put("openDate", s.getOpenDate() == null ? null : s.getOpenDate().toString());
            m.put("status", s.getStatus());
            m.put("managerId", s.getManagerId());
            if (s.getManagerId() != null) {
                User mg = userMapper.selectById(s.getManagerId());
                m.put("managerName", mg == null ? null : mg.getRealName());
            }
            m.put("chairCount", chairMapper.selectCount(new LambdaQueryWrapper<DentalChair>()
                    .eq(DentalChair::getStoreId, s.getStoreId()).eq(DentalChair::getStatus, 1)));
            m.put("doctorCount", doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                    .eq(Doctor::getHomeStoreId, s.getStoreId())));
            out.add(m);
        }
        return Result.ok(out);
    }

    public static class StoreReq {
        public String storeCode;
        public String storeName;
        public String address;
        public String phone;
        public String openDate;
        public Integer status;
    }

    @PostMapping("/stores")
    public Result<Store> addStore(@RequestBody StoreReq req) {
        if (req.storeCode == null || req.storeName == null)
            throw new BusinessException("门店编码与名称必填");
        Long dup = storeMapper.selectCount(new LambdaQueryWrapper<Store>().eq(Store::getStoreCode, req.storeCode));
        if (dup > 0) throw new BusinessException("门店编码已存在");
        Store s = new Store();
        s.setStoreCode(req.storeCode);
        s.setStoreName(req.storeName);
        s.setAddress(req.address);
        s.setPhone(req.phone);
        s.setStatus(req.status == null ? 1 : req.status);
        if (req.openDate != null && !req.openDate.isEmpty()) s.setOpenDate(java.time.LocalDate.parse(req.openDate));
        storeMapper.insert(s);
        return Result.ok(s);
    }

    @PostMapping("/stores/{id}/update")
    public Result<String> updateStore(@PathVariable Long id, @RequestBody StoreReq req) {
        Store s = storeMapper.selectById(id);
        if (s == null) throw new BusinessException("门店不存在");
        if (req.storeName != null) s.setStoreName(req.storeName);
        if (req.address != null) s.setAddress(req.address);
        if (req.phone != null) s.setPhone(req.phone);
        if (req.status != null) s.setStatus(req.status);
        if (req.openDate != null && !req.openDate.isEmpty()) s.setOpenDate(java.time.LocalDate.parse(req.openDate));
        storeMapper.updateById(s);
        return Result.ok("已保存");
    }

    /* ==================== 门店运营成本 ==================== */

    @GetMapping("/costs")
    public Result<List<Map<String, Object>>> costs(@RequestParam(required = false) Long storeId,
                                                   @RequestParam(required = false) Integer year) {
        LambdaQueryWrapper<OperationCost> qw = new LambdaQueryWrapper<>();
        if (storeId != null) qw.eq(OperationCost::getStoreId, storeId);
        if (year != null) qw.eq(OperationCost::getYear, year);
        qw.orderByDesc(OperationCost::getYear).orderByAsc(OperationCost::getQuarter);
        List<Map<String, Object>> out = new ArrayList<>();
        for (OperationCost c : costMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("costId", c.getCostId());
            m.put("storeId", c.getStoreId());
            Store s = storeMapper.selectById(c.getStoreId());
            m.put("storeName", s == null ? "-" : s.getStoreName());
            m.put("year", c.getYear());
            m.put("quarter", c.getQuarter());
            m.put("rent", c.getRent());
            m.put("labor", c.getLabor());
            m.put("depreciation", c.getDepreciation());
            m.put("amount", c.getAmount());
            out.add(m);
        }
        return Result.ok(out);
    }

    public static class CostReq {
        public Long storeId;
        public Integer year;
        public Integer quarter;
        public BigDecimal rent;
        public BigDecimal labor;
        public BigDecimal depreciation;
    }

    @PostMapping("/costs")
    public Result<OperationCost> saveCost(@RequestBody CostReq req) {
        if (req.storeId == null || req.year == null || req.quarter == null)
            throw new BusinessException("门店、年份、季度必填");
        OperationCost c = costMapper.selectOne(new LambdaQueryWrapper<OperationCost>()
                .eq(OperationCost::getStoreId, req.storeId)
                .eq(OperationCost::getYear, req.year)
                .eq(OperationCost::getQuarter, req.quarter));
        if (c == null) {
            c = new OperationCost();
            c.setStoreId(req.storeId);
            c.setYear(req.year);
            c.setQuarter(req.quarter);
        }
        c.setRent(nz(req.rent));
        c.setLabor(nz(req.labor));
        c.setDepreciation(nz(req.depreciation));
        c.setAmount(nz(req.rent).add(nz(req.labor)).add(nz(req.depreciation)));
        if (c.getCostId() == null) costMapper.insert(c); else costMapper.updateById(c);
        return Result.ok(c);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /* ==================== 角色授权矩阵 ==================== */

    @PostMapping("/role-permissions/grant")
    @Transactional
    public Result<String> grant(@RequestParam Long roleId, @RequestParam Long permId,
                                @RequestParam(defaultValue = "true") Boolean grant) {
        if (grant) {
            Long exist = rolePermMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, roleId).eq(SysRolePermission::getPermId, permId));
            if (exist == 0) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(roleId);
                rp.setPermId(permId);
                rolePermMapper.insert(rp);
            }
        } else {
            rolePermMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, roleId).eq(SysRolePermission::getPermId, permId));
        }
        return Result.ok(grant ? "已授权" : "已收回");
    }

    /* ==================== 预约管理（门店/总部） ==================== */

    @GetMapping("/appointments")
    public Result<List<Map<String, Object>>> appointments(@RequestParam(required = false) Long storeId,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<>();
        if (storeId != null) qw.eq(Appointment::getStoreId, storeId);
        if (status != null) qw.eq(Appointment::getStatus, status);
        if (from != null && !from.isEmpty()) qw.ge(Appointment::getVisitDate, LocalDate.parse(from));
        if (to != null && !to.isEmpty()) qw.le(Appointment::getVisitDate, LocalDate.parse(to));
        qw.orderByDesc(Appointment::getVisitDate).orderByAsc(Appointment::getTimeSlot);
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Appointment a : appointmentMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("appointmentId", a.getAppointmentId());
            m.put("appointmentNo", a.getAppointmentNo());
            m.put("patientId", a.getPatientId());
            User pu = userMapper.selectById(a.getPatientId());
            m.put("patientName", pu == null ? "-" : pu.getRealName());
            m.put("doctorId", a.getDoctorId());
            User du = userMapper.selectById(a.getDoctorId());
            m.put("doctorName", du == null ? "-" : du.getRealName());
            if (!kw.isEmpty()) {
                String no = String.valueOf(a.getAppointmentNo());
                String pn = pu == null ? "" : String.valueOf(pu.getRealName());
                String dn = du == null ? "" : String.valueOf(du.getRealName());
                if (!(no.contains(kw) || pn.contains(kw) || dn.contains(kw))) continue;
            }
            m.put("storeId", a.getStoreId());
            m.put("visitDate", a.getVisitDate() == null ? null : a.getVisitDate().toString());
            m.put("timeSlot", a.getTimeSlot());
            m.put("status", a.getStatus());
            m.put("depositAmount", a.getDepositAmount());
            m.put("depositStatus", a.getDepositStatus());
            m.put("cancelReason", a.getCancelReason());
            out.add(m);
        }
        return Result.ok(out);
    }

    /* ==================== 仪表盘 ==================== */

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> m = new HashMap<>();
        m.put("stores", storeMapper.selectCount(null));
        m.put("doctors", doctorMapper.selectCount(null));
        m.put("chairs", chairMapper.selectCount(new LambdaQueryWrapper<DentalChair>().eq(DentalChair::getStatus, 1)));
        m.put("patients", patientMapper.selectCount(null));
        m.put("users", userMapper.selectCount(null));
        m.put("appointments", appointmentMapper.selectCount(null));
        return Result.ok(m);
    }

    /* ==================== 数据备份与恢复 ==================== */

    @GetMapping("/backups")
    public Result<Map<String, Object>> backups(@RequestParam Long operatorId) {
        requireAdmin(operatorId);
        Map<String, Object> m = new HashMap<>();
        m.put("dataDir", backupService.getDataDir().toString());
        m.put("backupDir", backupService.getBackupDir().toString());
        m.put("items", backupService.listBackups());
        return Result.ok(m);
    }

    @PostMapping("/backups")
    public Result<Map<String, Object>> createBackup(@RequestParam Long operatorId) {
        requireAdmin(operatorId);
        try {
            return Result.ok(backupService.createBackup());
        } catch (Exception e) {
            throw new BusinessException("备份失败：" + e.getMessage());
        }
    }

    @GetMapping("/backups/download")
    public ResponseEntity<Resource> downloadBackup(@RequestParam Long operatorId,
                                                   @RequestParam String name) {
        requireAdmin(operatorId);
        Path p = backupService.resolve(name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(p));
    }

    @PostMapping("/backups/restore")
    public Result<Map<String, Object>> restoreBackup(@RequestParam Long operatorId,
                                                     @RequestParam String name) {
        requireAdmin(operatorId);
        try {
            return Result.ok(backupService.stageRestore(name));
        } catch (Exception e) {
            throw new BusinessException("恢复失败：" + e.getMessage());
        }
    }

    /* ==================== 数据导出（CSV，Excel 可直接打开） ==================== */

    @GetMapping("/export/patients")
    public ResponseEntity<byte[]> exportPatients(@RequestParam Long operatorId) {
        requireAdmin(operatorId);
        List<List<Object>> rows = new ArrayList<>();
        for (Patient p : patientMapper.selectList(null)) {
            User u = userMapper.selectById(p.getPatientId());
            Store s = p.getDefaultStoreId() == null ? null : storeMapper.selectById(p.getDefaultStoreId());
            long allergy = allergyMapper.selectCount(new LambdaQueryWrapper<AllergyRecord>()
                    .eq(AllergyRecord::getPatientId, p.getPatientId()).eq(AllergyRecord::getStatus, 1));
            rows.add(List.of(
                    nvl(u == null ? null : u.getRealName()),
                    u == null || u.getGender() == null ? "-" : (u.getGender() == 1 ? "男" : "女"),
                    nvl(u == null ? null : u.getPhone()),
                    nvl(p.getHealthCardNo()),
                    nvl(p.getBirthday()),
                    nvl(s == null ? null : s.getStoreName()),
                    allergy + " 条"));
        }
        return csv("患者档案", List.of("姓名", "性别", "手机", "健康卡号", "出生日期", "常用门店", "过敏记录"), rows);
    }

    @GetMapping("/export/appointments")
    public ResponseEntity<byte[]> exportAppointments(@RequestParam Long operatorId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        requireAdmin(operatorId);
        LambdaQueryWrapper<Appointment> qw = new LambdaQueryWrapper<>();
        if (from != null && !from.isEmpty()) qw.ge(Appointment::getVisitDate, LocalDate.parse(from));
        if (to != null && !to.isEmpty()) qw.le(Appointment::getVisitDate, LocalDate.parse(to));
        qw.orderByDesc(Appointment::getVisitDate);
        String[] statusText = {"已提交", "已确认", "已到诊", "诊疗中", "已完成", "已取消", "爽约"};
        List<List<Object>> rows = new ArrayList<>();
        for (Appointment a : appointmentMapper.selectList(qw)) {
            User pu = userMapper.selectById(a.getPatientId());
            User du = userMapper.selectById(a.getDoctorId());
            Store s = a.getStoreId() == null ? null : storeMapper.selectById(a.getStoreId());
            rows.add(List.of(
                    nvl(a.getAppointmentNo()),
                    nvl(pu == null ? null : pu.getRealName()),
                    nvl(du == null ? null : du.getRealName()),
                    nvl(s == null ? null : s.getStoreName()),
                    nvl(a.getVisitDate()),
                    a.getTimeSlot() == null ? "-" : String.valueOf(a.getTimeSlot()),
                    a.getStatus() == null || a.getStatus() > 6 ? "-" : statusText[a.getStatus()],
                    a.getDepositAmount() == null ? "0" : a.getDepositAmount().toPlainString()));
        }
        return csv("预约记录", List.of("预约号", "患者", "医生", "门店", "就诊日期", "时段", "状态", "预付金额"), rows);
    }

    @GetMapping("/export/bills")
    public ResponseEntity<byte[]> exportBills(@RequestParam Long operatorId,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        requireAdmin(operatorId);
        String[] statusText = {"待支付", "已支付", "已开票", "部分退款", "全额退款"};
        List<List<Object>> rows = new ArrayList<>();
        for (Bill b : billMapper.selectList(null)) {
            if (from != null && !from.isEmpty() && b.getPayTime() != null
                    && b.getPayTime().toLocalDate().isBefore(LocalDate.parse(from))) continue;
            if (to != null && !to.isEmpty() && b.getPayTime() != null
                    && b.getPayTime().toLocalDate().isAfter(LocalDate.parse(to))) continue;
            User pu = b.getPatientId() == null ? null : userMapper.selectById(b.getPatientId());
            Store s = b.getStoreId() == null ? null : storeMapper.selectById(b.getStoreId());
            rows.add(List.of(
                    nvl(b.getBillNo()),
                    nvl(pu == null ? null : pu.getRealName()),
                    nvl(s == null ? null : s.getStoreName()),
                    b.getTotalAmount() == null ? "0" : b.getTotalAmount().toPlainString(),
                    b.getDiscount() == null ? "0" : b.getDiscount().toPlainString(),
                    b.getPayable() == null ? "0" : b.getPayable().toPlainString(),
                    nvl(b.getPayChannel()),
                    b.getStatus() == null || b.getStatus() > 4 ? "-" : statusText[b.getStatus()],
                    b.getPayTime() == null ? "" : b.getPayTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        }
        return csv("收费单据", List.of("单号", "患者", "门店", "应收", "优惠", "实收", "支付渠道", "状态", "支付时间"), rows);
    }

    private ResponseEntity<byte[]> csv(String name, List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(",", header.stream().map(this::cell).toList())).append("\r\n");
        for (List<Object> r : rows) {
            sb.append(String.join(",", r.stream().map(this::cell).toList())).append("\r\n");
        }
        String fileName = name + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        String encoded = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String cell(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String nvl(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private List<String> roleCodesOf(Long userId) {
        List<String> codes = new ArrayList<>();
        for (SysUserRole ur : userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId))) {
            SysRole r = roleMapper.selectById(ur.getRoleId());
            if (r != null) codes.add(r.getRoleCode());
        }
        return codes;
    }
}
