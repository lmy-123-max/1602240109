// pages/records/records.js — 我的病历 + 复诊提醒 + 价目表
const { cloud } = require('../../utils/cloud');
const { getItems } = require('../../utils/biz');
const { money, fmtDate, todayStr, toastErr } = require('../../utils/format');

Page({
  data: {
    authState: 'checking',
    loading: false,
    records: [],
    items: [],
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
      this.setData({ authState: 'signed-out', records: [], items: [] });
      return;
    }
    this.setData({ authState: 'signed-in' });
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    const recRes = await cloud.database.from('clinic_records')
      .select('*')
      .order('visit_date', { ascending: false })
      .limit(50);
    const itemRes = await getItems();
    this.setData({ loading: false });
    if (recRes.error) {
      toastErr(recRes.error.message);
    }
    const today = todayStr();
    const records = (recRes.data || []).map(function (r) {
      const next = r.next_visit ? fmtDate(r.next_visit) : '';
      return {
        id: r.id,
        visitDate: fmtDate(r.visit_date),
        doctorName: r.doctor_name,
        doctorTitle: r.doctor_title || '',
        diagnosis: r.diagnosis || '-',
        toothPosition: r.tooth_position || '',
        treatment: r.treatment || '',
        totalText: money(r.total),
        storeName: r.store_name || '',
        hasRecall: !!next && next >= today,
        recallText: next,
      };
    });
    const items = (itemRes.data || []).map(function (it) {
      return {
        id: it.id,
        name: it.name,
        category: it.category || '',
        unit: it.unit || '次',
        priceText: money(it.price),
      };
    });
    this.setData({ records: records, items: items });
  },
}
);
