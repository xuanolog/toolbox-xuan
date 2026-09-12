// DOM 元素
const elements = {
  newName: document.getElementById('newName'),
  newUsername: document.getElementById('newUsername'),
  newPassword: document.getElementById('newPassword'),
  newIsp: document.getElementById('newIsp'),
  addAccountBtn: document.getElementById('addAccountBtn'),
  accountList: document.getElementById('accountList'),
  autoLogin: document.getElementById('autoLogin'),
  autoReconnect: document.getElementById('autoReconnect'),
  showNotifications: document.getElementById('showNotifications'),
  checkInterval: document.getElementById('checkInterval'),
  serverUrl: document.getElementById('serverUrl'),
  saveBtn: document.getElementById('saveBtn'),
  resetBtn: document.getElementById('resetBtn'),
  exportBtn: document.getElementById('exportBtn'),
  importBtn: document.getElementById('importBtn'),
  importFile: document.getElementById('importFile'),
  toast: document.getElementById('toast')
};

// 初始化
document.addEventListener('DOMContentLoaded', () => {
  loadAccounts();
  loadSettings();
  bindEvents();
});

// 加载账号列表
async function loadAccounts() {
  const result = await chrome.storage.local.get(['accounts']);
  const accounts = result.accounts || [];

  if (accounts.length === 0) {
    elements.accountList.innerHTML = '<p class="empty-hint">暂无保存的账号</p>';
    return;
  }

  const ISP_NAMES = {
    '@telecom': '中国电信',
    '@cmcc': '中国移动',
    '@unicom': '中国联通',
    '': '校园宽带'
  };

  elements.accountList.innerHTML = accounts.map((account, index) => `
    <div class="account-item" data-index="${index}">
      <div class="account-info">
        <h3>${account.name || account.username}</h3>
        <p>${account.username} - ${ISP_NAMES[account.isp] || '未知运营商'}</p>
      </div>
      <div class="account-actions">
        <button class="btn-icon edit" title="编辑" data-index="${index}">&#9998;</button>
        <button class="btn-icon delete" title="删除" data-index="${index}">&#10005;</button>
      </div>
    </div>
  `).join('');

  // 绑定删除和编辑事件
  document.querySelectorAll('.btn-icon.delete').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.currentTarget.dataset.index);
      deleteAccount(index);
    });
  });

  document.querySelectorAll('.btn-icon.edit').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.currentTarget.dataset.index);
      editAccount(index);
    });
  });
}

// 加载设置
async function loadSettings() {
  const result = await chrome.storage.local.get(['settings']);
  const settings = result.settings || {
    autoLogin: true,
    autoReconnect: true,
    checkInterval: 5,
    showNotifications: true,
    serverUrl: 'http://172.16.8.22'
  };

  elements.autoLogin.checked = settings.autoLogin;
  elements.autoReconnect.checked = settings.autoReconnect;
  elements.showNotifications.checked = settings.showNotifications;
  elements.checkInterval.value = settings.checkInterval;
  elements.serverUrl.value = settings.serverUrl || 'http://172.16.8.22';
}

// 绑定事件
function bindEvents() {
  // 添加账号
  elements.addAccountBtn.addEventListener('click', addAccount);

  // 保存设置
  elements.saveBtn.addEventListener('click', saveSettings);

  // 恢复默认
  elements.resetBtn.addEventListener('click', resetSettings);

  // 导出配置
  elements.exportBtn.addEventListener('click', exportConfig);

  // 导入配置
  elements.importBtn.addEventListener('click', () => {
    elements.importFile.click();
  });

  elements.importFile.addEventListener('change', importConfig);
}

