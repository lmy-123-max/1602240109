// packageAdmin/doctors/doctors.js — 医生团队：按门店分组查看出诊牙医
const staffUtil = require('../../utils/staff');
const { getDoctors, getStores } = require('../../utils/biz');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
    groups: [],
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
      this.setData({ authState: 'signed-out', groups: [] });
      return;
    }
    const info = await staffUtil.me();
    if (!info || (info.role !== 'store_admin' && info.role !== 'hq_admin')) {
      this.setData({ authState: 'denied', groups: [] });
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
    const storesRes = await getStores(true);
    const docsRes = await getDoctors();
    this.setData({ loading: false });
    if (docsRes.error) {
      wx.showToast({ title: docsRes.error.message, icon: 'none' });
      return;
    }
    const stores = storesRes.data || [];
    const doctors = docsRes.data || [];
    const groups = stores.map(function (s) {
      return {
        storeName: s.name,
        address: s.address || '',
        doctors: doctors.filter(function (d) { return d.store_id === s.id; }).map(function (d) {
          return {
            id: d.id,
            name: d.name,
            title: d.title || '',
            specialty: d.specialty || '',
            years: d.years || 0,
            rating: d.rating || '5.0',
            intro: d.intro || '',
          };
        }),
      };
    });
    this.setData({ groups: groups });
  },
}
);
