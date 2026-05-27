// 常量配置
const CONFIG = {
  LOGIN_URL: 'http://172.16.8.22:801/eportal/?c=ACSetting&a=Login',
  LOGOUT_URL: 'http://172.16.8.22/eportal/?c=ACSetting&a=Logout&ver=1.0',
  CHECK_URL: 'http://172.16.8.22/',
  ISP_OPTIONS: {
    '@telecom': '中国电信',
    '@cmcc': '中国移动',
    '@unicom': '中国联通',
    '': '校园宽带'
  }
};

// DOM 元素
const elements = {
  status: document.getElementById('status'),
  accountSelect: document.getElementById('accountSelect'),
  ispSelect: document.getElementById('ispSelect'),
  username: document.getElementById('username'),
  password: document.getElementById('password'),
  loginBtn: document.getElementById('loginBtn'),
  logoutBtn: document.getElementById('logoutBtn'),
  addAccountBtn: document.getElementById('addAccountBtn'),
  openSettings: document.getElementById('openSettings'),
  openHelp: document.getElementById('openHelp'),
  toast: document.getElementById('toast')
};

// 初始化
document.addEventListener('DOMContentLoaded', async () => {
  await loadAccounts();
  checkNetworkStatus();
  bindEvents();
});

// 加载账号列表
async function loadAccounts() {
  const result = await chrome.storage.local.get(['accounts', 'lastUsedAccount']);
  const accounts = result.accounts || [];
  const lastUsed = result.lastUsedAccount;

  // 清空下拉框
  elements.accountSelect.innerHTML = '<option value="">-- 请选择账号 --</option>';

  // 添加账号选项
  accounts.forEach((account, index) => {
    const option = document.createElement('option');
    option.value = index;
    option.textContent = `${account.name || account.username} (${CONFIG.ISP_OPTIONS[account.isp] || '未知'})`;
    elements.accountSelect.appendChild(option);
  });

  // 选中上次使用的账号
  if (lastUsed !== undefined && accounts[lastUsed]) {
    elements.accountSelect.value = lastUsed;
    selectAccount(lastUsed);
  }
}

// 选择账号
function selectAccount(index) {
  chrome.storage.local.get(['accounts'], (result) => {
    const accounts = result.accounts || [];
    if (accounts[index]) {
      const account = accounts[index];
      elements.ispSelect.value = account.isp;
      elements.username.value = account.username;
      elements.password.value = account.password;
    }
  });
}

// 检测网络状态
async function checkNetworkStatus() {
  elements.status.className = 'status checking';
  elements.status.querySelector('.status-text').textContent = '检测中...';

  try {
    const response = await fetch(CONFIG.CHECK_URL, {
      method: 'GET',
      mode: 'no-cors',
      cache: 'no-cache'
    });
    // 如果能访问到登录页面，说明未登录
    setNetworkStatus(false);
  } catch (error) {
    // 如果无法访问，可能已登录或网络断开
    // 尝试访问外网检测是否真正已登录
    try {
      await fetch('https://www.baidu.com', {
        method: 'HEAD',
        mode: 'no-cors',
        cache: 'no-cache'
      });
      setNetworkStatus(true);
    } catch {
      setNetworkStatus(false);
    }
  }
}

// 设置网络状态显示
function setNetworkStatus(isOnline) {
  elements.status.className = `status ${isOnline ? 'online' : 'offline'}`;
  elements.status.querySelector('.status-text').textContent = isOnline ? '已连接' : '未连接';
}

