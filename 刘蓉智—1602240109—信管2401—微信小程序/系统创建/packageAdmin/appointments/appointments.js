// packageAdmin/appointments/appointments.js — 预约管理：筛选 / 确认 / 取消 / 拨打患者电话
const staffUtil = require('../../utils/staff');
const { APPT_STATUS, SLOTS, todayStr, toast, toastErr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
    date: '',
    statusIndex: 0,
    statusOptions: ['全部状态', '待确认', '已确认', '已到诊', '诊疗中', '已完成', '已取消'],
    statusValues: [null, 0, 1, 2, 3, 4, 5],
    list: [],
  },

  onShow() {
    this.restoreAuth();
  },

  async restoreAuth() {
    let session = null;
    try {
      const res = await getApp().ensureSession();
      session = res && res.data ? res.data : null;
    } catch (e) {
      getApp().resetSession();
    }
    if (!session) {
      this.setData({ authState: 'signed-out', list: [] });
      return;
    }
    const info = await staffUtil.me();
    if (!info || (info.role !== 'store_admin' && info.role !== 'hq_admin')) {
      this.setData({ authState: 'denied', list: [] });
      return;
    }
    this.setData({ authState: 'ready', staffInfo: info });
    this.load();
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  async load() {
    this.setData({ loading: true });
    const res = await cloud.database.rpc('clinic_admin_appointments', {
      p_date: this.data.date || null,
      p_status: this.data.statusValues[this.data.statusIndex],
    });
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const today = todayStr();
    const rows = (res.data || []).map(function (a) {
      const st = APPT_STATUS[a.status] || { text: '未知', cls: 'tag-gray' };
      return {
        id: a.id,
        appointmentNo: a.appointment_no,
        patientName: a.patient_name,
        patientPhone: a.patient_phone || '',
        doctorName: a.doctor_name,
        storeName: a.store_name,
        visitDate: a.visit_date,
        isToday: a.visit_date === today,
        slotText: SLOTS[a.time_slot] || '',
        statusText: st.text,
        statusCls: st.cls,
        canConfirm: a.status === 0,
        canCancel: a.status === 0 || a.status === 1,
      };
    });
    this.setData({ list: rows });
  },

  onPullDownRefresh() {
    this.load().then(function () { wx.stopPullDownRefresh(); });
  },

  onDate(e) {
    this.setData({ date: e.detail.value });
    this.load();
  },

  clearDate() {
    this.setData({ date: '' });
    this.load();
  },

  onStatus(e) {
    this.setData({ statusIndex: Number(e.detail.value) });
    this.load();
  },

  onConfirm(e) {
    const id = Number(e.currentTarget.dataset.id);
    const that = this;
    wx.showModal({
      title: '确认预约',
      content: '确认该患者的预约？确认后患者会收到就诊安排。',
      success: function (r) {
        if (!r.confirm) return;
        that.setStatus(id, 1, '已确认预约');
      },
    });
  },

  onCancel(e) {
    const id = Number(e.currentTarget.dataset.id);
    const that = this;
    wx.showModal({
      title: '取消预约',
      content: '取消后将释放号源，患者端会同步显示已取消。确认取消？',
      confirmColor: '#e8590c',
      success: function (r) {
        if (!r.confirm) return;
        that.setStatus(id, 5, '已取消预约');
      },
    });
  },

  async setStatus(id, status, okText) {
    const res = await cloud.database.rpc('clinic_admin_set_status', { p_id: id, p_status: status });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    toast(okText);
    this.load();
  },

  onCallPhone(e) {
    const phone = e.currentTarget.dataset.phone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },
}
);
