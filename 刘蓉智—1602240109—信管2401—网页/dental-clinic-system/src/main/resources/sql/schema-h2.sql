-- =====================================================================
-- 牙科门店管理系统 H2 演示库（MODE=MySQL）
-- 与交付物 schema.sql 完全一致，
-- 与论文 4.6 节一致，共 26 张表，开箱即用演示。
-- =====================================================================

-- 1. 用户信息表
CREATE TABLE IF NOT EXISTS sys_user (
    user_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    account     VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(50)  NOT NULL,
    gender      TINYINT      DEFAULT 1,
    phone       VARCHAR(20),
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_account UNIQUE (account)
);

-- 2. 患者信息表（patient_id 同步 sys_user.user_id）
CREATE TABLE IF NOT EXISTS patient (
    patient_id       BIGINT PRIMARY KEY,
    health_card_no   VARCHAR(30) NOT NULL,
    default_store_id BIGINT,
    birthday         DATE,
    id_card          VARCHAR(20),
    emergency_phone  VARCHAR(20),
    CONSTRAINT uk_patient_card UNIQUE (health_card_no)
);

-- 3. 牙医信息表（doctor_id 同步 sys_user.user_id）
CREATE TABLE IF NOT EXISTS doctor (
    doctor_id      BIGINT PRIMARY KEY,
    job_no         VARCHAR(20) NOT NULL,
    title          VARCHAR(30),
    specialty      VARCHAR(100),
    practice_no    VARCHAR(50),
    home_store_id  BIGINT,
    incentive_rate DECIMAL(5,4) DEFAULT 0.1000,
    CONSTRAINT uk_doctor_jobno UNIQUE (job_no)
);

-- 4. 门店表
CREATE TABLE IF NOT EXISTS store (
    store_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_code VARCHAR(20) NOT NULL,
    store_name VARCHAR(50) NOT NULL,
    address    VARCHAR(200),
    phone      VARCHAR(20),
    manager_id BIGINT,
    open_date  DATE,
    status     TINYINT DEFAULT 1
);

-- 5. 牙椅表（稀缺诊疗资源：折旧 + 单次分摊成本）
CREATE TABLE IF NOT EXISTS dental_chair (
    chair_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id       BIGINT NOT NULL,
    chair_no       VARCHAR(20) NOT NULL,
    device_name    VARCHAR(100),
    purchase_price DECIMAL(12,2),
    purchase_date  DATE,
    deprec_years   INT,
    monthly_deprec DECIMAL(10,2),
    per_use_cost   DECIMAL(10,2),
    supplier_id    BIGINT,
    status         TINYINT DEFAULT 1
);

-- 6. 工作日历表（法定节假日/调休/营业时间）
CREATE TABLE IF NOT EXISTS work_calendar (
    calendar_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    BIGINT NOT NULL,
    cal_date    DATE   NOT NULL,
    day_type    TINYINT DEFAULT 1,
    open_time   TIME,
    close_time  TIME,
    remark      VARCHAR(200)
);

-- 7. 排班表（号源容量不超过可用牙椅数）
CREATE TABLE IF NOT EXISTS schedule (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id   BIGINT NOT NULL,
    store_id    BIGINT NOT NULL,
    work_date   DATE   NOT NULL,
    time_slot   TINYINT NOT NULL,
    chair_id    BIGINT,
    quota_total INT    DEFAULT 0,
    quota_used  INT    DEFAULT 0,
    status      TINYINT DEFAULT 0
);

-- 8. 供应商表
CREATE TABLE IF NOT EXISTS supplier (
    supplier_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_name  VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50),
    phone          VARCHAR(20),
    address        VARCHAR(200),
    supply_scope   VARCHAR(100),
    settle_cycle   VARCHAR(30),
    status         TINYINT DEFAULT 1
);

