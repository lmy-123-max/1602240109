// packageDoctor/workbench/workbench.js — 医生工作台首页：今日概览 + 功能入口
const staffUtil = require('../../utils/staff');
const { money } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking', // checking / signed-out / denied / ready
    staffInfo: null,
    perf: null,
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
      this.setData({ authState: 'signed-out', staffInfo: null });
      return;
    }
    const info = await staffUtil.me();
    if (!info || info.role !== 'doctor') {
      this.setData({ authState: 'denied', staffInfo: null });
      return;
    }
    this.setData({ authState: 'ready', staffInfo: info });
    this.loadPerf();
  },

  async loadPerf() {
    const res = await cloud.database.rpc('clinic_doctor_perf', {});
    if (!res.error && res.data) {
      const p = res.data;
      p.revenueText = money(p.monthRevenue);
      this.setData({ perf: p });
    }
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  goToday() { wx.navigateTo({ url: '/packageDoctor/today/today' }); },
  goSchedule() { wx.navigateTo({ url: '/packageDoctor/schedule/schedule' }); },
  goPerf() { wx.navigateTo({ url: '/packageDoctor/perf/perf' }); },
}
);
