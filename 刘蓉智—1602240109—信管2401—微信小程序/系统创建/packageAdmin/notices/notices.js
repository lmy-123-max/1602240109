// packageAdmin/notices/notices.js — 公告管理：发布 / 上架 / 下架
const staffUtil = require('../../utils/staff');
const { fmtDate, toast, toastErr } = require('../../utils/format');
const { cloud } = require('../../utils/cloud');

const TAGS = ['公告', '活动', '提醒'];

Page({
  data: {
    authState: 'checking',
    staffInfo: null,
    loading: true,
    list: [],
    tags: TAGS,
    tagIndex: 0,
    title: '',
    content: '',
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
    const res = await cloud.database.from('clinic_notices')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(30);
    this.setData({ loading: false });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    const rows = (res.data || []).map(function (n) {
      return {
        id: n.id,
        title: n.title,
        content: n.content || '',
        tag: n.tag || '公告',
        on: (n.status === undefined ? true : n.status === 1),
        date: fmtDate(n.created_at),
      };
    });
    this.setData({ list: rows });
  },

  onPullDownRefresh() {
    this.load().then(function () { wx.stopPullDownRefresh(); });
  },

  onTitle(e) { this.setData({ title: e.detail.value }); },
  onContent(e) { this.setData({ content: e.detail.value }); },
  onTag(e) { this.setData({ tagIndex: Number(e.detail.value) }); },

  async onPublish() {
    const title = this.data.title.trim();
    if (!title) {
      toastErr('请填写公告标题');
      return;
    }
    this.setData({ submitting: true });
    try {
      const res = await cloud.database.rpc('clinic_admin_notice_add', {
        p_title: title,
        p_content: this.data.content.trim() || null,
        p_tag: TAGS[this.data.tagIndex],
      });
      if (res.error) {
        toastErr(res.error.message);
      } else {
        toast('公告已发布');
        this.setData({ title: '', content: '' });
        this.load();
      }
    } catch (e) {
      toastErr((e && e.message) || '发布失败');
    }
    this.setData({ submitting: false });
  },

  onToggle(e) {
    const id = Number(e.currentTarget.dataset.id);
    const on = e.currentTarget.dataset.on;
    const that = this;
    wx.showModal({
      title: on ? '下架公告' : '重新上架',
      content: on ? '下架后患者端将不再显示该公告。' : '上架后患者端首页将重新展示。',
      success: function (r) {
        if (!r.confirm) return;
        that.toggle(id, on ? 0 : 1, on ? '已下架' : '已上架');
      },
    });
  },

  async toggle(id, status, okText) {
    const res = await cloud.database.rpc('clinic_admin_notice_set', { p_id: id, p_status: status });
    if (res.error) {
      toastErr(res.error.message);
      return;
    }
    toast(okText);
    this.load();
  },
}
);
