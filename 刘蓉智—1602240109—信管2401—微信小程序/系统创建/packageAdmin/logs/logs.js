// packageAdmin/logs/logs.js — 操作留痕：人员增减 / 公告发布 / 预约确认 / 账户注销 全程记录
const staffUtil = require('../../utils/staff');
const { cloud } = require('../../utils/cloud');

const ACTION_TEXT = {
  ADD: { text: '新增', cls: 'tag-green' },
  OFF: { text: '停用/下架', cls: 'tag-gray' },
  STATUS: { text: '预约确认', cls: 'tag-blue' },
  CANCEL: { text: '注销', cls: 'tag-orange' },
  REFUND: { text: '退款', cls: 'tag-orange' },
};

Page({
  data: {
    authState: 'checking',
    loading: true,
    curFilter: 'ALL',
    all: [],
    list: [],
    statTotal: 0,
    statAdd: 0,
    statOff: 0,
    statCancel: 0,
    statStatus: 0,
    keyword: '',
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
      this.setData({ authState: 'signed-out', loading: false });
      return;
    }
    const info = await staffUtil.me();
    if (!info || (info.role !== 'store_admin' && info.role !== 'hq_admin')) {
      this.setData({ authState: 'denied', loading: false });
      return;
    }
    this.setData({ authState: 'ready' });
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
    const res = await cloud.database.rpc('clinic_admin_op_logs', { p_limit: 50 });
    this.setData({ loading: false });
    if (res.error) {
      wx.showToast({ title: res.error.message || '加载失败', icon: 'none' });
      return;
    }
    const d = res.data || {};
    const items = (d.items || []).map(function (l) {
      const a = ACTION_TEXT[l.action] || { text: l.action, cls: 'tag-gray' };
      return {
        id: l.id,
        staffCode: l.staffCode,
        staffName: l.staffName,
        operator: (l.staffCode || '') === 'SELF' ? '患者本人' : l.staffName + '（' + l.staffCode + '）',
        roleText: l.role === 'hq_admin' ? '总部管理员' : l.role === 'store_admin' ? '门店管理员' : l.role === 'patient' ? '患者' : '工作人员',
        actionText: a.text,
        actionCls: a.cls,
        targetType: l.targetType,
        targetDesc: l.targetDesc,
        detail: l.detail || '',
        timeText: l.opTime ? String(l.opTime).replace('T', ' ').slice(5, 16) : '',
      };
    });
    this.setData({
      all: items,
      statTotal: d.total || 0,
      statAdd: d.addCount || 0,
      statOff: d.offCount || 0,
      statCancel: d.cancelCount || 0,
      statStatus: d.statusCount || 0,
    });
    this.applyFilter();
  },

  onFilter(e) {
    this.setData({ curFilter: e.currentTarget.dataset.key });
    this.applyFilter();
  },

  onKeyword(e) {
    this.setData({ keyword: e.detail.value });
    this.applyFilter();
  },

  applyFilter() {
    const f = this.data.curFilter;
    const kw = (this.data.keyword || '').trim().toLowerCase();
    const list = (this.data.all || []).filter(function (l) {
      if (f !== 'ALL' && l.targetType !== f) return false;
      if (kw) {
        const hay = (l.operator + ' ' + l.targetDesc + ' ' + l.detail).toLowerCase();
        if (hay.indexOf(kw) < 0) return false;
      }
      return true;
    });
    this.setData({ list: list });
  },
}
);
