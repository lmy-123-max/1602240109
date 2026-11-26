// packageAdmin/workbench/workbench.js — 管理工作台首页：运营总览 + 功能入口
const staffUtil = require('../../utils/staff');
const { money } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    overview: null,
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
    if (!info || (info.role !== 'store_admin' && info.role !== 'hq_admin')) {
      this.setData({ authState: 'denied', staffInfo: null });
      return;
    }
    this.setData({ authState: 'ready', staffInfo: info });
    this.loadOverview();
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  async loadOverview() {
    const res = await cloud.database.rpc('clinic_admin_overview', {});
    if (res.error) {
      wx.showToast({ title: res.error.message, icon: 'none' });
      return;
    }
    const o = res.data || {};
    o.revenueText = money(o.monthRevenue);
    o.insuranceText = money(o.monthInsurance);
    o.selfText = money(o.monthSelf);
    this.setData({ overview: o });
  },

  goAppts() { wx.navigateTo({ url: '/packageAdmin/appointments/appointments' }); },
  goNotices() { wx.navigateTo({ url: '/packageAdmin/notices/notices' }); },
  goDoctors() { wx.navigateTo({ url: '/packageAdmin/doctors/doctors' }); },
  goStaff() { wx.navigateTo({ url: '/packageAdmin/staff/staff' }); },
  goItems() { wx.navigateTo({ url: '/packageAdmin/items/items' }); },
  goStores() { wx.navigateTo({ url: '/packageAdmin/stores/stores' }); },
  goLogs() { wx.navigateTo({ url: '/packageAdmin/logs/logs' }); },
}
);