// 绑定事件
function bindEvents() {
  // 账号选择变化
  elements.accountSelect.addEventListener('change', (e) => {
    if (e.target.value !== '') {
      selectAccount(parseInt(e.target.value));
    }
  });

  // 登录按钮
  elements.loginBtn.addEventListener('click', handleLogin);

  // 注销按钮
  elements.logoutBtn.addEventListener('click', handleLogout);

  // 添加账号按钮
  elements.addAccountBtn.addEventListener('click', () => {
    const username = elements.username.value.trim();
    const password = elements.password.value.trim();
    const isp = elements.ispSelect.value;

    if (!username || !password) {
      showToast('请先输入账号和密码', 'error');
      return;
    }

    if (isp === '-1') {
      showToast('请选择运营商', 'error');
      return;
    }

    saveAccount(username, password, isp);
  });

  // 设置页面
  elements.openSettings.addEventListener('click', (e) => {
    e.preventDefault();
    chrome.runtime.openOptionsPage();
  });

  // 使用帮助
  elements.openHelp.addEventListener('click', (e) => {
    e.preventDefault();
    showToast('1. 添加账号 2. 选择账号 3. 点击登录', 'info');
  });
}

// 保存账号
async function saveAccount(username, password, isp) {
  const result = await chrome.storage.local.get(['accounts']);
  const accounts = result.accounts || [];

  // 检查是否已存在相同账号
  const existingIndex = accounts.findIndex(a => a.username === username && a.isp === isp);
  if (existingIndex >= 0) {
    accounts[existingIndex] = { username, password, isp, name: username };
    showToast('账号已更新', 'success');
  } else {
    accounts.push({ username, password, isp, name: username });
    showToast('账号已保存', 'success');
  }

  await chrome.storage.local.set({ accounts });
  await loadAccounts();
}

// 处理登录
async function handleLogin() {
  const username = elements.username.value.trim();
  const password = elements.password.value.trim();
  const isp = elements.ispSelect.value;

  if (!username || !password) {
    showToast('请输入账号和密码', 'error');
    return;
  }

  if (isp === '-1') {
    showToast('请选择运营商', 'error');
    return;
  }

  elements.loginBtn.disabled = true;
  elements.loginBtn.textContent = '登录中...';

  try {
    // 构建登录参数
    const fullUsername = username + isp;
    const params = new URLSearchParams();
    params.append('DDDDD', fullUsername);
    params.append('upass', password);
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

    // 保存最后使用的账号
    const accountIndex = elements.accountSelect.value;
    if (accountIndex !== '') {
      await chrome.storage.local.set({ lastUsedAccount: parseInt(accountIndex) });
    }

    if (text.includes('success') || text.includes('成功') || response.ok) {
      showToast('登录成功', 'success');
      setNetworkStatus(true);
      chrome.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon128.png',
        title: '校园网登录',
        message: '登录成功！已连接校园网。'
      });
    } else {
      showToast('登录失败，请检查账号密码', 'error');
      chrome.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon128.png',
        title: '校园网登录',
        message: '登录失败，请检查账号密码是否正确。'
      });
    }
  } catch (error) {
    showToast('登录失败: ' + error.message, 'error');
  } finally {
    elements.loginBtn.disabled = false;
    elements.loginBtn.textContent = '登录';
  }
}

// 处理注销
async function handleLogout() {
  elements.logoutBtn.disabled = true;
  elements.logoutBtn.textContent = '注销中...';

  try {
    await fetch(CONFIG.LOGOUT_URL, {
      method: 'GET',
      mode: 'no-cors'
    });

    showToast('注销成功', 'success');
    setNetworkStatus(false);
    chrome.notifications.create({
      type: 'basic',
      iconUrl: 'icons/icon128.png',
      title: '校园网注销',
      message: '已成功注销校园网连接。'
    });
  } catch (error) {
    showToast('注销失败: ' + error.message, 'error');
  } finally {
    elements.logoutBtn.disabled = false;
    elements.logoutBtn.textContent = '注销';
  }
}

// 显示提示
function showToast(message, type = 'info') {
  elements.toast.textContent = message;
  elements.toast.className = `toast ${type}`;
  setTimeout(() => {
    elements.toast.className = 'toast hidden';
  }, 3000);
}