// packageAdmin/staff/staff.js — 人员账号：管理类员工列表 / 增设工作人员 / 离职移除
// 权限由云端 RPC 保证：门店管理员仅可为本门店增设门店工作人员；总部管理员可增设两级管理员
const staffUtil = require('../../utils/staff');
const { toast, toastErr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');
const { getStores } = require('../../utils/biz');

Page({
  data: {
    authState: 'checking',
    staffInfo: null,          // 当前登录的管理员身份
    loading: true,
    list: [],                 // 管理类员工（门店管理员/总部管理员）
    name: '',
    roleIndex: 0,
    roles: [{ key: 'store_admin', label: '门店工作人员' }, { key: 'hq_admin', label: '总部管理员' }],
    roleIndexHq: 0,
    rolesHq: [{ key: 'store_admin', label: '门店管理员' }, { key: 'hq_admin', label: '总部管理员' }],
    stores: [],
    storeIndex: -1,
    submitting: false,
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
    if (!this.data.stores.length) {
      const res = await getStores();
      if (!res.error) {
        this.setData({ stores: res.data.map(function (s) { return { id: s.id, name: s.name }; }) });
      }
    }
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
    const res = await cloud.database.rpc('clinic_admin_staff_list', {});
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const storeMap = {};
    this.data.stores.forEach(function (s) { storeMap[s.id] = s.name; });
    const rows = (res.data || []).map(function (s) {
      return {
        staffCode: s.staffCode,
        name: s.name,
        role: s.role,
        roleText: s.role === 'hq_admin' ? '总部管理员' : '门店管理员',
        storeName: s.role === 'hq_admin' ? '全部门店' : (storeMap[s.storeId] || '门店#' + s.storeId),
        bound: !!s.boundUid,
      };
    });
    const me = this.data.staffInfo ? this.data.staffInfo.staffCode : '';
    rows.forEach(function (r) { r.mine = r.staffCode === me; });
    this.setData({ list: rows });
  },

  onName(e) { this.setData({ name: e.detail.value }); },
  onRole(e) {
    const idx = Number(e.detail.value);
    if (this.data.staffInfo && this.data.staffInfo.role === 'hq_admin') {
      this.setData({ roleIndexHq: idx });
    } else {
      this.setData({ roleIndex: idx });
    }
  },
  onStore(e) { this.setData({ storeIndex: Number(e.detail.value) }); },

  // ---- 增设工作人员：云端自动分配工号（S2xx 门店 / H3xx 总部） ----
  async onAdd() {
    const name = this.data.name.trim();
    if (!name) {
      toastErr('请填写姓名');
      return;
    }
    const isHq = this.data.staffInfo.role === 'hq_admin';
    let role, storeId = null;
    if (isHq) {
      role = this.data.rolesHq[this.data.roleIndexHq].key;
      if (role === 'store_admin') {
        if (this.data.storeIndex < 0) {
          toastErr('请选择归属门店');
          return;
        }
        storeId = this.data.stores[this.data.storeIndex].id;
      }
    } else {
      role = this.data.roles[this.data.roleIndex].key;
    }
    this.setData({ submitting: true });
    try {
      const res = await cloud.database.rpc('clinic_admin_staff_add', {
        p_name: name,
        p_role: role,
        p_store_id: storeId,
        p_title: null,
      });
      if (res.error) {
        toastErr(res.error.message);
      } else {
        const that = this;
        wx.showModal({
          title: '人员已新增',
          content: '姓名「' + res.data.name + '」工号 ' + res.data.staffCode + ' 已分配，该员工在「我的」页绑定此工号即可进入管理工作台。',
          showCancel: false,
          confirmColor: '#1a7ce0',
          success: function () {
            that.setData({ name: '' });
            that.load();
          },
        });
      }
    } catch (e) {
      toastErr((e && e.message) || '新增失败');
    }
    this.setData({ submitting: false });
  },

  // ---- 离职移除：删除工号与其绑定，历史业务数据保留 ----
  onOff(e) {
    const code = e.currentTarget.dataset.code;
    const name = e.currentTarget.dataset.name;
    if (this.data.list.filter(function (r) { return r.mine; }).length && code === this.data.staffInfo.staffCode) {
      toastErr('不能移除当前登录的账号');
      return;
    }
    const that = this;
    wx.showModal({
      title: '确认离职移除',
      content: '移除后「' + name + '」（' + code + '）将无法再绑定工作台，历史业务数据全部保留。',
      confirmColor: '#e8590c',
      success: function (r) {
        if (!r.confirm) return;
        that.off(code);
      },
    });
  },

  async off(code) {
    const res = await cloud.database.rpc('clinic_admin_staff_off', { p_code: code });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    toast('已移除');
    this.load();
  },
}
);
