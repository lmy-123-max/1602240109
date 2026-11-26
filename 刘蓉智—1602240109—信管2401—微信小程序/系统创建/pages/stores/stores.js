// pages/stores/stores.js — 门诊门店：地址 / 电话 / 开诊时间（可拨打）
const { getStores } = require('../../utils/biz');

Page({
  data: {
    loading: true,
    loadError: '',
    list: [],
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(function () { wx.stopPullDownRefresh(); });
  },

  async load() {
    this.setData({ loading: true, loadError: '' });
    try {
      const res = await getStores(true);
      if (res.error) {
        throw res.error;
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
      this.setData({ loading: false, list: list });
    } catch (e) {
      this.setData({
        loading: false,
        loadError: (e && e.message) || '加载失败，请检查网络后重试',
      });
    }
  },

  onCall(e) {
    const phone = e.currentTarget.dataset.phone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },

  openMap(e) {
    const idx = Number(e.currentTarget.dataset.index);
    const s = this.data.list[idx];
    if (!s) return;
    wx.openLocation({
      latitude: 30.5,
      longitude: 114.35,
      name: s.name,
      address: s.address,
      scale: 15,
    });
  },
}
);
