package com.dental.clinic.controller;

import com.dental.clinic.common.BusinessException;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Doctor;
import com.dental.clinic.entity.Patient;
import com.dental.clinic.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 认证：患者注册 / 登录 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Data
    public static class RegisterReq {
        private String account;
        private String password;
        private String realName;
        private String phone;
        private String healthCardNo;
    }

    @PostMapping("/register")
    public Result<Patient> register(@RequestBody RegisterReq req) {
        return Result.ok(authService.registerPatient(req.getAccount(), req.getPassword(),
                req.getRealName(), req.getPhone(), req.getHealthCardNo()));
    }

    @Data
    public static class LoginReq {
        private String account;
        private String password;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginReq req) {
        return Result.ok(authService.login(req.getAccount(), req.getPassword()));
    }

    /** 牙医自助注册：官网「注册」入口，工号自动生成，初始状态即可用账号登录牙医端 */
    @Data
    public static class DoctorRegReq {
        private String account;
        private String password;
        private String realName;
        private String phone;
        private String title;       // 主任医师/副主任医师/主治医师/住院医师
        private String specialty;   // 执业范围/特长
        private String practiceNo;  // 执业证号（选填）
        private Long homeStoreId;   // 归属门店（选填）
    }

    @PostMapping("/register/doctor")
    public Result<Doctor> registerDoctor(@RequestBody DoctorRegReq req) {
        if (req.getAccount() == null || req.getAccount().isBlank()) throw new BusinessException("登录账号必填");
        if (req.getPassword() == null || req.getPassword().length() < 6) throw new BusinessException("密码至少 6 位");
        if (req.getRealName() == null || req.getRealName().isBlank()) throw new BusinessException("姓名必填");
        return Result.ok(authService.registerDoctor(req.getAccount().trim(), req.getPassword(),
                req.getRealName().trim(), req.getPhone(), req.getTitle(), req.getSpecialty(),
                req.getPracticeNo(), req.getHomeStoreId()));
    }

    /** 账户注销（患者/牙医自助）：密码二次确认，个人信息匿名化处理并留痕 */
    @PostMapping("/cancel")
    public Result<Map<String, Object>> cancel(@RequestBody LoginReq req) {
        return Result.ok(authService.cancelAccount(req.getAccount(), req.getPassword()));
    }
}
