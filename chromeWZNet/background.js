// 常量配置
const CONFIG = {
  LOGIN_URL: 'http://172.16.8.22:801/eportal/?c=ACSetting&a=Login',
  LOGOUT_URL: 'http://172.16.8.22/eportal/?c=ACSetting&a=Logout&ver=1.0',
  CHECK_URL: 'http://172.16.8.22/',
  CHECK_INTERVAL: 5, // 检测间隔（分钟）
  RETRY_DELAY: 30 // 重试延迟（秒）
};

// 监听扩展安装事件
chrome.runtime.onInstalled.addListener(() => {
  console.log('校园网自动登录扩展已安装');

  // 创建定时检测闹钟
  chrome.alarms.create('networkCheck', {
    periodInMinutes: CONFIG.CHECK_INTERVAL
  });

  // 设置默认配置
  chrome.storage.local.get(['settings'], (result) => {
    if (!result.settings) {
      chrome.storage.local.set({
        settings: {
          autoLogin: true,
          autoReconnect: true,
          checkInterval: CONFIG.CHECK_INTERVAL,
          showNotifications: true
        }
      });
    }
  });
});

// 监听闹钟事件
chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === 'networkCheck') {
    const settings = await getSettings();
    if (settings.autoReconnect) {
      await checkAndReconnect();
    }
  }
});

// 监听浏览器启动事件
chrome.runtime.onStartup.addListener(async () => {
  console.log('浏览器启动，检查网络状态');
  const settings = await getSettings();
  if (settings.autoLogin) {
    // 延迟几秒等待网络就绪
    setTimeout(async () => {
      await checkAndReconnect();
    }, 5000);
  }
});

// 获取设置
async function getSettings() {
  const result = await chrome.storage.local.get(['settings']);
  return result.settings || {
    autoLogin: true,
    autoReconnect: true,
    checkInterval: CONFIG.CHECK_INTERVAL,
    showNotifications: true
  };
}

// 检测网络并重连
async function checkAndReconnect() {
  try {
    // 先检测是否已连接外网
    const isOnline = await checkInternetConnection();

    if (isOnline) {
      console.log('网络已连接，无需操作');
      return;
    }

    // 未连接，尝试登录
    console.log('检测到未连接，尝试自动登录');
    await autoLogin();

  } catch (error) {
    console.error('网络检测失败:', error);
  }
}

// 检测外网连接
async function checkInternetConnection() {
  try {
    const response = await fetch('https://www.baidu.com', {
      method: 'HEAD',
      mode: 'no-cors',
      cache: 'no-cache'
    });
    return true;
  } catch {
    return false;
  }
}

// 自动登录
async function autoLogin() {
  try {
    const result = await chrome.storage.local.get(['accounts', 'lastUsedAccount', 'settings']);
    const accounts = result.accounts || [];
    const lastUsed = result.lastUsedAccount;
    const settings = result.settings;

    let accountToUse = null;

    // 优先使用上次登录的账号
    if (lastUsed !== undefined && accounts[lastUsed]) {
      accountToUse = accounts[lastUsed];
    } else if (accounts.length > 0) {
      // 使用第一个账号
      accountToUse = accounts[0];
    }

    if (!accountToUse) {
      console.log('没有配置账号，跳过自动登录');
      return false;
    }

    // 构建登录参数
    const fullUsername = accountToUse.username + accountToUse.isp;
    const params = new URLSearchParams();
    params.append('DDDDD', fullUsername);
    params.append('upass', accountToUse.password);
    params.append('R1', '0');
    params.append('R2', '0');
    params.append('R3', '0');
    params.append('R6', '0');
    params.append('para', '00');
    params.append('0MKKey', '123456');
    params.append('buttonClicked', '');
    params.append('redirect_url', '');
    params.append('err_flag', '');
    params.append('username', '');
    params.append('password', '');
    params.append('user', '');
    params.append('cmd', '');
    params.append('Login', '');

    const response = await fetch(CONFIG.LOGIN_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: params.toString()
    });

    const text = await response.text();

    if (text.includes('success') || text.includes('成功') || response.ok) {
      console.log('自动登录成功');

      // 发送通知
      if (settings.showNotifications) {
        chrome.notifications.create({
          type: 'basic',
          iconUrl: 'icons/icon128.png',
          title: '校园网自动登录',
          message: `已使用账号 ${accountToUse.username} 自动登录成功。`
        });
      }

      return true;
    } else {
      console.log('自动登录失败');

      if (settings.showNotifications) {
        chrome.notifications.create({
          type: 'basic',
          iconUrl: 'icons/icon128.png',
          title: '校园网自动登录',
          message: '自动登录失败，请手动登录检查账号密码。'
        });
      }

      return false;
    }
  } catch (error) {
    console.error('自动登录错误:', error);
    return false;
  }
}

// 监听来自popup的消息
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'checkStatus') {
    checkInternetConnection().then(isOnline => {
      sendResponse({ isOnline });
    });
    return true; // 保持消息通道开启
  }

  if (request.action === 'autoLogin') {
    autoLogin().then(success => {
      sendResponse({ success });
    });
    return true;
  }

  if (request.action === 'updateSettings') {
    chrome.storage.local.set({ settings: request.settings });
    sendResponse({ success: true });
    return true;
  }
});