// utils/biz.js — 公共数据（门店/医生/公告）查询与缓存；患者私有数据的读写见各页面
const { cloud } = require('./cloud');

function unwrap(res, fallback) {
  if (res && res.error) {
    return { data: fallback, error: res.error };
  }
  return { data: res ? res.data : fallback, error: null };
}

let storesCache = null;
let noticesCache = null;

async function getStores(force) {
  if (storesCache && !force) return storesCache;
  const res = await cloud.database.from('clinic_stores').select('*').eq('status', 1).order('id');
  const out = unwrap(res, []);
  storesCache = out.data;
  return out;
}

async function getDoctors(storeId) {
  let q = cloud.database.from('clinic_doctors').select('*').eq('status', 1).order('id');
  if (storeId) {
    q = q.eq('store_id', storeId);
  }
  const res = await q;
  return unwrap(res, []);
}

async function getNotices(force) {
  if (noticesCache && !force) return noticesCache;
  const res = await cloud.database.from('clinic_notices').select('*').eq('status', 1).order('created_at', { ascending: false }).limit(5);
  const out = unwrap(res, []);
  noticesCache = out.data;
  return out;
}

async function getItems() {
  const res = await cloud.database.from('clinic_items').select('*').eq('status', 1).order('price');
  return unwrap(res, []);
}

module.exports = {
  getStores: getStores,
  getDoctors: getDoctors,
  getNotices: getNotices,
  getItems: getItems,
};
