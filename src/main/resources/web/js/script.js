// 全局变量
const API_BASE = '/api';
let TOKEN = localStorage.getItem('adminToken') || '';
let ADMIN_INFO = JSON.parse(localStorage.getItem('adminInfo') || '{}');

// DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 检查登录状态
    checkLoginStatus();
    
    // 登录表单提交
    document.getElementById('login-form').addEventListener('submit', handleLogin);
    
    // 退出登录
    document.getElementById('logout-btn').addEventListener('click', handleLogout);
    
    // 导航切换
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', switchModule);
    });
    
    // 公告管理
    document.getElementById('add-announcement-btn').addEventListener('click', () => openAnnouncementModal());
    document.getElementById('announcement-form').addEventListener('submit', saveAnnouncement);
    
    // 补偿管理
    document.getElementById('add-compensation-btn').addEventListener('click', () => openCompensationModal());
    document.getElementById('compensation-form').addEventListener('submit', saveCompensation);
    document.getElementById('add-compensation-item').addEventListener('click', addCompensationItemToDOM);
    
    // 白名单管理
    document.getElementById('whitelist-switch').addEventListener('change', toggleWhitelist);
    document.getElementById('add-whitelist-btn').addEventListener('click', () => openWhitelistModal());
    document.getElementById('whitelist-form').addEventListener('submit', addWhitelist);
    
    // 模态框关闭/取消
    document.querySelectorAll('.modal-close, .modal-cancel').forEach(btn => {
        btn.addEventListener('click', closeAllModals);
    });

    // 初始化数据
    if (TOKEN) {
        loadAnnouncementList();
        loadCompensationList();
        loadWhitelistList();
        loadLogList();
    }
    
    // 颜色代码按钮事件处理
    setupColorButtons();
    
    // 重写fetch，统一处理401/403
    const originalFetch = window.fetch;
    window.fetch = async function(url, options = {}) {
        const response = await originalFetch(url, options);
        
        // 检查是否是未授权/权限不足
        if (response.status === 401 || response.status === 403) {
            // 清除本地存储
            localStorage.removeItem('adminToken');
            localStorage.removeItem('adminInfo');
            TOKEN = '';
            ADMIN_INFO = {};
            
            // 刷新登录状态
            checkLoginStatus();
            showToast('登录失效，请重新登录', 'error');
        }
        
        return response;
    };
});

// ====================== 登录相关 ======================
/**
 * 检查登录状态
 */
function checkLoginStatus() {
    if (TOKEN && ADMIN_INFO.username) {
        // 已登录，显示主面板
        document.getElementById('login-page').classList.add('hidden');
        document.getElementById('main-panel').classList.remove('hidden');
        document.getElementById('admin-name').textContent = ADMIN_INFO.username;
    } else {
        // 未登录，显示登录页
        document.getElementById('login-page').classList.remove('hidden');
        document.getElementById('main-panel').classList.add('hidden');
    }
}

/**
 * 处理登录提交
 */
async function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();
    const errorEl = document.getElementById('login-error');

    try {
        // 发送登录请求
        const response = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ username, password }),
        });

        const result = await response.json();
        if (result.success) {
            // 保存Token和管理员信息
            TOKEN = result.token;
            ADMIN_INFO = {
                username: result.username,
                permissions: result.permissions,
            };
            localStorage.setItem('adminToken', TOKEN);
            localStorage.setItem('adminInfo', JSON.stringify(ADMIN_INFO));
            
            // 刷新页面状态
            checkLoginStatus();
            showToast('登录成功', 'success');
            
            // 加载数据
            loadAnnouncementList();
            loadCompensationList();
            loadWhitelistList();
            loadLogList();
        } else {
            errorEl.textContent = result.message || '登录失败';
            errorEl.classList.remove('hidden');
            showToast(result.message || '登录失败', 'error');
        }
    } catch (error) {
        errorEl.textContent = '网络错误，请重试';
        errorEl.classList.remove('hidden');
        showToast('网络错误，请重试', 'error');
        console.error('登录失败：', error);
    }
}

/**
 * 处理退出登录
 */
