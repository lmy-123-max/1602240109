// pages/bills/bills.js — 门诊缴费：待支付 / 已支付，模拟渠道支付
const { cloud } = require('../../utils/cloud');
const { money, fmtDate, toast, toastErr } = require('../../utils/format');

function mapBill(b) {
  return {
    id: b.id,
    billNo: b.bill_no,
    itemsSummary: b.items_summary || '诊疗费用',
    storeName: b.store_name || '',
    visitDateText: b.visit_date ? '就诊日 ' + fmtDate(b.visit_date) : fmtDate(b.created_at),
    totalText: money(b.total_amount),
    discountText: money(b.discount),
    discount: Number(b.discount || 0),
    payableText: money(b.payable),
    payable: Number(b.payable || 0),
    insuranceCoverText: money(b.insurance_cover || 0),
    insuranceCover: Number(b.insurance_cover || 0),
    selfPayText: money(b.self_pay || 0),
    selfPay: Number(b.self_pay || 0),
    status: b.status,
    statusText: b.status === 1 ? '已支付' : '待支付',
    payChannel: b.pay_channel || '',
  };
}

Page({
  data: {
    authState: 'checking',
    curTab: 'unpaid',
    curTabText: '待支付',
    loading: false,
    list: [],
    unpaidTotalText: '0',
    unpaidCount: 0,
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
      this.setData({ authState: 'signed-out', list: [], unpaidCount: 0, unpaidTotalText: '0' });
      return;
    }
    this.setData({ authState: 'signed-in' });
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    const res = await cloud.database.from('clinic_bills')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(50);
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const bills = (res.data || []).map(mapBill);
    const unpaid = bills.filter(function (b) { return b.status === 0; });
    let total = 0;
    unpaid.forEach(function (b) { total += b.payable; });
    this.setData({
      all: bills,
      unpaidTotalText: money(total),
      unpaidCount: unpaid.length,
    });
    this.applyFilter();
  },

  onTab(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ curTab: key, curTabText: key === 'unpaid' ? '待支付' : '已支付' });
    this.applyFilter();
  },

  applyFilter() {
    const tab = this.data.curTab;
    const list = (this.data.all || []).filter(function (b) {
      return tab === 'unpaid' ? b.status === 0 : b.status === 1;
    });
    this.setData({ list: list });
  },

  onPay(e) {
    const id = Number(e.currentTarget.dataset.id);
    const that = this;
    wx.showActionSheet({
      itemList: ['微信支付', '支付宝', '医保个人账户'],
      success: function (r) {
        const channels = ['微信支付', '支付宝', '医保个人账户'];
        const channel = channels[r.tapIndex];
        const tip = channel === '医保个人账户'
          ? '医保结算提示：甲类项目 100% 纳入统筹报销、乙类项目报销 70%，报销明细已在账单中列示。'
          : '选择' + channel + '支付。甲类/乙类项目的统筹报销额已在账单中抵扣，仅支付自付部分。';
        wx.showModal({
          title: '医保结算说明',
          content: tip,
          showCancel: false,
          confirmText: '继续支付',
          success: function () {
            that.doPay(id, channel);
          },
        });
      },
    });
  },

  async doPay(id, channel) {
    const res = await cloud.database.from('clinic_bills')
      .update({ status: 1, pay_channel: channel, pay_time: new Date().toISOString() })
      .eq('id', id)
      .select();
    const affected = Array.isArray(res.data) ? res.data : [];
    if (!affected.length) {
      toastErr('支付未完成，请稍后重试');
      return;
    }
    toast('支付成功');
    this.load();
  },
}
);