// 添加账号
async function addAccount() {
  const name = elements.newName.value.trim();
  const username = elements.newUsername.value.trim();
  const password = elements.newPassword.value.trim();
  const isp = elements.newIsp.value;

  if (!username || !password) {
    showToast('请输入学号和密码', 'error');
    return;
  }

  if (isp === '-1') {
    showToast('请选择运营商', 'error');
    return;
  }

  const result = await chrome.storage.local.get(['accounts']);
  const accounts = result.accounts || [];

  // 检查是否已存在
  const existing = accounts.findIndex(a => a.username === username && a.isp === isp);
  if (existing >= 0) {
    showToast('该账号已存在', 'error');
    return;
  }

  accounts.push({
    name: name || username,
    username,
    password,
    isp
  });

  await chrome.storage.local.set({ accounts });

  // 清空表单
  elements.newName.value = '';
  elements.newUsername.value = '';
  elements.newPassword.value = '';
  elements.newIsp.value = '-1';

  showToast('账号添加成功', 'success');
  loadAccounts();
}

// 删除账号
async function deleteAccount(index) {
  if (!confirm('确定要删除这个账号吗？')) {
    return;
  }

  const result = await chrome.storage.local.get(['accounts']);
  const accounts = result.accounts || [];

  accounts.splice(index, 1);
  await chrome.storage.local.set({ accounts });

  showToast('账号已删除', 'success');
  loadAccounts();
}

// 编辑账号
async function editAccount(index) {
  const result = await chrome.storage.local.get(['accounts']);
  const accounts = result.accounts || [];

  if (!accounts[index]) return;

  const account = accounts[index];

  // 填充表单
  elements.newName.value = account.name || '';
  elements.newUsername.value = account.username;
  elements.newPassword.value = account.password;
  elements.newIsp.value = account.isp;

  // 删除原账号
  accounts.splice(index, 1);
  await chrome.storage.local.set({ accounts });

  showToast('请修改后点击添加', 'info');
  loadAccounts();
}

// 保存设置
async function saveSettings() {
  const settings = {
    autoLogin: elements.autoLogin.checked,
    autoReconnect: elements.autoReconnect.checked,
    showNotifications: elements.showNotifications.checked,
    checkInterval: parseInt(elements.checkInterval.value) || 5,
    serverUrl: elements.serverUrl.value.trim()
  };

  await chrome.storage.local.set({ settings });

  // 通知background更新闹钟
  chrome.runtime.sendMessage({
    action: 'updateSettings',
    settings
  });

  showToast('设置已保存', 'success');
}

// 恢复默认设置
async function resetSettings() {
  if (!confirm('确定要恢复默认设置吗？')) {
    return;
  }

  const defaultSettings = {
    autoLogin: true,
    autoReconnect: true,
    showNotifications: true,
    checkInterval: 5,
    serverUrl: 'http://172.16.8.22'
  };

  await chrome.storage.local.set({ settings: defaultSettings });
  loadSettings();

  showToast('已恢复默认设置', 'success');
}

// 导出配置
async function exportConfig() {
  const result = await chrome.storage.local.get(['accounts', 'settings']);
  const config = {
    accounts: result.accounts || [],
    settings: result.settings || {},
    exportDate: new Date().toISOString()
  };

  const blob = new Blob([JSON.stringify(config, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = 'campus-network-config.json';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);

  showToast('配置已导出', 'success');
}

// 导入配置
async function importConfig(e) {
  const file = e.target.files[0];
  if (!file) return;

  try {
    const text = await file.text();
    const config = JSON.parse(text);

    if (config.accounts) {
      await chrome.storage.local.set({ accounts: config.accounts });
    }

    if (config.settings) {
      await chrome.storage.local.set({ settings: config.settings });
      loadSettings();
    }

    showToast('配置已导入', 'success');
    loadAccounts();
  } catch (error) {
    showToast('导入失败: 文件格式错误', 'error');
  }

  // 重置文件输入
  e.target.value = '';
}

// 显示提示
function showToast(message, type = 'info') {
  elements.toast.textContent = message;
  elements.toast.className = `toast ${type}`;
  setTimeout(() => {
    elements.toast.className = 'toast hidden';
  }, 3000);
}