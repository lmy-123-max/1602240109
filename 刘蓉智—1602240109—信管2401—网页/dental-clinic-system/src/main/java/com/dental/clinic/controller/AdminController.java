package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.*;
import com.dental.clinic.mapper.*;
import com.dental.clinic.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端基础数据中心（参照 DentalPin Admin / OpenDental Setup）：
 * 牙医管理、牙椅管理、治疗项目价目、供应商、工作日历、资质审核、角色权限
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DoctorMapper doctorMapper;
    private final UserMapper userMapper;
    private final DentalChairMapper chairMapper;
    private final TreatmentItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final WorkCalendarMapper calendarMapper;
    private final DoctorQualificationMapper qualificationMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final PurchaseRecordMapper purchaseMapper;
    private final BillMapper billMapper;
    private final AuthService authService;
    private final com.dental.clinic.service.OpLogService opLogService;

    /* ==================== 牙医管理 ==================== */

    @GetMapping("/doctors")
    public Result<List<Map<String, Object>>> doctors() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Doctor d : doctorMapper.selectList(null)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("doctorId", d.getDoctorId());
            User u = userMapper.selectById(d.getDoctorId());
            m.put("doctorName", u == null ? d.getJobNo() : u.getRealName());
            m.put("phone", u == null ? null : u.getPhone());
            m.put("account", u == null ? null : u.getAccount());
            m.put("jobNo", d.getJobNo());
            m.put("title", d.getTitle());
            m.put("specialty", d.getSpecialty());
            m.put("practiceNo", d.getPracticeNo());
            m.put("homeStoreId", d.getHomeStoreId());
            m.put("incentiveRate", d.getIncentiveRate());
            out.add(m);
        }
        return Result.ok(out);
    }

    @Data
    public static class DoctorReq {
        private String realName;
        private String phone;
        private String jobNo;
        private String title;
        private String specialty;
        private String practiceNo;
        private Long homeStoreId;
    }

    /** 新增牙医：同步创建 sys_user 账号（初始密码 123456） */
    @PostMapping("/doctors")
    public Result<Doctor> addDoctor(@RequestBody DoctorReq req) {
        if (req.getRealName() == null || req.getJobNo() == null) {
            throw new BusinessException("姓名与工号必填");
        }
        User user = new User();
        user.setAccount(req.getJobNo());
        user.setPassword(AuthService.md5("123456"));
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        user.setCreateTime(java.time.LocalDateTime.now());
        userMapper.insert(user);

        Doctor d = new Doctor();
        d.setDoctorId(user.getUserId());
        d.setJobNo(req.getJobNo());
        d.setTitle(req.getTitle());
        d.setSpecialty(req.getSpecialty());
        d.setPracticeNo(req.getPracticeNo());
        d.setHomeStoreId(req.getHomeStoreId());
        d.setIncentiveRate(new java.math.BigDecimal("0.1000"));
        doctorMapper.insert(d);
        opLogService.record(null, "管理员", "ADD", "医生",
                req.getRealName() + "（" + req.getJobNo() + "）", "新增牙医执业档案与登录账号，初始密码 123456");
        return Result.ok(d);
    }

    /** 修改牙医 */
    @PostMapping("/doctors/{id}/update")
    public Result<String> updateDoctor(@PathVariable Long id, @RequestBody DoctorReq req) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) {
            return Result.fail("牙医不存在");
        }
        if (req.getJobNo() != null) d.setJobNo(req.getJobNo());
        if (req.getTitle() != null) d.setTitle(req.getTitle());
        if (req.getSpecialty() != null) d.setSpecialty(req.getSpecialty());
        if (req.getPracticeNo() != null) d.setPracticeNo(req.getPracticeNo());
        if (req.getHomeStoreId() != null) d.setHomeStoreId(req.getHomeStoreId());
        doctorMapper.updateById(d);
        User u = userMapper.selectById(id);
        if (u != null) {
            if (req.getRealName() != null) u.setRealName(req.getRealName());
            if (req.getPhone() != null) u.setPhone(req.getPhone());
            userMapper.updateById(u);
        }
        return Result.ok("已更新");
    }

    /** 删除牙医（连同账号与角色关联） */
    @PostMapping("/doctors/{id}/delete")
    public Result<String> deleteDoctor(@PathVariable Long id) {
        Doctor d = doctorMapper.selectById(id);
        User u = userMapper.selectById(id);
        doctorMapper.deleteById(id);
        userMapper.deleteById(id);
        opLogService.record(null, "管理员", "DELETE", "医生",
                (u == null ? String.valueOf(id) : u.getRealName()) + "（" + (d == null ? "-" : d.getJobNo()) + "）",
                "删除牙医执业档案与登录账号");
        return Result.ok("已删除");
    }

    /* ==================== 牙椅管理 ==================== */

    @GetMapping("/chairs")
    public Result<List<DentalChair>> chairs() {
        return Result.ok(chairMapper.selectList(new LambdaQueryWrapper<DentalChair>()
                .orderByAsc(DentalChair::getStoreId).orderByAsc(DentalChair::getChairNo)));
    }

    @PostMapping("/chairs")
    public Result<DentalChair> addChair(@RequestBody DentalChair chair) {
        chair.setChairId(null);
        chairMapper.insert(chair);
        opLogService.record(null, "管理员", "ADD", "牙椅",
                chair.getChairNo() == null ? "牙椅#" : chair.getChairNo(), "新增牙椅资源，门店 #" + chair.getStoreId());
        return Result.ok(chair);
    }

    @PostMapping("/chairs/{id}/update")
    public Result<String> updateChair(@PathVariable Long id, @RequestBody DentalChair chair) {
        chair.setChairId(id);
        chairMapper.updateById(chair);
        opLogService.record(null, "管理员", "UPDATE", "牙椅",
                chair.getChairNo() == null ? "牙椅#" + id : chair.getChairNo(), "牙椅信息变更");
        return Result.ok("已更新");
    }

    @PostMapping("/chairs/{id}/delete")
    public Result<String> deleteChair(@PathVariable Long id) {
        DentalChair old = chairMapper.selectById(id);
        chairMapper.deleteById(id);
        opLogService.record(null, "管理员", "DELETE", "牙椅",
                old == null ? "牙椅#" + id : old.getChairNo(), "删除牙椅资源");
        return Result.ok("已删除");
    }

    /* ==================== 治疗项目价目管理 ==================== */

    @GetMapping("/items")
    public Result<List<TreatmentItem>> items() {
        return Result.ok(itemMapper.selectList(null));
    }

    @PostMapping("/items")
    public Result<TreatmentItem> addItem(@RequestBody TreatmentItem item) {
        item.setItemId(null);
        if (item.getStatus() == null) item.setStatus(1);
        itemMapper.insert(item);
        opLogService.record(null, "管理员", "ADD", "价目",
                item.getItemName() + "（" + item.getItemCode() + "）",
                "新增诊疗项目：定价 ¥" + item.getPrice() + "，医保属性 " + (item.getInsuranceType() == null ? "自费" : item.getInsuranceType()));
        return Result.ok(item);
    }

    @PostMapping("/items/{id}/update")
    public Result<String> updateItem(@PathVariable Long id, @RequestBody TreatmentItem item) {
        TreatmentItem old = itemMapper.selectById(id);
        item.setItemId(id);
        itemMapper.updateById(item);
        boolean off = old != null && old.getStatus() == 1 && item.getStatus() != null && item.getStatus() == 0;
        opLogService.record(null, "管理员", off ? "OFF" : "UPDATE", "价目",
                (old == null ? "项目#" + id : old.getItemName()),
                off ? "价目项目停用（不再可开单，历史账单不受影响）" : "价目项目信息变更");
        return Result.ok("已更新");
    }

    @PostMapping("/items/{id}/delete")
    public Result<String> deleteItem(@PathVariable Long id) {
        TreatmentItem old = itemMapper.selectById(id);
        itemMapper.deleteById(id);
        opLogService.record(null, "管理员", "DELETE", "价目",
                old == null ? "项目#" + id : old.getItemName() + "（" + old.getItemCode() + "）", "删除价目项目");
        return Result.ok("已删除");
    }

    /* ==================== 供应商管理 ==================== */

    @GetMapping("/suppliers")
    public Result<List<Supplier>> suppliers() {
        return Result.ok(supplierMapper.selectList(null));
    }

    @PostMapping("/suppliers")
    public Result<Supplier> addSupplier(@RequestBody Supplier s) {
        s.setSupplierId(null);
        if (s.getStatus() == null) s.setStatus(1);
        supplierMapper.insert(s);
        opLogService.record(null, "管理员", "ADD", "供应商",
                s.getSupplierName(), "新增供应商，供货范围：" + (s.getSupplyScope() == null ? "-" : s.getSupplyScope()));
        return Result.ok(s);
    }

    @PostMapping("/suppliers/{id}/update")
    public Result<String> updateSupplier(@PathVariable Long id, @RequestBody Supplier s) {
        s.setSupplierId(id);
        supplierMapper.updateById(s);
        opLogService.record(null, "管理员", "UPDATE", "供应商",
                s.getSupplierName() == null ? "供应商#" + id : s.getSupplierName(), "供应商信息变更");
        return Result.ok("已更新");
    }

    @PostMapping("/suppliers/{id}/delete")
    public Result<String> deleteSupplier(@PathVariable Long id) {
        Supplier old = supplierMapper.selectById(id);
        supplierMapper.deleteById(id);
        opLogService.record(null, "管理员", "DELETE", "供应商",
                old == null ? "供应商#" + id : old.getSupplierName(), "删除供应商档案");
        return Result.ok("已删除");
    }

    /* ==================== 工作日历管理 ==================== */

    @GetMapping("/calendar")
    public Result<List<WorkCalendar>> calendar(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<WorkCalendar> qw = new LambdaQueryWrapper<WorkCalendar>()
                .orderByAsc(WorkCalendar::getCalDate);
        if (storeId != null) {
            qw.eq(WorkCalendar::getStoreId, storeId);
        }
        return Result.ok(calendarMapper.selectList(qw));
    }

    /** 设置某日类型（1工作日 2法定节假日 3调休补班） */
    @PostMapping("/calendar/set")
    public Result<WorkCalendar> setCalendar(@RequestBody WorkCalendar cal) {
        WorkCalendar old = calendarMapper.selectOne(new LambdaQueryWrapper<WorkCalendar>()
                .eq(WorkCalendar::getStoreId, cal.getStoreId())
                .eq(WorkCalendar::getCalDate, cal.getCalDate()));
        if (old != null) {
            cal.setCalendarId(old.getCalendarId());
            calendarMapper.updateById(cal);
        } else {
            cal.setCalendarId(null);
            calendarMapper.insert(cal);
        }
        return Result.ok(cal);
    }

    /* ==================== 资质审核 ==================== */

    @GetMapping("/qualifications")
    public Result<List<Map<String, Object>>> qualifications() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DoctorQualification q : qualificationMapper.selectList(
                new LambdaQueryWrapper<DoctorQualification>().orderByAsc(DoctorQualification::getAuditStatus))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("qualificationId", q.getQualificationId());
            m.put("doctorId", q.getDoctorId());
            Doctor d = doctorMapper.selectById(q.getDoctorId());
            User u = d == null ? null : userMapper.selectById(d.getDoctorId());
            m.put("doctorName", u == null ? ("牙医" + q.getDoctorId()) : u.getRealName());
            m.put("qualName", q.getQualName());
            m.put("certNo", q.getCertNo());
            m.put("issueOrg", q.getIssueOrg());
            m.put("expireDate", q.getExpireDate());
            m.put("auditStatus", q.getAuditStatus());
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 资质审核：auditStatus 0待初审 1初审通过 2终审通过 3已驳回 */
    @PostMapping("/qualifications/{id}/audit")
    public Result<String> audit(@PathVariable Long id, @RequestParam Integer auditStatus,
                                @RequestParam(required = false) Long auditorId) {
        DoctorQualification q = qualificationMapper.selectById(id);
        if (q == null) {
            return Result.fail("资质记录不存在");
        }
        q.setAuditStatus(auditStatus);
        q.setAuditorId(auditorId);
        qualificationMapper.updateById(q);
        return Result.ok("审核状态已更新为 " + auditStatus);
    }

    /* ==================== 耗材库存管理（采购入库，论文 3.7.2 门店管理员用例） ==================== */

    @GetMapping("/purchases")
    public Result<List<Map<String, Object>>> purchases(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<PurchaseRecord> qw = new LambdaQueryWrapper<PurchaseRecord>()
                .orderByDesc(PurchaseRecord::getPurchaseDate);
        if (storeId != null) {
            qw.eq(PurchaseRecord::getStoreId, storeId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseRecord p : purchaseMapper.selectList(qw)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purchaseId", p.getPurchaseId());
            m.put("storeId", p.getStoreId());
            Supplier s = p.getSupplierId() == null ? null : supplierMapper.selectById(p.getSupplierId());
            m.put("supplierName", s == null ? "-" : s.getSupplierName());
            m.put("itemType", p.getItemType());
            m.put("itemName", p.getItemName());
            m.put("quantity", p.getQuantity());
            m.put("unitPrice", p.getUnitPrice());
            m.put("totalAmount", p.getTotalAmount());
            m.put("purchaseDate", p.getPurchaseDate());
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 采购入库：自动计算总金额 */
    @PostMapping("/purchases")
    public Result<PurchaseRecord> addPurchase(@RequestBody PurchaseRecord p) {
        if (p.getItemName() == null || p.getQuantity() == null || p.getUnitPrice() == null) {
            throw new BusinessException("品名、数量、进价必填");
        }
        p.setPurchaseId(null);
        if (p.getPurchaseDate() == null) p.setPurchaseDate(LocalDate.now());
        p.setTotalAmount(p.getUnitPrice().multiply(new java.math.BigDecimal(p.getQuantity())));
        purchaseMapper.insert(p);
        return Result.ok(p);
    }

    /* ==================== 门店账单核查与电子发票（论文 3.7.2） ==================== */

    @GetMapping("/bills")
    public Result<List<Map<String, Object>>> bills(@RequestParam(required = false) Long storeId) {
        LambdaQueryWrapper<Bill> qw = new LambdaQueryWrapper<Bill>()
                .orderByDesc(Bill::getBillId);
        if (storeId != null) {
            qw.eq(Bill::getStoreId, storeId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bill b : billMapper.selectList(qw)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billId", b.getBillId());
            m.put("billNo", b.getBillNo());
            User u = b.getPatientId() == null ? null : userMapper.selectById(b.getPatientId());
            m.put("patientName", u == null ? ("患者" + b.getPatientId()) : u.getRealName());
            m.put("storeId", b.getStoreId());
            m.put("totalAmount", b.getTotalAmount());
            m.put("discount", b.getDiscount());
            m.put("payable", b.getPayable());
            m.put("payChannel", b.getPayChannel());
            m.put("status", b.getStatus());
            m.put("payTime", b.getPayTime());
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 电子发票开具：仅已支付(1)账单可开票，开票后状态置 2 */
    @PostMapping("/bills/{id}/invoice")
    public Result<String> invoice(@PathVariable Long id) {
        Bill b = billMapper.selectById(id);
        if (b == null) {
            return Result.fail("账单不存在");
        }
        if (b.getStatus() == null || b.getStatus() != 1) {
            return Result.fail("仅已支付账单可开具电子发票（当前状态码 " + b.getStatus() + "）");
        }
        b.setStatus(2);
        billMapper.updateById(b);
        return Result.ok("电子发票已开具（账单 " + b.getBillNo() + "）");
    }

    /* ==================== 角色权限（RBAC 查看端） ==================== */

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysRole r : roleMapper.selectList(null)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleId", r.getRoleId());
            m.put("roleName", r.getRoleName());
            m.put("roleCode", r.getRoleCode());
            m.put("userCount", userRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, r.getRoleId())));
            out.add(m);
        }
        return Result.ok(out);
    }

    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions() {
        return Result.ok(permMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getPermId)));
    }

    @GetMapping("/role-permissions")
    public Result<List<Map<String, Object>>> rolePermissions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysRole r : roleMapper.selectList(null)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleId", r.getRoleId());
            m.put("roleName", r.getRoleName());
            List<String> perms = new ArrayList<>();
            for (com.dental.clinic.entity.SysRolePermission rp :
                    rolePermMapper.selectList(new LambdaQueryWrapper<com.dental.clinic.entity.SysRolePermission>()
                            .eq(com.dental.clinic.entity.SysRolePermission::getRoleId, r.getRoleId()))) {
                SysPermission p = permMapper.selectById(rp.getPermId());
                if (p != null) perms.add(p.getPermName());
            }
            m.put("permNames", perms);
            out.add(m);
        }
        return Result.ok(out);
    }
}
