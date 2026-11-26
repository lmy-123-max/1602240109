// utils/cloud.js — WorkBuddy 云服务客户端（小程序专用 /miniprogram 入口）
// endpoint 与 publishableKey 来自云服务开通时返回的 publicConfig，客户端仅持有公开配置。
const { createMiniProgramWorkBuddyCloud } = require('@tencent-ai/workbuddy-cloud-sdk/miniprogram');
const { createDiagnosticWx } = require('./workbuddy-cloud-diagnostics');

const publicConfig = {
  endpoint: 'https://mp-api.app.workbuddy.host',
  publishableKey: 'wbpk_93EkTGHWlMlOyoQq7I3H9E_jpwF9kn27C1QaUnmMUgHtMQspUS2TrhU',
  workBuddyOAuthClientId: 'cb_jq6QyEPkOkJXPcpz0JxS_wbapp_93EkTGHWlMlOyoQq7I3H9E',
};

const cloud = createMiniProgramWorkBuddyCloud({
  endpoint: publicConfig.endpoint,
  publishableKey: publicConfig.publishableKey,
  workBuddyOAuthClientId: publicConfig.workBuddyOAuthClientId,
  wx: createDiagnosticWx(wx),
});

module.exports = { cloud, publicConfig };
