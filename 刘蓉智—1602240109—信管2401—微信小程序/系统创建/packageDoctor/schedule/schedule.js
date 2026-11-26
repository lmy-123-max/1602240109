// packageDoctor/schedule/schedule.js — 我的排班：未来两周出诊安排
const staffUtil = require('../../utils/staff');
const { SLOTS, WEEKDAYS, todayStr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

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
    if (!info || info.role !== 'doctor') {
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
    const today = todayStr();
    const res = await cloud.database.from('clinic_schedules')
      .select('*')
      .eq('doctor_id', this.data.staffInfo.doctorId)
      .gte('work_date', today)
      .order('work_date')
      .order('time_slot')
      .limit(60);
    this.setData({ loading: false });
    if (res.error) {
      wx.showToast({ title: res.error.message, icon: 'none' });
      return;
    }
    const byDate = {};
    (res.data || []).forEach(function (s) {
      (byDate[s.work_date] = byDate[s.work_date] || []).push(s);
    });
    const groups = Object.keys(byDate).sort().map(function (date) {
      return {
        date: date,
        weekday: WEEKDAYS[new Date(date.replace(/-/g, '/')).getDay()],
        items: byDate[date].map(function (s) {
          return {
            id: s.id,
            slotText: SLOTS[s.time_slot] || '',
            quotaText: '已约 ' + (s.quota_used || 0) + '/' + (s.quota_total || 0),
            full: (s.quota_used || 0) >= (s.quota_total || 0),
            off: s.status !== 1,
          };
        }),
      };
    });
    this.setData({ groups: groups });
  },
}
);
