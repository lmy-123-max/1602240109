package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.Doctor;
import com.dental.clinic.entity.Patient;
import com.dental.clinic.entity.SysLoginLog;
import com.dental.clinic.entity.User;
import com.dental.clinic.mapper.DoctorMapper;
import com.dental.clinic.mapper.PatientMapper;
import com.dental.clinic.mapper.SysLoginLogMapper;
import com.dental.clinic.entity.SysPermission;
import com.dental.clinic.entity.SysRole;
import com.dental.clinic.entity.SysRolePermission;
import com.dental.clinic.entity.SysUserRole;
import com.dental.clinic.mapper.SysPermissionMapper;
import com.dental.clinic.mapper.SysRoleMapper;
import com.dental.clinic.mapper.SysRolePermissionMapper;
import com.dental.clinic.mapper.SysUserRoleMapper;
import com.dental.clinic.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务：患者注册建档 + 全角色统一登录
 * RBAC：登录时从 sys_user_role / sys_role / sys_role_permission / sys_permission 加载角色与权限
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final com.dental.clinic.service.OpLogService opLogService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermMapper;
    private final SysPermissionMapper permMapper;

    /** 患者注册：sys_user 建档 + patient 子表建档（混合建表方案） */
    @Transactional
    public Patient registerPatient(String account, String password, String realName,
                                   String phone, String healthCardNo) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (exists > 0) {
            throw new BusinessException("该账号已被注册");
        }
        User user = new User();
        user.setAccount(account);
        user.setPassword(md5(password));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        Patient patient = new Patient();
        patient.setPatientId(user.getUserId());
        patient.setHealthCardNo(healthCardNo == null || healthCardNo.isBlank()
                ? "HC" + String.format("%010d", user.getUserId()) : healthCardNo);
        patientMapper.insert(patient);
        return patient;
    }

    /** 牙医自助注册：sys_user 建档 + doctor 子表建档，工号自动生成（官网「注册」入口） */
    @Transactional
    public Doctor registerDoctor(String account, String password, String realName, String phone,
                                 String title, String specialty, String practiceNo, Long homeStoreId) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (exists > 0) {
            throw new BusinessException("该账号已被注册");
        }
        User user = new User();
        user.setAccount(account);
        user.setPassword(md5(password));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        Doctor d = new Doctor();
        d.setDoctorId(user.getUserId());
        d.setJobNo(nextJobNo());
        d.setTitle(title == null || title.isBlank() ? "主治医师" : title);
        d.setSpecialty(specialty == null || specialty.isBlank() ? "全科" : specialty);
        d.setPracticeNo(practiceNo);
        d.setHomeStoreId(homeStoreId);
        d.setIncentiveRate(new java.math.BigDecimal("0.1000"));
        doctorMapper.insert(d);
        return d;
    }

    /**
     * 账户注销（患者/牙医自助）：
     * 依据《个人信息保护法》最小必要原则做匿名化处理——姓名、手机、证件、过敏等个人敏感信息清除；
     * 病历与账单依《医疗机构病历管理规定》须留存，但已与本人身份解绑（仅保留匿名档案号），
     * 账号置为已注销状态不可再登录。每次注销写入操作留痕。
     */
    @Transactional
    public Map<String, Object> cancelAccount(String account, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (user == null || !user.getPassword().equals(md5(password))) {
            throw new BusinessException("账号或密码错误，注销失败");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号当前状态不可注销");
        }
        boolean isPatient = patientMapper.selectById(user.getUserId()) != null;
        boolean isDoctor = doctorMapper.selectById(user.getUserId()) != null;
        if (!isPatient && !isDoctor) {
            throw new BusinessException("工作人员账号请联系上级管理员办理离职/停用，不可自助注销");
        }
        // 1) 匿名化个人身份信息（个保法：最小必要、目的限定）
        String anonName = (isPatient ? "已注销患者" : "已注销牙医") + "#" + user.getUserId();
        user.setRealName(anonName);
        user.setPhone(null);
        user.setStatus(0);
        userMapper.updateById(user);
        if (isPatient) {
            Patient p = patientMapper.selectById(user.getUserId());
            p.setIdCard(null);
            p.setEmergencyPhone(null);
            patientMapper.updateById(p);
        }
        // 2) 注销记录写入操作留痕（谁在何时注销了账户）
        opLogService.record(user.getUserId(), anonName, "CANCEL", "账户",
                account + "（" + (isPatient ? "患者" : "牙医") + "）",
                "患者/牙医自助注销：个人身份信息已匿名化，病历与账单依规匿名留存，账号不可再登录");
        Map<String, Object> out = new HashMap<>();
        out.put("cancelled", true);
        out.put("account", account);
        return out;
    }

    /** 牙医工号自动生成：现有 D#### 最大号 +1 */    private String nextJobNo() {
        int max = 1000;
        for (Doctor d : doctorMapper.selectList(null)) {
            String jn = d.getJobNo();
            if (jn != null && jn.matches("D\\d{4}")) {
                try {
                    max = Math.max(max, Integer.parseInt(jn.substring(1)));
                } catch (NumberFormatException ignore) { }
            }
        }
        return "D" + String.format("%04d", max + 1);
    }

    /** 统一登录：校验账号密码，推导角色；每次进入（成功/失败）均写入登录日志 */
    public Map<String, Object> login(String account, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (user == null || !user.getPassword().equals(md5(password))) {
            recordLogin(user, null, 0, user == null ? "账号不存在" : "密码错误");
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != 1) {
            recordLogin(user, null, 0, "账号已禁用");
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        Map<String, Object> info = new HashMap<>();
        user.setPassword(null);
        info.put("user", user);

        // RBAC：加载该用户的角色与权限点
        List<String> roleCodes = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        List<String> permNames = new ArrayList<>();
        List<SysUserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getUserId()));
        for (SysUserRole ur : urs) {
            SysRole r = roleMapper.selectById(ur.getRoleId());
            if (r == null || r.getStatus() == null || r.getStatus() != 1) {
                continue;
            }
            roleCodes.add(r.getRoleCode());
            roleNames.add(r.getRoleName());
            for (SysRolePermission rp : rolePermMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, r.getRoleId()))) {
                SysPermission perm = permMapper.selectById(rp.getPermId());
                if (perm != null && !permNames.contains(perm.getPermName())) {
                    permNames.add(perm.getPermName());
                }
            }
        }
        if (!roleCodes.isEmpty()) {
            info.put("roleCodes", roleCodes);
            info.put("roleNames", roleNames);
            info.put("permissions", permNames);
        }

        // 角色推导：patient / doctor 子表命中则返回对应身份，否则视为管理员
        Patient p = patientMapper.selectById(user.getUserId());
        if (p != null) {
            info.put("role", "patient");
            info.put("roleName", "患者");
            info.put("roleId", p.getPatientId());
            recordLogin(user, "患者", 1, null);
            return info;
        }
        Doctor d = doctorMapper.selectById(user.getUserId());
        if (d != null) {
            info.put("role", "doctor");
            info.put("roleName", "牙医");
            info.put("roleId", d.getDoctorId());
            info.put("doctor", d);
            recordLogin(user, "牙医", 1, null);
            return info;
        }
        // 管理员账号约定：store 开头为门店管理员，其余为总部管理员
        if (account.startsWith("store")) {
            info.put("role", "storeAdmin");
            info.put("roleName", "门店管理员");
        } else {
            info.put("role", "hqAdmin");
            info.put("roleName", "总部管理员");
        }
        info.put("roleId", user.getUserId());
        recordLogin(user, (String) info.get("roleName"), 1, null);
        return info;
    }

    /** 写入登录留痕：谁、何时、从哪个 IP、成功还是失败 */
    private void recordLogin(User user, String roleName, Integer success, String failReason) {
        try {
            SysLoginLog log = new SysLoginLog();
            if (user != null) {
                log.setUserId(user.getUserId());
                log.setAccount(user.getAccount());
                log.setRealName(user.getRealName());
            }
            log.setRoleName(roleName);
            log.setSuccess(success);
            log.setFailReason(failReason);
            log.setLoginTime(LocalDateTime.now());
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
                log.setIp(ip);
                String ua = req.getHeader("User-Agent");
                log.setUserAgent(ua == null ? null : ua.substring(0, Math.min(ua.length(), 290)));
            }
            loginLogMapper.insert(log);
        } catch (Exception ignore) {
            // 日志写入失败不影响登录主流程
        }
    }

    /** 演示环境简化口令摘要（MD5），生产环境应替换为 BCrypt */
    public static String md5(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(("dental#" + raw).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
