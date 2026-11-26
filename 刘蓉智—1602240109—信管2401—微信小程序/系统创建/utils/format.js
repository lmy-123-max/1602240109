// utils/format.js — 展示用映射与小工具
const APPT_STATUS = {
  0: { text: '待确认', cls: 'tag-orange' },
  1: { text: '已确认', cls: 'tag-blue' },
  2: { text: '已到诊', cls: 'tag-green' },
  3: { text: '诊疗中', cls: 'tag-green' },
  4: { text: '已完成', cls: 'tag-gray' },
  5: { text: '已取消', cls: 'tag-gray' },
};

const SLOTS = {
  1: '上午 09:00-12:00',
  2: '下午 14:00-18:00',
};

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function money(v) {
  const n = Number(v || 0);
  return n.toFixed(2).replace(/\.00$/, '');
}

function fmtDate(d) {
  if (!d) return '';
  return String(d).slice(0, 10);
}

function todayStr() {
  const t = new Date();
  const m = t.getMonth() + 1;
  const day = t.getDate();
  return t.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day);
}

function weekdayOf(dateStr) {
  const t = new Date(dateStr.replace(/-/g, '/'));
  if (isNaN(t.getTime())) return '';
  return WEEKDAYS[t.getDay()];
}

function toast(msg, ok) {
  wx.showToast({ title: msg || '操作成功', icon: ok === false ? 'none' : 'success' });
}

function toastErr(msg) {
  wx.showToast({ title: msg || '操作失败', icon: 'none' });
}

module.exports = {
  APPT_STATUS: APPT_STATUS,
  SLOTS: SLOTS,
  money: money,
  fmtDate: fmtDate,
  todayStr: todayStr,
  weekdayOf: weekdayOf,
  toast: toast,
  toastErr: toastErr,
};
