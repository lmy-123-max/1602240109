// pages/booking/booking.js — 在线预约：门店 → 医生 → 日期 → 时段 → 确认
const { cloud } = require('../../utils/cloud');
const { getStores, getDoctors } = require('../../utils/biz');
const { todayStr, weekdayOf, toast, toastErr, SLOTS } = require('../../utils/format');

const PHONE_RE = /^1\d{10}$/;

function buildDates() {
  const out = [];
  const base = new Date();
  for (let i = 0; i < 7; i++) {
    const t = new Date(base.getTime() + i * 24 * 3600 * 1000);
    const m = t.getMonth() + 1;
    const d = t.getDate();
    const date = t.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (d < 10 ? '0' + d : d);
    out.push({
      date: date,
      md: (m < 10 ? '0' + m : m) + '/' + (d < 10 ? '0' + d : d),
      weekday: i === 0 ? '今天' : weekdayOf(date),
    });
  }
  return out;
}

Page({
  data: {
    authState: 'checking',
    showLogin: false,
    stores: [],
    storeId: null,
    curStore: null,
    doctors: [],
    docLoading: false,
    doctorId: null,
    selDoctor: null,
    dates: buildDates(),
    date: todayStr(),
    schedules: [],
    schLoading: false,
    selId: null,
    selSlotText: '',
    patientName: '',
    patientPhone: '',
    submitting: false,
  },

  onLoad() {
    this.initBase();
  },

  onShow() {
    this.restoreAuth();
  },

  async restoreAuth() {
    this.setData({ authState: 'checking' });
    let session = null;
    try {
      const res = await getApp().ensureSession();
      session = res && res.data ? res.data : null;
    } catch (e) {
      getApp().resetSession();
    }
    if (session) {
      this.setData({ authState: 'signed-in', showLogin: false });
      if (!this.data.patientName && session.user && session.user.name) {
        this.setData({ patientName: session.user.name });
      }
    } else {
      this.setData({ authState: 'signed-out' });
    }
  },

  async initBase() {
    const res = await getStores();
    const stores = res.data || [];
    this.setData({ stores: stores, storeId: stores.length ? stores[0].id : null, curStore: stores[0] || null });
    this.loadDoctors();
  },

  async onStore(e) {
    const id = Number(e.currentTarget.dataset.id);
    const cur = this.data.stores.filter(function (s) { return s.id === id; })[0];
    this.setData({ storeId: id, curStore: cur, doctors: [], doctorId: null, selDoctor: null, schedules: [], selId: null });
    this.loadDoctors();
  },

  async loadDoctors() {
    this.setData({ docLoading: true });
    const res = await getDoctors(this.data.storeId);
    this.setData({ docLoading: false, doctors: res.data || [] });
    if (res.error) toastErr(res.error.message);
  },

  onDoctor(e) {
    const id = Number(e.currentTarget.dataset.id);
    const doc = this.data.doctors.filter(function (d) { return d.id === id; })[0];
    this.setData({ doctorId: id, selDoctor: doc, schedules: [], selId: null, schLoading: true });
    this.loadSchedules();
  },

  onDate(e) {
    this.setData({ date: e.currentTarget.dataset.date, schedules: [], selId: null, schLoading: true });
    if (this.data.doctorId) this.loadSchedules();
  },

  async loadSchedules() {
    const res = await cloud.database.from('clinic_schedules')
      .select('*')
      .eq('doctor_id', this.data.doctorId)
      .eq('work_date', this.data.date)
      .eq('status', 1)
      .order('time_slot');
    if (res.error) {
      this.setData({ schLoading: false, schedules: [] });
      toastErr(res.error.message);
      return;
    }
    const rows = (res.data || []).map(function (s) {
      return {
        id: s.id,
        slotText: SLOTS[s.time_slot] || '时段',
        remaining: (s.quota_total || 0) - (s.quota_used || 0),
      };
    });
    this.setData({ schLoading: false, schedules: rows });
  },

  onSlot(e) {
    const id = Number(e.currentTarget.dataset.id);
    const sel = this.data.schedules.filter(function (s) { return s.id === id; })[0];
    if (!sel) return;
    if (sel.remaining <= 0) {
      toastErr('该时段已约满，请选择其他时段');
      return;
    }
    this.setData({
      selId: id,
      selSlotText: sel.slotText.split(' ')[0],
    });
  },

  onName(e) { this.setData({ patientName: e.detail.value }); },
  onPhone(e) { this.setData({ patientPhone: e.detail.value }); },

  onBook() {
    if (this.data.authState !== 'signed-in') {
      this.setData({ showLogin: true });
      return;
    }
    if (!this.data.patientName) { toastErr('请填写就诊人姓名'); return; }
    if (!PHONE_RE.test(this.data.patientPhone || '')) { toastErr('请填写正确的 11 位手机号'); return; }
    this.submit();
  },

  async submit() {
    const sel = this.data.schedules.filter(function (s) { return s.id === this.data.selId; }, this)[0];
    if (!sel) { toastErr('请选择就诊时段'); return; }
    this.setData({ submitting: true });
    const book = await cloud.database.rpc('clinic_book', {
      p_schedule_id: sel.id,
      p_patient_name: this.data.patientName,
      p_phone: this.data.patientPhone,
    });
    if (book.error) {
      this.setData({ submitting: false });
      toastErr(book.error.message || '预约失败，请重试');
      return;
    }
    const info = book.data || {};
    // 挂号费账单（预约时已通过微信支付）
    await cloud.database.from('clinic_bills').insert({
      bill_no: 'B' + Date.now(),
      appointment_id: info.appointment_id,
      patient_name: this.data.patientName,
      store_name: info.store_name,
      items_summary: '预约挂号费',
      total_amount: 20,
      discount: 0,
      payable: 20,
      status: 1,
      pay_channel: '微信支付',
      pay_time: new Date().toISOString(),
      visit_date: info.visit_date,
    }).select();
    this.setData({ submitting: false });
    toast('预约成功');
    wx.switchTab({ url: '/pages/appointments/appointments' });
  },

  onSignedIn() {
    getApp().resetSession();
    this.setData({ showLogin: false });
    this.restoreAuth();
  },

  closeLogin() {
    this.setData({ showLogin: false });
  },
}
);
