// utils/staff.js — 员工（牙医/门店管理员/总部管理员）身份绑定与工作台 RPC 封装
// 身份由云端 clinic_staff / clinic_staff_bind + SECURITY DEFINER RPC 保证：
// 绑定工号后，云端函数只返回/处理该身份有权访问的数据。
const { cloud } = require('./cloud');

const KEY = 'dental_staff';

const ROLE_TEXT = {
  doctor: '牙医',
  store_admin: '门店管理员',
  hq_admin: '总部管理员',
};

const ROLE_HOME = {
  doctor: '/packageDoctor/workbench/workbench',
  store_admin: '/packageAdmin/workbench/workbench',
  hq_admin: '/packageAdmin/workbench/workbench',
};

function cached() {
  try {
    return wx.getStorageSync(KEY) || null;
  } catch (e) {
    return null;
  }
}

function saveCache(data) {
  try {
    if (data) {
      wx.setStorageSync(KEY, data);
    } else {
      wx.removeStorageSync(KEY);
    }
  } catch (e) { /* 忽略缓存异常 */ }
}

// 当前登录账号绑定的员工身份；未绑定返回 null
async function me() {
  let session = null;
  try {
    const res = await getApp().ensureSession();
    session = res && res.data ? res.data : null;
  } catch (e) {
    return null;
  }
  if (!session) {
    saveCache(null);
    return null;
  }
  const res = await cloud.database.rpc('clinic_staff_me', {});
  if (res.error) return null;
  const data = res.data || null;
  saveCache(data);
  return data;
}

// 绑定员工工号（演示工号：D101 牙医 / S201 门店管理员 / H301 总部管理员）
async function bind(code) {
  const res = await cloud.database.rpc('clinic_staff_bind', { p_code: String(code || '').trim() });
  if (res.error) {
    throw res.error;
  }
  const data = res.data;
  saveCache(data);
  return data;
}

function unbind() {
  saveCache(null);
}

function homeOf(role) {
  return ROLE_HOME[role] || '';
}

module.exports = {
  me: me,
  bind: bind,
  unbind: unbind,
  cached: cached,
  homeOf: homeOf,
  ROLE_TEXT: ROLE_TEXT,
};