-- 9. 采购记录表（耗材/设备进价，支撑耗材成本核算）
CREATE TABLE IF NOT EXISTS purchase_record (
    purchase_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id     BIGINT NOT NULL,
    supplier_id  BIGINT NOT NULL,
    item_type    TINYINT DEFAULT 1,
    item_name    VARCHAR(100),
    quantity     INT    DEFAULT 1,
    unit_price   DECIMAL(10,2),
    total_amount DECIMAL(12,2),
    purchase_date DATE,
    operator_id  BIGINT
);

-- 10. 患者过敏记录表（过敏史独立实体，1 名患者 0..n 条）
CREATE TABLE IF NOT EXISTS allergy_record (
    allergy_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id    BIGINT NOT NULL,
    allergen_name VARCHAR(50) NOT NULL,
    allergy_type  TINYINT DEFAULT 1,
    severity      TINYINT DEFAULT 1,
    reaction_desc VARCHAR(200),
    source        TINYINT DEFAULT 1,
    record_date   DATE,
    status        TINYINT DEFAULT 1
);

-- 11. 诊疗项目价目表
CREATE TABLE IF NOT EXISTS treatment_item (
    item_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code     VARCHAR(20) NOT NULL,
    item_name     VARCHAR(100) NOT NULL,
    category      VARCHAR(30),
    unit          VARCHAR(10),
    price         DECIMAL(10,2) NOT NULL,
    material_cost DECIMAL(10,2) DEFAULT 0,
    status        TINYINT DEFAULT 1,
    insurance_type VARCHAR(10) DEFAULT '自费',
    insurance_rate DECIMAL(4,2) DEFAULT 0,
    CONSTRAINT uk_item_code UNIQUE (item_code)
);

-- 12. 预约单表（状态机 + 预付挂号费 + 取消违约金）
CREATE TABLE IF NOT EXISTS appointment (
    appointment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_no VARCHAR(30) NOT NULL,
    patient_id     BIGINT NOT NULL,
    doctor_id      BIGINT NOT NULL,
    schedule_id    BIGINT NOT NULL,
    store_id       BIGINT NOT NULL,
    visit_date     DATE   NOT NULL,
    time_slot      VARCHAR(20),
    deposit_amount DECIMAL(10,2) DEFAULT 0,
    deposit_status TINYINT DEFAULT 0,
    cancel_fee     DECIMAL(10,2) DEFAULT 0,
    remind_time    DATETIME,
    status         TINYINT DEFAULT 0,
    cancel_reason  VARCHAR(200),
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_appt_no UNIQUE (appointment_no)
);

-- 13. 诊疗记录表（病历主档）
CREATE TABLE IF NOT EXISTS treatment_record (
    record_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id  BIGINT NOT NULL,
    doctor_id       BIGINT NOT NULL,
    patient_id      BIGINT NOT NULL,
    chief_complaint VARCHAR(500),
    diagnosis       VARCHAR(500),
    allergy_check   TINYINT DEFAULT 1,
    tooth_position  VARCHAR(50),
    visit_status    VARCHAR(30),
    next_visit      DATE
);

-- 14. 诊疗明细表（随病历组合，价格快照）
CREATE TABLE IF NOT EXISTS treatment_detail (
    detail_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id     BIGINT NOT NULL,
    item_id       BIGINT NOT NULL,
    tooth_position VARCHAR(50),
    quantity      INT    DEFAULT 1,
    unit_price    DECIMAL(10,2),
    material_id   BIGINT
);

-- 15. 电子账单表（生成后金额不可改，退款红冲）
CREATE TABLE IF NOT EXISTS bill (
    bill_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no      VARCHAR(30) NOT NULL,
    record_id    BIGINT,
    patient_id   BIGINT NOT NULL,
    store_id     BIGINT NOT NULL,
    total_amount DECIMAL(12,2) DEFAULT 0,
    discount     DECIMAL(12,2) DEFAULT 0,
    payable      DECIMAL(12,2) DEFAULT 0,
    pay_channel  VARCHAR(20),
    pay_trade_no VARCHAR(50),
    insurance_cover DECIMAL(12,2) DEFAULT 0,
    self_pay     DECIMAL(12,2) DEFAULT 0,
    status       TINYINT DEFAULT 0,
    pay_time     DATETIME,
    CONSTRAINT uk_bill_no UNIQUE (bill_no)
);

