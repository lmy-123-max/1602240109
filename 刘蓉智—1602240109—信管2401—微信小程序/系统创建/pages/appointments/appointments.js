// pages/appointments/appointments.js — 我的预约：列表 / 签到 / 取消 / 候诊
const { cloud } = require('../../utils/cloud');
const { APPT_STATUS, SLOTS, todayStr, toast, toastErr } = require('../../utils/format');

Page({
  data: {
    authState: 'checking',
    tabs: [
      { key: 'all', text: '全部' },
      { key: 'wait', text: '待就诊' },
      { key: 'finish', text: '已完成' },
      { key: 'cancel', text: '已取消' },
    ],
    curTab: 'all',
    curTabText: '',
    loading: false,
    list: [],
    all: [],
  },

  onLoad() {
    this.setData({ curTabText: this.data.tabs[0].text });
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
      this.setData({ authState: 'signed-out', all: [], list: [] });
      return;
    }
    this.setData({ authState: 'signed-in' });
    this.load();
  },

  onSignedIn() {
    getApp().resetSession();
    this.restoreAuth();
  },

  async load() {
    this.setData({ loading: true });
    const res = await cloud.database.from('clinic_appointments')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(50);
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const today = todayStr();
    const rows = (res.data || []).map(function (a) {
      const st = APPT_STATUS[a.status] || { text: '未知', cls: 'tag-gray' };
      return {
        id: a.id,
        appointmentNo: a.appointment_no,
        patientName: a.patient_name,
        doctorName: a.doctor_name,
        doctorTitle: a.doctor_title || '',
        storeName: a.store_name,
        visitDate: a.visit_date,
        slotText: SLOTS[a.time_slot] || '',
        status: a.status,
        statusText: st.text,
        statusCls: st.cls,
        canCheckin: a.status === 1 && a.visit_date === today,
      };
    });
    this.setData({ all: rows });
    this.applyFilter();
  },

  onTab(e) {
    const key = e.currentTarget.dataset.key;
    const tab = this.data.tabs.filter(function (t) { return t.key === key; })[0];
    this.setData({ curTab: key, curTabText: tab ? tab.text : '' });
    this.applyFilter();
  },

  applyFilter() {
    const tab = this.data.curTab;
    const list = this.data.all.filter(function (a) {
      if (tab === 'wait') return a.status === 0 || a.status === 1 || a.status === 2 || a.status === 3;
      if (tab === 'finish') return a.status === 4;
      if (tab === 'cancel') return a.status === 5;
      return true;
    });
    this.setData({ list: list });
  },

  onCheckin(e) {
    const id = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '到院签到',
      content: '确认已到院并签到取号？签到后进入叫号队列。',
      success: function (r) {
        if (!r.confirm) return;
        cloud.database.from('clinic_appointments').update({ status: 2 }).eq('id', id).select().then(function (res) {
          const affected = Array.isArray(res.data) ? res.data : [];
          if (!affected.length) {
            toastErr('签到失败，请稍后重试');
            return;
          }
          toast('签到成功，已进入候诊队列');
          wx.navigateTo({ url: '/pages/queue/queue?id=' + id });
        });
      },
    });
  },

  onCancel(e) {
    const id = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '取消预约',
      content: '距就诊不足 2 小时将收取 50% 挂号费违约金，确认取消本次预约？',
      confirmColor: '#e8590c',
      success: function (r) {
        if (!r.confirm) return;
        cloud.database.from('clinic_appointments').update({ status: 5 }).eq('id', id).select().then(function (res) {
          const affected = Array.isArray(res.data) ? res.data : [];
          if (!affected.length) {
            toastErr('取消失败，请稍后重试');
            return;
          }
          toast('已取消预约');
        });
      },
    });
  },

  goQueue(e) {
    wx.navigateTo({ url: '/pages/queue/queue?id=' + e.currentTarget.dataset.id });
  },

  goRecords() {
    wx.navigateTo({ url: '/pages/records/records' });
  },

  goBooking() {
    wx.switchTab({ url: '/pages/booking/booking' });
  },
}
);
