// packageAdmin/stores/stores.js — 门店信息：地址 / 电话 / 开诊时间
const staffUtil = require('../../utils/staff');
const { getStores } = require('../../utils/biz');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
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
    const res = await getStores(true);
    this.setData({ loading: false });
    if (res.error) {
      wx.showToast({ title: res.error.message, icon: 'none' });
      return;
    }
    const list = (res.data || []).map(function (s) {
      return {
        id: s.id,
        name: s.name,
        code: s.store_code || '',
        address: s.address || '',
        phone: s.phone || '',
        openTime: s.open_time || '',
        open: s.status === 1,
      };
    });
    this.setData({ list: list });
  },

  onCall(e) {
    const phone = e.currentTarget.dataset.phone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },
}
);
