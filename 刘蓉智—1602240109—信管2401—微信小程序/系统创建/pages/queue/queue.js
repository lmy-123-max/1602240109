// pages/queue/queue.js — 候诊进度：排队序号 / 前方人数 / 自动刷新
const { cloud } = require('../../utils/cloud');
const { SLOTS, todayStr, toast, toastErr } = require('../../utils/format');

Page({
  data: {
    authState: 'checking',
    loading: true,
    list: [],
    pickerNames: [],
    pickerIndex: 0,
    cur: null,
    queue: { ahead: 0, serving: 0, waitingTotal: 0, mySeq: 0 },
  },

  onLoad(query) {
    this.fixedId = query && query.id ? Number(query.id) : null;
  },

  onShow() {
    this.restoreAuth();
  },

  onHide() {
    this.stopTimer();
  },

  onUnload() {
    this.stopTimer();
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
      this.setData({ authState: 'signed-out', loading: false, list: [] });
      this.stopTimer();
      return;
    }
    this.setData({ authState: 'signed-in' });
    await this.load();
    this.startTimer();
  },

  async load() {
    this.setData({ loading: true });
    const res = await cloud.database.from('clinic_appointments')
      .select('*')
      .eq('visit_date', todayStr())
      .in('status', [1, 2, 3])
      .order('id')
      .limit(20);
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const rows = (res.data || []).map(function (a) {
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
      };
    });
    const names = rows.map(function (r) { return r.patientName + ' · ' + r.doctorName; });
    let idx = 0;
    if (this.fixedId) {
      const found = rows.map(function (r) { return r.id; }).indexOf(this.fixedId);
      if (found >= 0) idx = found;
      this.fixedId = null;
    } else if (this.data.pickerIndex < names.length) {
      idx = this.data.pickerIndex;
    }
    this.setData({ list: rows, pickerNames: names, pickerIndex: idx });
    if (rows.length) {
      this.setData({ cur: rows[idx] });
      this.refreshStatus();
    } else {
      this.setData({ cur: null });
    }
  },

  async refreshStatus() {
    const cur = this.data.cur;
    if (!cur) return;
    const res = await cloud.database.rpc('clinic_queue_status', { p_appointment_id: cur.id });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const q = res.data || {};
    this.setData({
      'queue.ahead': q.ahead || 0,
      'queue.serving': q.serving || 0,
      'queue.waitingTotal': q.waitingTotal || 0,
      'queue.mySeq': (q.ahead || 0) + 1,
    });
  },

  onPick(e) {
    const idx = Number(e.detail.value);
    const cur = this.data.list[idx];
    this.setData({ pickerIndex: idx, cur: cur });
    this.refreshStatus();
  },

  onCheckin() {
    const cur = this.data.cur;
    if (!cur) return;
    const that = this;
    cloud.database.from('clinic_appointments').update({ status: 2 }).eq('id', cur.id).select().then(function (res) {
      const affected = Array.isArray(res.data) ? res.data : [];
      if (!affected.length) {
        toastErr('签到失败，请稍后重试');
        return;
      }
      toast('签到成功');
      that.load();
    });
  },

  startTimer() {
    this.stopTimer();
    const that = this;
    this.timer = setInterval(function () {
      if (that.data.cur && that.data.cur.status === 2) {
        that.refreshStatus();
      }
    }, 15000);
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  },
}
);
