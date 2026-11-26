-- MySQL 模式演示数据（幂等：先清空核心业务表再插入）
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM notice;
DELETE FROM doctor_qualification;
DELETE FROM sys_role_permission;
DELETE FROM sys_user_role;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM bill_detail;
DELETE FROM bill;
DELETE FROM treatment_detail;
DELETE FROM treatment_record;
DELETE FROM appointment;
DELETE FROM schedule;
DELETE FROM allergy_record;
DELETE FROM purchase_record;
DELETE FROM operation_cost;
DELETE FROM performance;
DELETE FROM work_calendar;
DELETE FROM dental_chair;
DELETE FROM treatment_item;
DELETE FROM supplier;
DELETE FROM store;
DELETE FROM doctor;
DELETE FROM patient;
DELETE FROM sys_user;
SET FOREIGN_KEY_CHECKS=1;

-- =====================================================================
-- 牙科门店管理系统 演示数据（H2 / MySQL 通用，日期基准 2026-10-22）
-- 演示账号：patient01 / doctor01 / store101 / hq001，密码均为 123456
-- =====================================================================

-- ---------- 用户（密码为 MD5("dental#123456")） ----------
INSERT INTO sys_user (user_id, account, password, real_name, gender, phone, status) VALUES
(1, 'patient01', 'b74fd1563ba21b07c3b2caffc7615ccf', '刘晓',   0, '13800000001', 1),
(2, 'patient02', 'b74fd1563ba21b07c3b2caffc7615ccf', '王强',   1, '13800000002', 1),
(3, 'doctor01',  'b74fd1563ba21b07c3b2caffc7615ccf', '林正畸', 1, '13900000001', 1),
(4, 'doctor02',  'b74fd1563ba21b07c3b2caffc7615ccf', '陈种植', 1, '13900000002', 1),
(5, 'store101',  'b74fd1563ba21b07c3b2caffc7615ccf', '东湖店前台', 0, '13700000001', 1),
(6, 'hq001',     'b74fd1563ba21b07c3b2caffc7615ccf', '总部管理员', 1, '13600000001', 1),
(7, 'doctor03',  'b74fd1563ba21b07c3b2caffc7615ccf', '王牙周', 0, '13900000003', 1),
(8, 'doctor04',  'b74fd1563ba21b07c3b2caffc7615ccf', '赵拔牙', 1, '13900000004', 1),
(9, 'doctor05',  'b74fd1563ba21b07c3b2caffc7615ccf', '李儿牙', 0, '13900000005', 1),
(10,'doctor06',  'b74fd1563ba21b07c3b2caffc7615ccf', '周修复', 0, '13900000006', 1),
(11,'doctor07',  'b74fd1563ba21b07c3b2caffc7615ccf', '吴正畸', 0, '13900000007', 1),
(12,'doctor08',  'b74fd1563ba21b07c3b2caffc7615ccf', '郑种植', 1, '13900000008', 1),
(13,'doctor09',  'b74fd1563ba21b07c3b2caffc7615ccf', '孙洁牙', 0, '13900000009', 1),
(14,'doctor10',  'b74fd1563ba21b07c3b2caffc7615ccf', '罗全科', 1, '13900000010', 1);

-- ---------- 患者子表 ----------
INSERT INTO patient (patient_id, health_card_no, default_store_id, birthday, emergency_phone) VALUES
(1, 'HC0000000001', 1, '1998-05-12', '13800010001'),
(2, 'HC0000000002', 1, '1985-11-03', '13800010002');

-- ---------- 牙医子表 ----------
INSERT INTO doctor (doctor_id, job_no, title, specialty, practice_no, home_store_id, incentive_rate) VALUES
(3, 'D1001', '主治医师',   '正畸/牙体牙髓', 'PRA202011001', 1, 0.1000),
(4, 'D1002', '副主任医师', '种植/修复',     'PRA201511002', 1, 0.1000),
(7, 'D1003', '主治医师',   '牙周/洁牙',     'PRA201803003', 1, 0.1000),
(8, 'D1004', '副主任医师', '口腔外科',      'PRA201206004', 1, 0.1000),
(9, 'D1005', '主治医师',   '儿童牙科',      'PRA201909005', 1, 0.1000),
(10,'D1006', '主治医师',   '修复美学',      'PRA201805006', 2, 0.1000),
(11,'D1007', '主治医师',   '正畸',          'PRA201711007', 2, 0.1000),
(12,'D1008', '主任医师',   '种植',          'PRA201004008', 2, 0.1000),
(13,'D1009', '主治医师',   '洁牙',          'PRA202002009', 2, 0.1000),
(14,'D1010', '主治医师',   '全科/急诊',     'PRA201808010', 1, 0.1000);

