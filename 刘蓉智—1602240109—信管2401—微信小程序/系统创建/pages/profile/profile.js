// pages/profile/profile.js — 我的：登录 / 数据统计 / 功能入口 / 员工工作台
const { cloud } = require('../../utils/cloud');
const { todayStr } = require('../../utils/format');
const staff = require('../../utils/staff');

Page({
  data: {
    authState: 'checking',
    user: null,
    avatarText: '?',
    statAppts: 0,
    statUnpaid: 0,
    statRecords: 0,
    staffInfo: null,
    staffRoleText: '',
    staffCodeInput: '',
    staffBinding: false,
  },

  onShow() {
    this.restoreAuth();
  },

  async restoreAuth() {
    this.setData({ authState: 'checking' });
    let session = null;
    try {
      const res = await getApp().ensureSession();
      session = res && res.data ? res.data : null;
    } catch (e) {
      getApp().resetSession();
    }
    if (!session) {
      this.setData({ authState: 'signed-out', user: null, statAppts: 0, statUnpaid: 0, statRecords: 0, staffInfo: null });
      staff.unbind();
      return;
    }
    const user = session.user || null;
    const name = user && user.name ? user.name : '';
    this.setData({
      authState: 'signed-in',
      user: user,
      avatarText: name ? name.slice(0, 1) : '?',
    });
    this.loadStats();
    this.loadStaff();
  },

  // ---- 员工工作台 ----
  async loadStaff() {
    const info = await staff.me();
    this.setData({
      staffInfo: info,
      staffRoleText: info ? staff.ROLE_TEXT[info.role] || info.role : '',
    });
  },

  onStaffInput(e) {
    this.setData({ staffCodeInput: e.detail.value });
  },

  async onBindStaff() {
    const code = this.data.staffCodeInput;
    if (!code) {
      wx.showToast({ title: '请输入员工工号', icon: 'none' });
      return;
    }
    this.setData({ staffBinding: true });
    try {
      const info = await staff.bind(code);
      wx.showToast({ title: '已绑定：' + info.name, icon: 'success' });
      this.setData({ staffCodeInput: '' });
      this.loadStaff();
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '绑定失败', icon: 'none' });
    }
    this.setData({ staffBinding: false });
  },

  onUnbindStaff() {
    const that = this;
    wx.showModal({
      title: '解除绑定',
      content: '解除后将退出员工工作台，患者功能不受影响。',
      confirmColor: '#e8590c',
      success: function (r) {
        if (!r.confirm) return;
        staff.unbind();
        that.setData({ staffInfo: null, staffRoleText: '' });
        wx.showToast({ title: '已解绑', icon: 'success' });
      },
    });
  },

  goWorkbench() {
    const info = this.data.staffInfo;
    if (!info) return;
    const url = staff.homeOf(info.role);
    if (url) {
      wx.navigateTo({ url: url });
    }
  },

  async loadStats() {
    const today = todayStr();
    const apptRes = await cloud.database.from('clinic_appointments').select('id,status,visit_date').limit(100);
    const billRes = await cloud.database.from('clinic_bills').select('id,status').limit(100);
    const recRes = await cloud.database.from('clinic_records').select('id').limit(100);
    const appts = apptRes.data || [];
    const bills = billRes.data || [];
    const recs = recRes.data || [];
    this.setData({
      statAppts: appts.filter(function (a) { return a.status < 5 && a.visit_date >= today; }).length,
      statUnpaid: bills.filter(function (b) { return b.status === 0; }).length,
      statRecords: recs.length,
    });
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  async onSignOut() {
    await cloud.auth.signOut();
    getApp().resetSession();
    this.setData({ authState: 'signed-out', user: null });
  },

  goTab() {
    wx.switchTab({ url: '/pages/appointments/appointments' });
  },

  // ---- 账户与隐私：注销账户（匿名化） ----
  onCancelAccount() {
    const that = this;
    wx.showModal({
      title: '注销账户',
      content: '注销后您的姓名、手机号等个人标识信息将立即匿名化删除，健康卡号停用；历史病历依《医疗机构病历管理规定》匿名留存。此操作不可恢复，确定注销吗？',
      confirmText: '确认注销',
      cancelText: '再想想',
      confirmColor: '#e8590c',
      success: async function (r) {
        if (!r.confirm) return;
        try {
          const res = await cloud.database.rpc('clinic_patient_cancel', {});
          if (res.error) {
            wx.showToast({ title: res.error.message || '注销失败', icon: 'none' });
            return;
          }
          await cloud.auth.signOut();
          getApp().resetSession();
          that.setData({ authState: 'signed-out', user: null, staffInfo: null, staffRoleText: '' });
          staff.unbind();
          wx.showModal({
            title: '已注销',
            content: '您的个人标识信息已匿名化处理，历史病历依规匿名留存。感谢使用。',
            showCancel: false,
          });
        } catch (e) {
          wx.showToast({ title: (e && e.message) || '注销失败', icon: 'none' });
        }
      },
    });
  },

  onPrivacy() {
    wx.showModal({
      title: '隐私与合规说明',
      content: '本系统依据《个人信息保护法》处理个人信息：注册需同意《个人信息处理告知书》；患者数据仅本人可读写；工作人员增减、公告发布、预约确认等操作全程留痕；账户可自助注销并匿名化；医保甲类、乙类项目按政策比例报销并在账单中列明。',
      showCancel: false,
    });
  },
  goRegister() { wx.navigateTo({ url: '/pages/register/register' }); },
  goBills() { wx.navigateTo({ url: '/pages/bills/bills' }); },
  goRecords() { wx.navigateTo({ url: '/pages/records/records' }); },
  goQueue() { wx.navigateTo({ url: '/pages/queue/queue' }); },

  onCall() {
    wx.makePhoneCall({ phoneNumber: '02788880001' });
  },

  onAbout() {
    wx.showModal({
      title: '关于牙科门诊',
      content: '牙科门诊连锁（东湖店 / 光谷店）。开诊时间 09:00-18:00，地址：洪山区东湖路 88 号 / 江夏区光谷大道 66 号。本小程序为系统分析与设计课程演示项目。',
      showCancel: false,
    });
  },
}
);
