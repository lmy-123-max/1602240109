// packageDoctor/perf/perf.js — 我的业绩：本月诊疗量 / 收入 / 复诊统计
const staffUtil = require('../../utils/staff');
const { money } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
    perf: null,
    monthText: '',
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
      this.setData({ authState: 'signed-out', perf: null });
      return;
    }
    const info = await staffUtil.me();
    if (!info || info.role !== 'doctor') {
      this.setData({ authState: 'denied', perf: null });
      return;
    }
    const now = new Date();
    this.setData({
      authState: 'ready',
      staffInfo: info,
      monthText: now.getFullYear() + ' 年 ' + (now.getMonth() + 1) + ' 月',
    });
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
    const res = await cloud.database.rpc('clinic_doctor_perf', {});
    this.setData({ loading: false });
    if (res.error) {
      wx.showToast({ title: res.error.message, icon: 'none' });
      return;
    }
    const p = res.data || {};
    p.revenueText = money(p.monthRevenue);
    this.setData({ perf: p });
  },
}
);