-- 16. 账单明细表
CREATE TABLE IF NOT EXISTS bill_detail (
    bill_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id        BIGINT NOT NULL,
    item_name      VARCHAR(100),
    quantity       INT    DEFAULT 1,
    amount         DECIMAL(10,2)
);

-- 17. 门店季度运营成本表
CREATE TABLE IF NOT EXISTS operation_cost (
    cost_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id     BIGINT NOT NULL,
    year         INT    NOT NULL,
    quarter      INT    NOT NULL,
    rent         DECIMAL(12,2) DEFAULT 0,
    labor        DECIMAL(12,2) DEFAULT 0,
    depreciation DECIMAL(12,2) DEFAULT 0,
    amount       DECIMAL(12,2) DEFAULT 0
);

-- 18. 季度业绩核算表（净利润提成制）
CREATE TABLE IF NOT EXISTS performance (
    performance_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id        BIGINT NOT NULL,
    store_id         BIGINT NOT NULL,
    year             INT    NOT NULL,
    quarter          INT    NOT NULL,
    revenue          DECIMAL(12,2) DEFAULT 0,
    direct_cost      DECIMAL(12,2) DEFAULT 0,
    share_cost       DECIMAL(12,2) DEFAULT 0,
    net_profit       DECIMAL(12,2) DEFAULT 0,
    incentive_amount DECIMAL(12,2) DEFAULT 0,
    status           TINYINT DEFAULT 0
);

-- 19. 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    role_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    status    TINYINT DEFAULT 1,
    CONSTRAINT uk_role_name UNIQUE (role_name)
);

-- 20. 权限点表
CREATE TABLE IF NOT EXISTS sys_permission (
    perm_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_name VARCHAR(50) NOT NULL,
    perm_type VARCHAR(20) DEFAULT '菜单',
    parent_id BIGINT,
    CONSTRAINT uk_perm_name UNIQUE (perm_name)
);

-- 21. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL
);

-- 22. 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    perm_id BIGINT NOT NULL
);

-- 23. 牙医资质证书表
CREATE TABLE IF NOT EXISTS doctor_qualification (
    qualification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id        BIGINT NOT NULL,
    qual_name        VARCHAR(100) NOT NULL,
    cert_no          VARCHAR(50) NOT NULL,
    issue_org        VARCHAR(100),
    expire_date      DATE,
    audit_status     TINYINT DEFAULT 0,
    auditor_id       BIGINT,
    CONSTRAINT uk_qual_certno UNIQUE (cert_no)
);

-- 24. 公告表
CREATE TABLE IF NOT EXISTS notice (
    notice_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    content      TEXT,
    publisher_id BIGINT NOT NULL,
    publish_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status       TINYINT DEFAULT 1
);

-- 25. 登录日志表（用户进入留痕：成功/失败均记录，支持审计）
CREATE TABLE IF NOT EXISTS sys_login_log (
    log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    account     VARCHAR(50),
    real_name   VARCHAR(50),
    role_name   VARCHAR(30),
    ip          VARCHAR(64),
    user_agent  VARCHAR(300),
    success     TINYINT DEFAULT 1,
    fail_reason VARCHAR(200),
    login_time  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 26. 业务操作留痕表（人员/价目/公告/账单等增减动作全量留痕，符合监管审计要求）
CREATE TABLE IF NOT EXISTS sys_op_log (
    log_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    action        VARCHAR(20),
    target_type   VARCHAR(30),
    target_desc   VARCHAR(200),
    detail        VARCHAR(500),
    op_time       DATETIME DEFAULT CURRENT_TIMESTAMP
);
