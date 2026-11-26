// packageDoctor/today/today.js — 今日队列：按号序列表 / 叫号 / 完成就诊
const staffUtil = require('../../utils/staff');
const { APPT_STATUS, SLOTS, toast, toastErr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
    list: [],
    statWait: 0,
    statServing: 0,
    statDone: 0,
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
    if (!info || info.role !== 'doctor') {
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
    const res = await cloud.database.rpc('clinic_doctor_today', {});
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const rows = (res.data || []).map(function (a) {
      const st = APPT_STATUS[a.status] || { text: '未知', cls: 'tag-gray' };
      return {
        id: a.id,
        appointmentNo: a.appointment_no,
        patientName: a.patient_name,
        patientPhone: a.patient_phone || '',
        slotText: SLOTS[a.time_slot] || '',
        status: a.status,
        statusText: st.text,
        statusCls: st.cls,
        remark: a.remark || '',
        canCall: a.status === 1 || a.status === 2,
        canFinish: a.status === 3,
      };
    });
    this.setData({
      list: rows,
      statWait: rows.filter(function (r) { return r.status === 1 || r.status === 2; }).length,
      statServing: rows.filter(function (r) { return r.status === 3; }).length,
      statDone: rows.filter(function (r) { return r.status === 4; }).length,
    });
  },

  onPullDownRefresh() {
    this.load().then(function () { wx.stopPullDownRefresh(); });
  },

  onCall(e) {
    const id = Number(e.currentTarget.dataset.id);
    const that = this;
    wx.showModal({
      title: '叫号接诊',
      content: '确认将该患者叫入诊室，状态置为「诊疗中」？',
      success: function (r) {
        if (!r.confirm) return;
        that.doCall(id);
      },
    });
  },

  async doCall(id) {
    const res = await cloud.database.rpc('clinic_doctor_call', { p_id: id });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    toast('已叫号，患者进入诊疗中');
    this.load();
  },

  onFinish(e) {
    const id = e.currentTarget.dataset.id;
    const name = e.currentTarget.dataset.name;
    wx.navigateTo({
      url: '/packageDoctor/consult/consult?id=' + id +
        '&name=' + encodeURIComponent(name || ''),
    });
  },

  onCallPhone(e) {
    const phone = e.currentTarget.dataset.phone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },
}
);
