// pages/register/register.js — 注册 / 入驻：患者注册建档、牙医入驻（工号自动分配）
const { cloud } = require('../../utils/cloud');
const { toast, toastErr } = require('../../utils/format');
const { getStores } = require('../../utils/biz');
const account = require('../../utils/account');

const TITLES = ['住院医师', '主治医师', '副主任医师', '主任医师'];

Page({
  data: {
    authState: 'checking',
    tab: 'patient',           // patient / doctor
    patient: null,            // 已注册的就诊人档案
    name: '',
    phone: '',
    dName: '',
    titleIndex: 1,
    titles: TITLES,
    specialty: '',
    stores: [],
    storeIndex: -1,
    years: '',
    consent: false,           // 《个人信息处理告知书》是否已勾选
    showConsent: false,       // 告知书全文弹层
    submitting: false,
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
      this.setData({ authState: 'signed-out', patient: null });
      return;
    }
    this.setData({ authState: 'signed-in' });
    this.loadStores();
    this.loadPatient();
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  async loadStores() {
    const res = await getStores();
    if (!res.error && res.data.length) {
      this.setData({
        stores: res.data.map(function (s) { return { id: s.id, name: s.name }; }),
      });
    }
  },

  async loadPatient() {
    const p = await account.patientMe();
    this.setData({
      patient: p,
      name: p ? p.name || '' : this.data.name,
      phone: p ? p.phone || '' : this.data.phone,
    });
  },

  onTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
  },

  onName(e) { this.setData({ name: e.detail.value }); },
  onPhone(e) { this.setData({ phone: e.detail.value }); },
  onDName(e) { this.setData({ dName: e.detail.value }); },
  onTitle(e) { this.setData({ titleIndex: Number(e.detail.value) }); },
  onSpecialty(e) { this.setData({ specialty: e.detail.value }); },
  onStore(e) { this.setData({ storeIndex: Number(e.detail.value) }); },
  onYears(e) { this.setData({ years: e.detail.value }); },

  // ---- 个人信息处理告知书：合规同意 ----
  onConsent() { this.setData({ consent: !this.data.consent }); },
  openConsent() { this.setData({ showConsent: true }); },
  closeConsent() { this.setData({ showConsent: false }); },

  // ---- 患者注册：一人一份档案，重复提交即更新 ----
  async onPatientRegister() {
    const name = this.data.name.trim();
    if (!name) {
      toastErr('请填写姓名');
      return;
    }
    if (!this.data.consent) {
      toastErr('请先阅读并勾选《个人信息处理告知书》');
      return;
    }
    this.setData({ submitting: true });
    try {
      const p = await account.patientRegister(name, this.data.phone.trim());
      this.setData({ patient: p });
      toast('注册成功，健康卡 ' + p.healthCardNo);
    } catch (e) {
      toastErr((e && e.message) || '注册失败');
    }
    this.setData({ submitting: false });
  },

  // ---- 牙医入驻：clinic_doctors + 员工档案 + 工号自动分配 + 绑定当前账号 ----
  async onDoctorRegister() {
    const name = this.data.dName.trim();
    if (!name) {
      toastErr('请填写姓名');
      return;
    }
    if (this.data.storeIndex < 0) {
      toastErr('请选择归属门店');
      return;
    }
    if (!this.data.consent) {
      toastErr('请先阅读并勾选《个人信息处理告知书》');
      return;
    }
    const store = this.data.stores[this.data.storeIndex];
    this.setData({ submitting: true });
    try {
      const r = await account.doctorRegister(
        name,
        TITLES[this.data.titleIndex],
        this.data.specialty.trim() || '全科',
        store.id,
        this.data.years.trim()
      );
      this.setData({ dName: '', specialty: '', years: '' });
      const that = this;
      wx.showModal({
        title: '入驻成功',
        content: '工号 ' + r.staffCode + ' 已自动分配并绑定当前账号，可立即进入医生工作台。',
        confirmText: '进入工作台',
        cancelText: '知道了',
        confirmColor: '#1a7ce0',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/packageDoctor/workbench/workbench' });
          } else {
            that.onShow();
          }
        },
      });
    } catch (e) {
      toastErr((e && e.message) || '入驻失败');
    }
    this.setData({ submitting: false });
  },
}
);
