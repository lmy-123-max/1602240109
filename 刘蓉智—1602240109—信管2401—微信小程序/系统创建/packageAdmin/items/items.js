// packageAdmin/items/items.js — 价目表：治疗项目与定价（按分类分组）
const staffUtil = require('../../utils/staff');
const { money } = require('../../utils/format');
const { getItems } = require('../../utils/biz');

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
    const res = await getItems();
    this.setData({ loading: false });
    if (res.error) {
      wx.showToast({ title: res.error.message, icon: 'none' });
      return;
    }
    const items = res.data || [];
    const cats = [];
    items.forEach(function (it) {
      if (cats.indexOf(it.category || '其他') < 0) {
        cats.push(it.category || '其他');
      }
    });
    const groups = cats.map(function (c) {
      return {
        category: c,
        items: items.filter(function (it) { return (it.category || '其他') === c; }).map(function (it) {
          const insType = it.insurance_type || '自费';
          return {
            id: it.id,
            code: it.item_code || '',
            name: it.name,
            unit: it.unit || '次',
            priceText: money(it.price),
            costText: money(it.material_cost),
            insText: insType,
            insCls: insType === '甲类' ? 'a' : insType === '乙类' ? 'b' : 'c',
          };
        }),
      };
    });
    this.setData({ groups: groups });
  },
}
);
