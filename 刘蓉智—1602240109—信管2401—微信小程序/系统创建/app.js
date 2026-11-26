// app.js — 云服务客户端在 utils/cloud.js 中初始化（全应用唯一实例）
const { cloud } = require('./utils/cloud');

App({
  onLaunch() {
    // 预取会话，首页与各页在 onShow 时通过 ensureSession 复用
    this.ensureSession();
  },

  // 会话缓存：登录/退出后调用 resetSession() 重置
  ensureSession() {
    if (!this.sessionPromise) {
      this.sessionPromise = cloud.auth.getSession();
    }
    return this.sessionPromise;
  },

  resetSession() {
    this.sessionPromise = null;
  },

  globalData: {
    user: null,
    stores: null, // 首页加载后缓存，供其他页面复用
  },
});
