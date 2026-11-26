// utils/account.js — 账号注册封装：患者注册建档 / 牙医入驻（工号自动分配）
// 云端 SECURITY DEFINER RPC 保证：患者数据只有本人可读写，牙医入驻自动生成工号并绑定当前账号
const { cloud } = require('./cloud');

// 患者注册：写入 clinic_patients（uid 维度，一人一份），自动生成健康卡号 HC+10 位
async function patientRegister(name, phone) {
  const res = await cloud.database.rpc('clinic_patient_register', {
    p_name: String(name || '').trim(),
    p_phone: phone ? String(phone).trim() : null,
  });
  if (res.error) throw res.error;
  return res.data;
}

// 当前账号的就诊人档案；未注册返回 null
async function patientMe() {
  const res = await cloud.database.rpc('clinic_patient_me', {});
  if (res.error) return null;
  return res.data || null;
}

// 牙医入驻：clinic_doctors + clinic_staff 建档，工号 D1xx 自动分配，并自动绑定当前账号
async function doctorRegister(name, title, specialty, storeId, years) {
  const res = await cloud.database.rpc('clinic_doctor_register', {
    p_name: String(name || '').trim(),
    p_title: title || '主治医师',
    p_specialty: specialty || '全科',
    p_store_id: storeId ? Number(storeId) : null,
    p_years: years ? Number(years) : null,
  });
  if (res.error) throw res.error;
  return res.data;
}

module.exports = {
  patientRegister: patientRegister,
  patientMe: patientMe,
  doctorRegister: doctorRegister,
};
