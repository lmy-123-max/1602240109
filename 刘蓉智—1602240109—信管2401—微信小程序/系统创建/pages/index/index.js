// pages/index/index.js — 首页：门店切换 / 公告 / 出诊医生 / 快捷入口
const { getStores, getDoctors, getNotices } = require('../../utils/biz');
const { fmtDate, todayStr, weekdayOf } = require('../../utils/format');

Page({
  data: {
    loading: true,
    loadError: '',
    stores: [],
    curStore: {},
    doctors: [],
    notices: [],
    todayText: '',
  },

  onLoad() {
    const d = todayStr();
    this.setData({ todayText: fmtDate(d) + ' ' + weekdayOf(d) });
    this.reload();
  },

  onShow() {
    // 公共数据无需登录；其他页签的数据刷新由各自页面负责
  },

  async reload() {
    this.setData({ loading: true, loadError: '' });
    try {
      const storesRes = await getStores(true);
      const stores = storesRes.data || [];
      if (storesRes.error) throw storesRes.error;
      if (!stores.length) {
        this.setData({ loading: false, stores: [], doctors: [], notices: [] });
        return;
      }
      const cur = this.data.curStore && this.data.curStore.id
        ? stores.filter(function (s) { return s.id === this.data.curStore.id; }, this)[0] || stores[0]
        : stores[0];
      this.setData({ stores: stores, curStore: cur });
      await this.loadStoreData();
      const noticesRes = await getNotices();
      this.setData({ notices: noticesRes.data || [], loading: false });
    } catch (e) {
      this.setData({
        loading: false,
        loadError: (e && e.message) || '加载失败，请检查网络后重试',
      });
    }
  },

  async loadStoreData() {
    const res = await getDoctors(this.data.curStore.id);
    if (res.error) throw res.error;
    this.setData({ doctors: res.data || [] });
  },

  onStoreTap(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (id === this.data.curStore.id) return;
    const cur = this.data.stores.filter(function (s) { return s.id === id; })[0];
    if (!cur) return;
    this.setData({ curStore: cur, doctors: [] });
    this.loadStoreData();
  },

  onDoctorBook(e) {
    const id = e.currentTarget.dataset.id;
    wx.setStorageSync('bookingDoctorId', id);
    wx.switchTab({ url: '/pages/booking/booking' });
  },

  goBooking() { wx.switchTab({ url: '/pages/booking/booking' }); },
  goQueue() { wx.navigateTo({ url: '/pages/queue/queue' }); },
  goBills() { wx.navigateTo({ url: '/pages/bills/bills' }); },
  goRecords() { wx.navigateTo({ url: '/pages/records/records' }); },
  goStores() { wx.navigateTo({ url: '/pages/stores/stores' }); },
}
);
