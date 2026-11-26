// packageDoctor/consult/consult.js — 完成就诊：病历 + 费用一键录入
const staffUtil = require('../../utils/staff');
const { toast, toastErr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

Page({
  data: {
    authState: 'checking',
    apptId: 0,
    patientName: '',
    form: {
      diagnosis: '',
      tooth: '',
      treatment: '',
      total: '',
      nextVisit: '',
    },
    minDate: '',
    submitting: false,
  },

  onLoad(query) {
    const t = new Date();
    const m = t.getMonth() + 1;
    const d = t.getDate();
    this.setData({
      apptId: Number(query.id || 0),
      patientName: decodeURIComponent(query.name || ''),
      minDate: t.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (d < 10 ? '0' + d : d),
    });
  },

  onShow() {
    this.checkAuth();
  },

  async checkAuth() {
    let session = null;
    try {
      const res = await getApp().ensureSession();
      session = res && res.data ? res.data : null;
    } catch (e) {
      getApp().resetSession();
    }
    if (!session) {
      this.setData({ authState: 'signed-out' });
      return;
    }
    const info = await staffUtil.me();
    this.setData({ authState: info && info.role === 'doctor' ? 'ready' : 'denied' });
  },

  onSignedIn() {
    getApp().resetSession();
    this.checkAuth();
  },

  goProfile() {
    wx.switchTab({ url: '/pages/profile/profile' });
  },

  onField(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ ['form.' + key]: e.detail.value });
  },

  onDate(e) {
    this.setData({ 'form.nextVisit': e.detail.value });
  },

  clearDate() {
    this.setData({ 'form.nextVisit': '' });
  },

  async onSubmit() {
    const f = this.data.form;
    if (!f.diagnosis.trim()) {
      toastErr('请填写诊断结果');
      return;
    }
    this.setData({ submitting: true });
    try {
      const res = await cloud.database.rpc('clinic_doctor_finish', {
        p_id: this.data.apptId,
        p_diagnosis: f.diagnosis.trim(),
        p_tooth: f.tooth.trim() || null,
        p_treatment: f.treatment.trim() || null,
        p_total: Number(f.total || 0),
        p_next_visit: f.nextVisit || null,
      });
      if (res.error) {
        toastErr(res.error.message);
        this.setData({ submitting: false });
        return;
      }
      const d = res.data || {};
      const total = Number(f.total || 0);
      const rate = Number(d.insuranceRate || 0);
      let extra = '';
      if (rate > 0 && d.insuranceCover > 0) {
        extra = '\n\n医保结算：' + (rate >= 1 ? '甲类项目' : '乙类项目') +
          '统筹报销 ¥' + Number(d.insuranceCover).toFixed(2) +
          '，患者自付 ¥' + Number(d.selfPay || 0).toFixed(2) + '。';
      } else {
        extra = '\n\n该项目为自费项目（如种植/正畸/修复），不纳入医保报销。';
      }
      wx.showModal({
        title: '就诊已完成',
        content: '病历已写入患者档案，缴费单 ' + (d.billNo || '') +
          '（¥' + total.toFixed(2).replace(/\.00$/, '') + '）已生成，等待患者支付。' + extra,
        showCancel: false,
        success: function () {
          wx.navigateBack();
        },
      });
    } catch (e) {
      toastErr((e && e.message) || '提交失败');
    }
    this.setData({ submitting: false });
  },
}
);