-- ---------- 门店 ----------
INSERT INTO store (store_id, store_code, store_name, address, phone, open_date, status) VALUES
(1, 'ST001', '东湖口腔门诊部', '武汉市洪山区东湖路 88 号', '027-88880001', '2022-03-01', 1),
(2, 'ST002', '光谷口腔分院',   '武汉市江夏区光谷大道 66 号', '027-88880002', '2024-06-01', 1);

-- ---------- 供应商 ----------
INSERT INTO supplier (supplier_id, supplier_name, contact_person, phone, supply_scope, settle_cycle, status) VALUES
(1, '华中齿科设备有限公司', '周经理', '13500000001', '设备', '月结30天', 1),
(2, '康达医疗耗材供应商',   '吴经理', '13500000002', '耗材/药品', '月结15天', 1);

-- ---------- 牙椅（门店 1 三台在用，门店 2 三台在用） ----------
INSERT INTO dental_chair (chair_id, store_id, chair_no, device_name, purchase_price, purchase_date, deprec_years, monthly_deprec, per_use_cost, supplier_id, status) VALUES
(1, 1, 'C01', '西诺德 Sirona C8',      280000.00, '2022-03-10', 8, 2916.67, 15.00, 1, 1),
(2, 1, 'C02', '西诺德 Sirona C8',      280000.00, '2022-03-10', 8, 2916.67, 15.00, 1, 1),
(3, 1, 'C03', '菲森 Fussen S1600',     180000.00, '2023-09-01', 8, 1875.00, 12.00, 1, 1),
(4, 2, 'C11', '菲森 Fussen S1600',     180000.00, '2024-06-20', 8, 1875.00, 12.00, 1, 1),
(5, 2, 'C12', '西诺德 Sirona C8',      280000.00, '2024-06-20', 8, 2916.67, 15.00, 1, 1),
(6, 2, 'C13', '牙科综合治疗台 Am-600', 120000.00, '2025-01-15', 8, 1250.00, 10.00, 1, 1);

-- ---------- 工作日历（含一个"法定节假日"演示日 10-26） ----------
INSERT INTO work_calendar (calendar_id, store_id, cal_date, day_type, open_time, close_time, remark) VALUES
(1, 1, '2026-10-22', 1, '09:00:00', '18:00:00', NULL),
(2, 1, '2026-10-23', 1, '09:00:00', '18:00:00', NULL),
(3, 1, '2026-10-24', 1, '09:00:00', '18:00:00', '周六门诊'),
(4, 1, '2027-01-01', 2, NULL, NULL, '元旦（法定节假日演示）'),
(5, 2, '2026-10-22', 1, '09:00:00', '18:00:00', NULL);

-- ---------- 采购记录 ----------
INSERT INTO purchase_record (purchase_id, store_id, supplier_id, item_type, item_name, quantity, unit_price, total_amount, purchase_date, operator_id) VALUES
(1, 1, 1, 2, '西诺德 Sirona C8 牙椅', 2, 280000.00, 560000.00, '2022-03-10', 6),
(2, 1, 1, 2, '菲森 Fussen S1600 牙椅', 1, 180000.00, 180000.00, '2023-09-01', 6),
(3, 1, 2, 1, '复合树脂（3M Z350）', 50, 85.00, 4250.00, '2026-10-10', 5),
(4, 1, 2, 1, '一次性检查盘', 200, 6.50, 1300.00, '2026-10-10', 5);

-- ---------- 患者过敏史（独立实体，一对多） ----------
INSERT INTO allergy_record (allergy_id, patient_id, allergen_name, allergy_type, severity, reaction_desc, source, record_date, status) VALUES
(1, 1, '青霉素', 1, 3, '皮试阳性，禁用青霉素类抗生素', 1, '2026-01-15', 1),
(2, 2, '乳胶',   2, 2, '接触性皮疹', 1, '2026-03-20', 1);