function handleLogout() {
    // 清除本地存储
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminInfo');
    TOKEN = '';
    ADMIN_INFO = {};
    
    // 刷新页面状态
    checkLoginStatus();
    showToast('已退出登录', 'success');
}

// ====================== 模块切换 ======================
/**
 * 切换功能模块
 */
function switchModule(e) {
    const target = e.currentTarget.dataset.target;
    
    // 切换导航激活状态
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    e.currentTarget.classList.add('active');
    
    // 切换内容模块
    document.querySelectorAll('.module').forEach(module => {
        module.classList.add('hidden');
    });
    document.getElementById(target).classList.remove('hidden');
    
    // 按需加载数据
    switch (target) {
        case 'announcement':
            loadAnnouncementList();
            break;
        case 'compensation':
            loadCompensationList();
            break;
        case 'whitelist':
            loadWhitelistList();
            break;
        case 'log':
            loadLogList();
            break;
    }
}

// ====================== 公告管理 ======================
/**
 * 加载公告列表
 */
async function loadAnnouncementList() {
    try {
        const response = await fetch(`${API_BASE}/announcement`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            const listEl = document.getElementById('announcement-list');
            listEl.innerHTML = '';
            
            if (result.data && result.data.length > 0) {
                result.data.forEach(ann => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td class="border border-gray-200 px-4 py-2">${ann.id || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${ann.name || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${ann.priority || 0}</td>
                        <td class="border border-gray-200 px-4 py-2">${ann.sendTime || '立即发送'}</td>
                        <td class="border border-gray-200 px-4 py-2">${ann.createTime || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">
                            ${ann.sent ? '<span class="text-success">已发送</span>' : '<span class="text-warning">未发送</span>'}
                        </td>
                        <td class="border border-gray-200 px-4 py-2 text-center">
                            <button class="text-primary hover:text-primary/80 mr-2" onclick="editAnnouncement('${ann.id}')">
                                <i class="fa fa-edit"></i>
                            </button>
                            <button class="text-danger hover:text-danger/80" onclick="deleteAnnouncement('${ann.id}')">
                                <i class="fa fa-trash"></i>
                            </button>
                        </td>
                    `;
                    listEl.appendChild(tr);
                });
            } else {
                listEl.innerHTML = `
                    <tr>
                        <td colspan="7" class="border border-gray-200 px-4 py-8 text-center text-gray-500">
                            暂无公告数据
                        </td>
                    </tr>
                `;
            }
        } else {
            showToast(result.message || '加载公告失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，加载公告失败', 'error');
        console.error('加载公告失败：', error);
    }
}

/**
 * 打开公告弹窗
 */
function openAnnouncementModal(id = '') {
    const modal = document.getElementById('announcement-modal');
    const titleEl = document.getElementById('announcement-modal-title');
    const form = document.getElementById('announcement-form');
    
    // 重置表单
    form.reset();
    document.getElementById('announcement-id').value = '';
    
    if (id) {
        // 编辑模式
        titleEl.textContent = '编辑公告';
        // 查找公告数据（实际项目可重新请求，这里简化）
        fetch(`${API_BASE}/announcement`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${TOKEN}` }
        }).then(res => res.json()).then(result => {
            if (result.success) {
                const ann = result.data.find(item => item.id === id);
                if (ann) {
                    document.getElementById('announcement-id').value = ann.id;
                    document.getElementById('announcement-name').value = ann.name || '';
                    document.getElementById('announcement-content').value = ann.content || '';
                    document.getElementById('announcement-priority').value = ann.priority || 0;
                    // 转换时间格式为datetime-local（YYYY-MM-DDTHH:MM）
                    if (ann.sendTime) {
                        const formattedTime = ann.sendTime.replace(' ', 'T');
                        document.getElementById('announcement-send-time').value = formattedTime;
                    }
                }
            }
        });
    } else {
        // 新增模式
        titleEl.textContent = '新增公告';
    }
    
    // 显示弹窗
    modal.classList.add('show');
}

/**
 * 保存公告
 */
async function saveAnnouncement(e) {
    e.preventDefault();
    const id = document.getElementById('announcement-id').value;
    const name = document.getElementById('announcement-name').value.trim();
    const content = document.getElementById('announcement-content').value.trim();
    const priority = parseInt(document.getElementById('announcement-priority').value) || 0;
    let sendTime = document.getElementById('announcement-send-time').value;
    
    // 转换时间格式（YYYY-MM-DDTHH:MM → YYYY-MM-DD HH:MM）
    if (sendTime) {
        sendTime = sendTime.replace('T', ' ');
    } else {
        sendTime = '';
    }

    try {
        const data = { name, content, sendTime, priority };
        if (id) data.id = id;
        
        const response = await fetch(`${API_BASE}/announcement`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data),
        });

        const result = await response.json();
        if (result.success) {
            closeAllModals();
            loadAnnouncementList();
            showToast(id ? '公告编辑成功' : '公告新增成功', 'success');
        } else {
            showToast(result.message || '保存公告失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，保存公告失败', 'error');
        console.error('保存公告失败：', error);
    }
}

/**
 * 编辑公告
 */
function editAnnouncement(id) {
    openAnnouncementModal(id);
}

/**
 * 删除公告
 */
async function deleteAnnouncement(id) {
    if (!confirm('确定要删除该公告吗？')) return;

    try {
        const response = await fetch(`${API_BASE}/announcement?id=${id}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            loadAnnouncementList();
            showToast('公告删除成功', 'success');
        } else {
            showToast(result.message || '删除公告失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，删除公告失败', 'error');
        console.error('删除公告失败：', error);
    }
}

// ====================== 补偿管理 ======================
/**
 * 加载补偿列表
 */
async function loadCompensationList() {
    try {
        const response = await fetch(`${API_BASE}/compensation`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            const listEl = document.getElementById('compensation-list');
            listEl.innerHTML = '';
            
            if (result.data && result.data.length > 0) {
                result.data.forEach(comp => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td class="border border-gray-200 px-4 py-2">${comp.id || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${comp.name || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${comp.description || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${comp.createTime || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2 text-center">
                            <button class="text-primary hover:text-primary/80 mr-2" onclick="editCompensation('${comp.id}')">
                                <i class="fa fa-edit"></i>
                            </button>
                            <button class="text-danger hover:text-danger/80" onclick="deleteCompensation('${comp.id}')">
                                <i class="fa fa-trash"></i>
                            </button>
                        </td>
                    `;
                    listEl.appendChild(tr);
                });
            } else {
                listEl.innerHTML = `
                    <tr>
                        <td colspan="5" class="border border-gray-200 px-4 py-8 text-center text-gray-500">
                            暂无补偿数据
                        </td>
                    </tr>
                `;
            }
        } else {
            showToast(result.message || '加载补偿失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，加载补偿失败', 'error');
        console.error('加载补偿失败：', error);
    }
}