-- ---------- 诊疗项目价目（10 大服务类别，21 个项目） ----------
INSERT INTO treatment_item (item_id, item_code, item_name, category, unit, price, material_cost, status) VALUES
(1, 'D001', '超声波洁牙',             '洁牙',     '次', 168.00,  20.00, 1),
(2, 'D002', '补牙（树脂充填）',       '补牙',     '颗', 300.00,  60.00, 1),
(3, 'D003', '根管治疗（前牙）',       '牙体牙髓', '颗', 800.00,  150.00, 1),
(4, 'D004', '种植牙（国产植体）',     '种植',     '颗', 6000.00, 2200.00, 1),
(5, 'D005', '儿童涂氟',               '预防',     '次', 120.00,  15.00, 1),
(6, 'D006', '喷砂洁牙',               '洁牙',     '次', 268.00,  35.00, 1),
(7, 'D007', '龈下刮治（全口分区）',   '牙周',     '区', 480.00,  90.00, 1),
(8, 'D008', '牙周系统治疗',           '牙周',     '次', 1680.00, 300.00, 1),
(9, 'D009', '嵌体修复（树脂嵌体）',   '补牙',     '颗', 680.00,  140.00, 1),
(10,'D010', '根管治疗（磨牙）',       '牙体牙髓', '颗', 1200.00, 220.00, 1),
(11,'D011', '根尖手术（显微）',       '牙体牙髓', '颗', 1500.00, 280.00, 1),
(12,'D012', '智齿拔除（阻生齿）',     '口腔外科', '颗', 680.00,  120.00, 1),
(13,'D013', '复杂牙拔除',             '口腔外科', '颗', 380.00,  60.00, 1),
(14,'D014', '种植牙（进口植体）',     '种植',     '颗', 9800.00, 3600.00, 1),
(15,'D015', '种植骨增量手术（GBR）',  '种植',     '次', 4500.00, 1500.00, 1),
(16,'D016', '金属托槽正畸（全疗程）', '正畸',     '疗程', 12800.00, 5000.00, 1),
(17,'D017', '隐形正畸（全疗程）',     '正畸',     '疗程', 29800.00, 12000.00, 1),
(18,'D018', '全瓷冠修复',             '修复美学', '颗', 2800.00, 900.00, 1),
(19,'D019', '烤瓷冠修复',             '修复美学', '颗', 1500.00, 450.00, 1),
(20,'D020', '儿童窝沟封闭',           '儿童牙科', '颗', 150.00,  25.00, 1),
(21,'D021', '儿童乳牙拔除',           '儿童牙科', '颗', 80.00,   10.00, 1);

-- ---------- 排班（号源容量 ≤ 可用牙椅数 3） ----------
INSERT INTO schedule (schedule_id, doctor_id, store_id, work_date, time_slot, chair_id, quota_total, quota_used, status) VALUES
(1, 3, 1, '2026-10-22', 1, 1, 3, 1, 1),
(2, 3, 1, '2026-10-22', 2, 1, 3, 0, 1),
(3, 4, 1, '2026-10-22', 1, 2, 3, 1, 1),
(4, 4, 1, '2026-10-23', 1, 2, 3, 0, 1),
(5, 3, 1, '2026-10-20', 1, 1, 3, 1, 1),
(6, 3, 1, '2026-10-21', 1, 1, 3, 1, 1);

-- ---------- 预约单（预付挂号费 20 元；含取消违约金样例） ----------
INSERT INTO appointment (appointment_id, appointment_no, patient_id, doctor_id, schedule_id, store_id, visit_date, time_slot, deposit_amount, deposit_status, cancel_fee, remind_time, status, cancel_reason, create_time) VALUES
(1, 'A20261022001', 1, 3, 1, 1, '2026-10-22', '上午', 20.00, 1, 0,    '2026-10-22 08:40:00', 1, NULL, '2026-10-21 20:15:00'),
(2, 'A20261022002', 2, 4, 3, 1, '2026-10-22', '上午', 20.00, 1, 0,    '2026-10-22 08:40:00', 2, NULL, '2026-10-21 21:00:00'),
(3, 'A20261020001', 1, 3, 5, 1, '2026-10-20', '上午', 20.00, 2, 10.00, NULL, 5, '临时出差，距就诊不足 2 小时取消，收取 50% 违约金', '2026-10-19 09:30:00'),
(4, 'A20261021001', 2, 3, 6, 1, '2026-10-21', '上午', 20.00, 3, 0,    '2026-10-21 08:40:00', 4, NULL, '2026-10-20 15:00:00');

-- ---------- 历史病历（预约 4 已完成，用于统计与业绩演示） ----------
INSERT INTO treatment_record (record_id, appointment_id, doctor_id, patient_id, chief_complaint, diagnosis, allergy_check, tooth_position, visit_status, next_visit) VALUES
(1, 4, 3, 2, '左上后牙冷热敏感一月', '16 牙合面中龋', 1, '16', '治疗完成', '2027-04-01');

INSERT INTO treatment_detail (detail_id, record_id, item_id, tooth_position, quantity, unit_price) VALUES
(1, 1, 2, '16', 1, 300.00);

-- ---------- 电子账单（挂号费抵扣诊疗费） ----------
INSERT INTO bill (bill_id, bill_no, record_id, patient_id, store_id, total_amount, discount, payable, pay_channel, pay_trade_no, status, pay_time) VALUES
(1, 'B20261021103000001', 1, 2, 1, 300.00, 20.00, 280.00, '微信', 'T20261021103000001', 1, '2026-10-21 10:30:00');

INSERT INTO bill_detail (bill_detail_id, bill_id, item_name, quantity, amount) VALUES
(1, 1, '补牙（树脂充填）', 1, 300.00);

-- ---------- 门店季度运营成本 ----------
INSERT INTO operation_cost (cost_id, store_id, year, quarter, rent, labor, depreciation, amount) VALUES
(1, 1, 2026, 3, 30000.00, 50000.00, 7708.34, 87708.34),
(2, 1, 2026, 4, 30000.00, 50000.00, 7708.34, 87708.34);

-- ---------- RBAC：角色 / 权限 / 关联 ----------
INSERT INTO sys_role (role_id, role_name, role_code, status) VALUES
(1, '患者',       'patient',   1),
(2, '牙医',       'doctor',    1),
(3, '门店管理员', 'storeAdmin',1),
(4, '总部管理员', 'hqAdmin',   1);

INSERT INTO sys_permission (perm_id, perm_name, perm_type, parent_id) VALUES
(1, '患者服务',   '菜单', NULL),
(2, '预约挂号',   '按钮', 1),
(3, '就诊报到',   '按钮', 1),
(4, '门诊缴费',   '按钮', 1),
(5, '诊疗工作台', '菜单', NULL),
(6, '提交病历',   '按钮', 5),
(7, '门店管理',   '菜单', NULL),
(8, '排班管理',   '按钮', 7),
(9, '总部统计',   '菜单', NULL),
(10,'业绩核算',   '按钮', 9);

INSERT INTO sys_user_role (id, user_id, role_id) VALUES
(1, 1, 1), (2, 2, 1), (3, 3, 2), (4, 4, 2), (5, 5, 3), (6, 6, 4),
(7, 7, 2), (8, 8, 2), (9, 9, 2), (10, 10, 2), (11, 11, 2), (12, 12, 2),
(13, 13, 2), (14, 14, 2);

INSERT INTO sys_role_permission (id, role_id, perm_id) VALUES
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4),
(5, 2, 5), (6, 2, 6),
(7, 3, 7), (8, 3, 8),
(9, 4, 9), (10, 4, 10);

-- ---------- 牙医资质证书 ----------
INSERT INTO doctor_qualification (qualification_id, doctor_id, qual_name, cert_no, issue_org, expire_date, audit_status, auditor_id) VALUES
(1, 3, '医师执业证书（口腔类别）', 'PRA202011001', '湖北省卫生健康委员会', '2030-12-31', 2, 6),
(2, 4, '医师执业证书（口腔种植）', 'PRA201511002', '湖北省卫生健康委员会', '2028-06-30', 2, 6),
(3, 4, '口腔种植专项培训合格证',       'TR20170012',   '中华口腔医学会',       '2027-03-01', 1, 6),
(4, 7, '医师执业证书（口腔类别）', 'PRA201803003', '湖北省卫生健康委员会', '2029-05-31', 2, 6),
(5, 8, '医师执业证书（口腔类别）', 'PRA201206004', '湖北省卫生健康委员会', '2027-11-30', 2, 6),
(6, 9, '医师执业证书（口腔类别）', 'PRA201909005', '湖北省卫生健康委员会', '2031-02-28', 2, 6),
(7, 9, '儿童口腔舒适化治疗培训证', 'TR20210331',   '中华口腔医学会',       '2027-09-30', 0, NULL),
(8, 10,'医师执业证书（口腔类别）', 'PRA201805006', '湖北省卫生健康委员会', '2029-08-31', 2, 6),
(9, 11,'医师执业证书（口腔类别）', 'PRA201711007', '湖北省卫生健康委员会', '2028-04-30', 1, 6),
(10,12,'医师执业证书（口腔类别）', 'PRA201004008', '湖北省卫生健康委员会', '2026-12-31', 2, 6),
(11,13,'医师执业证书（口腔类别）', 'PRA202002009', '湖北省卫生健康委员会', '2030-06-30', 2, 6),
(12,14,'医师执业证书（口腔类别）', 'PRA201808010', '湖北省卫生健康委员会', '2029-10-31', 1, 6);

-- ---------- 公告 ----------
INSERT INTO notice (notice_id, title, content, publisher_id, publish_time, status) VALUES
(1, '十一月起正畸项目体验价', '11 月 1 日至 11 月 30 日，隐形正畸首次面诊免费，戴牙套立减 2000 元。', 6, '2026-10-20 09:00:00', 1),
(2, '节假日就诊提醒', '请您按预约时段提前 10-20 分钟到院取号；距就诊不足 2 小时取消将收取 50% 违约金。', 5, '2026-10-21 10:00:00', 1);