/**
 * 打开补偿弹窗
 */
function openCompensationModal(id = '') {
    const modal = document.getElementById('compensation-modal');
    const titleEl = document.getElementById('compensation-modal-title');
    const form = document.getElementById('compensation-form');
    
    // 重置表单
    form.reset();
    document.getElementById('compensation-id').value = '';
    
    // 清空物品列表
    const itemsContainer = document.getElementById('compensation-items-container');
    itemsContainer.innerHTML = '';
    
    if (id) {
        // 编辑模式
        titleEl.textContent = '编辑补偿';
        // 查找补偿数据
        fetch(`${API_BASE}/compensation`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${TOKEN}` }
        }).then(res => res.json()).then(result => {
            if (result.success) {
                const comp = result.data.find(item => item.id === id);
                if (comp) {
                    document.getElementById('compensation-id').value = comp.id;
                    document.getElementById('compensation-name').value = comp.name || '';
                    document.getElementById('compensation-desc').value = comp.description || '';
                    
                    // 加载物品列表
                    if (comp.items && comp.items.length > 0) {
                        comp.items.forEach(item => {
                            addCompensationItemToDOM(item.material, item.amount, item.customName, item.lore);
                        });
                    } else {
                        // 默认添加一个空物品行
                        addCompensationItemToDOM();
                    }
                }
            }
        });
    } else {
        // 新增模式
        titleEl.textContent = '新增补偿';
        // 默认添加一个空物品行
        addCompensationItemToDOM();
    }
    
    // 显示弹窗
    modal.classList.add('show');
}

/**
 * 添加补偿物品到DOM
 */
function addCompensationItemToDOM(material = '', amount = 1, customName = '', lore = []) {
    const itemsContainer = document.getElementById('compensation-items-container');
    const itemIndex = itemsContainer.children.length;
    
    const itemDiv = document.createElement('div');
    itemDiv.className = 'p-3 border border-gray-200 rounded-lg bg-gray-50';
    itemDiv.innerHTML = `
        <div class="flex items-center justify-between mb-2">
            <span class="text-sm font-medium text-gray-700">物品 ${itemIndex + 1}</span>
            <button type="button" class="text-danger hover:text-danger/80 delete-item" data-index="${itemIndex}">
                <i class="fa fa-trash"></i>
            </button>
        </div>
        <div class="grid grid-cols-2 gap-3">
            <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">物品类型</label>
                <input type="text" name="item-material" value="${material}" 
                       class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-primary/50 focus:border-primary outline-none" 
                       placeholder="DIAMOND" required>
            </div>
            <div>
                <label class="block text-xs font-medium text-gray-600 mb-1">数量</label>
                <input type="number" name="item-amount" value="${amount}" min="1" max="64" 
                       class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-primary/50 focus:border-primary outline-none" 
                       required>
            </div>
        </div>
        <div class="mt-3">
            <label class="block text-xs font-medium text-gray-600 mb-1">自定义名称（可选）</label>
            <input type="text" name="item-custom-name" value="${customName || ''}" 
                   class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-primary/50 focus:border-primary outline-none" 
                   placeholder="&b钻石">
        </div>
        <div class="mt-3">
            <label class="block text-xs font-medium text-gray-600 mb-1">描述（可选，每行一条）</label>
            <textarea name="item-lore" rows="2" 
                      class="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-primary/50 focus:border-primary outline-none" 
                      placeholder="&7高级钻石&#10;&a用于制作高级装备">${lore ? lore.join('\n') : ''}</textarea>
        </div>
    `;
    
    // 添加删除按钮事件
    itemDiv.querySelector('.delete-item').addEventListener('click', function() {
        itemDiv.remove();
        // 更新所有物品的索引显示
        updateItemIndices();
    });
    
    itemsContainer.appendChild(itemDiv);
}

/**
 * 更新物品索引显示
 */
function updateItemIndices() {
    const itemsContainer = document.getElementById('compensation-items-container');
    const itemDivs = itemsContainer.querySelectorAll('[class*="p-3 border border-gray-200 rounded-lg bg-gray-50"]');
    
    itemDivs.forEach((div, index) => {
        const titleSpan = div.querySelector('[class*="text-sm font-medium text-gray-700"]');
        if (titleSpan) {
            titleSpan.textContent = `物品 ${index + 1}`;
        }
        
        const deleteBtn = div.querySelector('.delete-item');
        if (deleteBtn) {
            deleteBtn.dataset.index = index;
        }
    });
}

// ====================== 颜色代码按钮 ======================
/**
 * 设置颜色代码按钮事件
 */
function setupColorButtons() {
    // 为公告编辑的颜色按钮添加事件
    document.addEventListener('click', function(e) {
        if (e.target && e.target.classList.contains('color-btn')) {
            const colorCode = e.target.dataset.code;
            const parentId = e.target.closest('div').id;
            
            let textareaId;
            if (parentId === 'color-buttons-announcement') {
                textareaId = 'announcement-content';
            } else if (parentId === 'color-buttons-compensation') {
                textareaId = 'compensation-desc';
            }
            
            if (textareaId) {
                const textarea = document.getElementById(textareaId);
                if (textarea) {
                    // 插入颜色代码到文本框
                    insertAtCursor(textarea, colorCode);
                    // 聚焦文本框
                    textarea.focus();
                }
            }
        }
    });
}

/**
 * 在文本框光标位置插入文本
 */
function insertAtCursor(textarea, text) {
    const startPos = textarea.selectionStart;
    const endPos = textarea.selectionEnd;
    const scrollTop = textarea.scrollTop;
    
    // 插入文本
    const newText = textarea.value.substring(0, startPos) + text + textarea.value.substring(endPos);
    textarea.value = newText;
    
    // 恢复光标位置
    textarea.selectionStart = textarea.selectionEnd = startPos + text.length;
    textarea.scrollTop = scrollTop;
}

/**
 * 获取补偿物品列表
 */
function getCompensationItems() {
    const itemsContainer = document.getElementById('compensation-items-container');
    const itemDivs = itemsContainer.querySelectorAll('[class*="p-3 border border-gray-200 rounded-lg bg-gray-50"]');
    const items = [];
    
    itemDivs.forEach(div => {
        const material = div.querySelector('input[name="item-material"]').value.trim().toUpperCase();
        const amount = parseInt(div.querySelector('input[name="item-amount"]').value) || 1;
        const customName = div.querySelector('input[name="item-custom-name"]').value.trim();
        const loreText = div.querySelector('textarea[name="item-lore"]').value.trim();
        const lore = loreText ? loreText.split('\n').map(line => line.trim()).filter(line => line) : [];
        
        if (material) {
            items.push({
                material: material,
                amount: amount,
                customName: customName,
                lore: lore
            });
        }
    });
    
    return items;
}

/**
 * 保存补偿
 */
async function saveCompensation(e) {
    e.preventDefault();
    const id = document.getElementById('compensation-id').value;
    const name = document.getElementById('compensation-name').value.trim();
    const description = document.getElementById('compensation-desc').value.trim();
    const items = getCompensationItems();
    
    // 验证至少有一个物品
    if (items.length === 0) {
        showToast('请至少添加一个补偿物品', 'error');
        return;
    }

    try {
        const data = { 
            name, 
            description,
            items
        };
        if (id) data.id = id;
        
        const response = await fetch(`${API_BASE}/compensation`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data),
        });

        const result = await response.json();
        if (result.success) {
            closeAllModals();
            loadCompensationList();
            showToast(id ? '补偿编辑成功' : '补偿新增成功', 'success');
        } else {
            showToast(result.message || '保存补偿失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，保存补偿失败', 'error');
        console.error('保存补偿失败：', error);
    }
}

/**
 * 编辑补偿
 */
function editCompensation(id) {
    openCompensationModal(id);
}

/**
 * 删除补偿
 */
async function deleteCompensation(id) {
    if (!confirm('确定要删除该补偿吗？')) return;

    try {
        const response = await fetch(`${API_BASE}/compensation?id=${id}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            loadCompensationList();
            showToast('补偿删除成功', 'success');
        } else {
            showToast(result.message || '删除补偿失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，删除补偿失败', 'error');
        console.error('删除补偿失败：', error);
    }
}

// ====================== 白名单管理 ======================
/**
 * 加载白名单列表
 */
async function loadWhitelistList() {
    try {
        const response = await fetch(`${API_BASE}/whitelist`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            // 设置白名单开关状态
            document.getElementById('whitelist-switch').checked = result.enabled || false;
            
            const listEl = document.getElementById('whitelist-list');
            listEl.innerHTML = '';
            
            if (result.data && result.data.length > 0) {
                result.data.forEach(entry => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td class="border border-gray-200 px-4 py-2">${entry.uuid || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${entry.playerName || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${entry.addTime || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2 text-center">
                            <button class="text-danger hover:text-danger/80" onclick="deleteWhitelist('${entry.uuid}')">
                                <i class="fa fa-trash"></i>
                            </button>
                        </td>
                    `;
                    listEl.appendChild(tr);
                });
            } else {
                listEl.innerHTML = `
                    <tr>
                        <td colspan="4" class="border border-gray-200 px-4 py-8 text-center text-gray-500">
                            暂无白名单数据
                        </td>
                    </tr>
                `;
            }
        } else {
            showToast(result.message || '加载白名单失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，加载白名单失败', 'error');
        console.error('加载白名单失败：', error);
    }
}

/**
 * 切换白名单状态
 */
async function toggleWhitelist() {
    const enabled = document.getElementById('whitelist-switch').checked;

    try {
        const response = await fetch(`${API_BASE}/whitelist`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ action: 'toggle', enabled }),
        });

        const result = await response.json();
        if (result.success) {
            showToast(`白名单已${enabled ? '启用' : '禁用'}`, 'success');
        } else {
            // 回滚开关状态
            document.getElementById('whitelist-switch').checked = !enabled;
            showToast(result.message || '切换白名单状态失败', 'error');
        }
    } catch (error) {
        // 回滚开关状态
        document.getElementById('whitelist-switch').checked = !enabled;
        showToast('网络错误，切换白名单状态失败', 'error');
        console.error('切换白名单状态失败：', error);
    }
}

/**
 * 打开白名单弹窗
 */
function openWhitelistModal() {
    const modal = document.getElementById('whitelist-modal');
    const form = document.getElementById('whitelist-form');
    
    // 重置表单
    form.reset();
    // 显示弹窗
    modal.classList.add('show');
}

/**
 * 添加白名单
 */
async function addWhitelist(e) {
    e.preventDefault();
    const uuid = document.getElementById('whitelist-uuid').value.trim();
    const name = document.getElementById('whitelist-name').value.trim();

    try {
        const response = await fetch(`${API_BASE}/whitelist`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ 
                action: 'add',
                uuid,
                name
            }),
        });

        const result = await response.json();
        if (result.success) {
            closeAllModals();
            loadWhitelistList();
            showToast('白名单添加成功', 'success');
        } else {
            showToast(result.message || '添加白名单失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，添加白名单失败', 'error');
        console.error('添加白名单失败：', error);
    }
}

/**
 * 删除白名单
 */
async function deleteWhitelist(uuid) {
    if (!confirm('确定要删除该白名单吗？')) return;

    try {
        const response = await fetch(`${API_BASE}/whitelist?uuid=${uuid}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            loadWhitelistList();
            showToast('白名单删除成功', 'success');
        } else {
            showToast(result.message || '删除白名单失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，删除白名单失败', 'error');
        console.error('删除白名单失败：', error);
    }
}

// ====================== 日志管理 ======================
/**
 * 加载领取日志
 */
async function loadLogList() {
    try {
        const response = await fetch(`${API_BASE}/log`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${TOKEN}`,
            },
        });

        const result = await response.json();
        if (result.success) {
            const listEl = document.getElementById('log-list');
            listEl.innerHTML = '';
            
            if (result.data && result.data.length > 0) {
                result.data.forEach(log => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td class="border border-gray-200 px-4 py-2">${log.id || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${log.playerName || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${log.playerUUID || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${log.compensationId || '-'}</td>
                        <td class="border border-gray-200 px-4 py-2">${log.claimTime || '-'}</td>
                    `;
                    listEl.appendChild(tr);
                });
            } else {
                listEl.innerHTML = `
                    <tr>
                        <td colspan="5" class="border border-gray-200 px-4 py-8 text-center text-gray-500">
                            暂无领取日志数据
                        </td>
                    </tr>
                `;
            }
        } else {
            showToast(result.message || '加载日志失败', 'error');
        }
    } catch (error) {
        showToast('网络错误，加载日志失败', 'error');
        console.error('加载日志失败：', error);
    }
}

// ====================== 通用工具 ======================
/**
 * 关闭所有模态框
 */
function closeAllModals() {
    document.querySelectorAll('.modal').forEach(modal => {
        modal.classList.remove('show');
    });
}

/**
 * 显示提示框
 */
function showToast(text, type = 'success') {
    const toast = document.getElementById('toast');
    const icon = document.getElementById('toast-icon');
    const textEl = document.getElementById('toast-text');
    
    // 设置内容和样式
    textEl.textContent = text;
    toast.className = `fixed top-4 right-4 py-2 px-4 rounded-lg shadow-lg flex items-center gap-2 transform transition-all duration-300 z-50 ${type}`;
    
    // 设置图标
    switch (type) {
        case 'success':
            icon.className = 'fa fa-check-circle';
            break;
        case 'error':
            icon.className = 'fa fa-times-circle';
            break;
        case 'warning':
            icon.className = 'fa fa-exclamation-circle';
            break;
        default:
            icon.className = 'fa fa-info-circle';
    }
    
    // 显示提示
    toast.classList.add('show');
    
    // 3秒后隐藏
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}