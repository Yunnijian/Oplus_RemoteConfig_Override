class ConfigEditor {
    constructor() {
        // 删除了默认配置文件路径
        this.config = null;
        this.originalConfig = null;
        this.currentConfigPath = null; // 添加当前配置文件路径
        
        // 获取弹窗元素
        this.modal = document.getElementById('custom-modal');
        this.modalTitle = document.getElementById('modal-title');
        this.modalBody = document.getElementById('modal-body');
        this.modalButtons = document.getElementById('modal-buttons');
        
        // 当前模态框的resolve函数
        this.currentModalResolve = null;
        
        // 属性
        this.fieldOrderMap = new Map();
        this.objectDataMap = new Map();
        this.allPackages = []; // 存储所有包名
        
        // 不再自动初始化数据库字段
    }
    
    // 显示自定义模态框
    showModal(options) {
        return new Promise((resolve) => {
            this.currentModalResolve = resolve;
            
            this.modalTitle.textContent = options.title || '提示';
            
            this.modalBody.innerHTML = '';
            
            if (options.type === 'prompt') {
                this.modalBody.innerHTML = `
                    <div class="modal-message">${options.message || '请输入:'}</div>
                    <input type="text" class="modal-input" id="modal-input" value="${options.defaultValue || ''}">
                `;
            } else if (options.type === 'alert') {
                this.modalBody.innerHTML = `
                    <div class="modal-message" style="white-space: pre-wrap; word-wrap: break-word; max-height: 400px; overflow-y: auto; padding: 15px; background: #f5f5f5; border-radius: 5px; font-family: monospace; font-size: 13px; line-height: 1.6;">${options.message || ''}</div>
                `;
            } else if (options.type === 'confirm') {
                this.modalBody.innerHTML = `
                    <div class="modal-message">${options.message || '确定要执行此操作吗?'}</div>
                `;
            } else if (options.type === 'file-select') {
                this.modalBody.innerHTML = `
                    <div class="modal-message">${options.message || '请选择配置文件:'}</div>
                    <div class="file-list" id="file-list" style="max-height: 300px; overflow-y: auto; margin-top: 15px; border: 1px solid #e0e7ee; border-radius: 8px; padding: 10px;">
                        <div class="loading-files">正在搜索配置文件...</div>
                    </div>
                `;
            } else if (options.type === 'package-select') {
                this.modalBody.innerHTML = `
                    <div class="modal-message">${options.message || '请选择要提取的游戏包名:'}</div>
                    <div class="file-list" id="file-list" style="max-height: 300px; overflow-y: auto; margin-top: 15px; border: 1px solid #e0e7ee; border-radius: 8px; padding: 10px;">
                        <div class="loading-files">正在获取游戏包名列表...</div>
                    </div>
                `;
            } else if (options.type === 'mode-select') {
                this.modalBody.innerHTML = `
                    <div class="modal-message" style="margin-bottom: 20px; font-size: 16px;">请选择提取模式：</div>
                    <div class="mode-buttons" style="display: flex; gap: 15px; justify-content: center; margin: 20px 0;">
                        <button class="mode-btn mode-1" style="flex: 1; padding: 15px; background: #4CAF50; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; display: flex; flex-direction: column; align-items: center;">
                            <span style="font-size: 24px; font-weight: bold; margin-bottom: 5px;">1</span>
                            <span>整理模式</span>
                            <span style="font-size: 12px; margin-top: 5px; opacity: 0.9;">整理后的数据库数据</span>
                        </button>
                        <button class="mode-btn mode-2" style="flex: 1; padding: 15px; background: #2196F3; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; display: flex; flex-direction: column; align-items: center;">
                            <span style="font-size: 24px; font-weight: bold; margin-bottom: 5px;">2</span>
                            <span>原始模式</span>
                            <span style="font-size: 12px; margin-top: 5px; opacity: 0.9;">输出数据库原始数据</span>
                        </button>
                    </div>
                `;
            }
            
            this.modalButtons.innerHTML = '';
            
            if (options.type === 'prompt') {
                this.addButton('确定', 'confirm', true);
                this.addButton('取消', 'cancel');
            } else if (options.type === 'alert') {
                this.addButton('确定', 'confirm');
            } else if (options.type === 'confirm') {
                this.addButton('确定', 'confirm');
                this.addButton('取消', 'cancel');
            } else if (options.type === 'file-select') {
                this.addButton('确定', 'confirm', true);
                this.addButton('取消', 'cancel');
            } else if (options.type === 'package-select') {
                this.addButton('全部提取', 'all');
                this.addButton('部分提取', 'select');
                this.addButton('取消', 'cancel');
            } else if (options.type === 'mode-select') {
                this.addButton('取消', 'cancel');
            }
            
            this.modal.classList.add('active');
            
            if (options.type === 'prompt') {
                setTimeout(() => {
                    const input = document.getElementById('modal-input');
                    if (input) {
                        input.focus();
                        input.select();
                    }
                }, 100);
            } else if (options.type === 'file-select') {
                this.loadFileList();
            } else if (options.type === 'package-select') {
                this.loadPackageListForExtract();
            } else if (options.type === 'mode-select') {
                const mode1Btn = this.modalBody.querySelector('.mode-1');
                const mode2Btn = this.modalBody.querySelector('.mode-2');
                
                if (mode1Btn) {
                    mode1Btn.addEventListener('click', () => {
                        this.modal.classList.remove('active');
                        this.currentModalResolve('1');
                    });
                }
                
                if (mode2Btn) {
                    mode2Btn.addEventListener('click', () => {
                        this.modal.classList.remove('active');
                        this.currentModalResolve('2');
                    });
                }
            }
        });
    }
    
    // 加载提取用的包名列表（单选，使用checkbox但限制只能选一个）
    async loadPackageListForExtract() {
        const fileListElement = document.getElementById('file-list');
        if (!fileListElement) return;
        
        try {
            fileListElement.innerHTML = '<div class="loading-files">正在获取游戏包名列表...</div>';
            
            const { sqlitePath, dbPath } = await this.ensureDatabaseAvailable();
            
            const command = `"${sqlitePath}" "${dbPath}" "SELECT DISTINCT package_name FROM PackageConfigBean WHERE package_name NOT IN ('oplus.cosa.common.model.config', 'oplus.cosa.default.model.config') AND package_name LIKE 'com.%' ORDER BY package_name;"`;
            
            const { stdout } = await this.execCommand(command);
            
            if (!stdout || stdout.trim() === '') {
                fileListElement.innerHTML = '<div class="no-files">未找到游戏包名</div>';
                return;
            }
            
            const packages = stdout.trim().split('\n').filter(pkg => pkg.trim() !== '' && pkg.includes('.'));
            
            if (packages.length === 0) {
                fileListElement.innerHTML = '<div class="no-files">未找到游戏包名</div>';
                return;
            }
            
            this.allPackages = packages;
            
            fileListElement.innerHTML = '';
            
            packages.forEach((packageName, index) => {
                const fileItem = document.createElement('div');
                fileItem.className = 'file-item';
                fileItem.innerHTML = `
                    <label class="file-label">
                        <input type="checkbox" name="extract-package" value="${packageName}" ${index === 0 ? 'checked' : ''}>
                        <span class="file-name">${packageName}</span>
                        <span class="file-path">${packageName}</span>
                    </label>
                `;
                fileListElement.appendChild(fileItem);
            });
            
            // 添加事件监听，确保只能选中一个
            const checkboxes = document.querySelectorAll('input[name="extract-package"]');
            checkboxes.forEach(checkbox => {
                checkbox.addEventListener('change', function() {
                    if (this.checked) {
                        checkboxes.forEach(cb => {
                            if (cb !== this) {
                                cb.checked = false;
                            }
                        });
                    }
                });
            });
            
        } catch (error) {
            console.error('加载包名列表失败:', error);
            fileListElement.innerHTML = '<div class="error-files">加载失败: ' + error.message + '</div>';
        }
    }
    
    // 确保数据库可用
    async ensureDatabaseAvailable() {
        const sqlitePath = '/data/adb/modules/RemoteConfigOverride/tool/sqlite3';
        const dbPath = await this.getAvailableDatabasePath();
        
        if (!dbPath) {
            throw new Error('数据库不可用');
        }
        
        // 简单验证是否能查询
        const { stdout } = await this.execCommand(
            `"${sqlitePath}" "${dbPath}" "SELECT 1;" 2>/dev/null`
        );
        
        if (!stdout || !stdout.trim()) {
            throw new Error('数据库查询失败');
        }
        
        return { sqlitePath, dbPath };
    }
    
    // 加载游戏包名列表方法（原有功能保留）
    async loadPackageList() {
        const fileListElement = document.getElementById('file-list');
        if (!fileListElement) return;
        
        try {
            fileListElement.innerHTML = '<div class="loading-files">正在获取游戏包名列表...</div>';
            
            const { sqlitePath, dbPath } = await this.ensureDatabaseAvailable();
            
            console.log(`使用数据库路径: ${dbPath}`);
            
            const command = `"${sqlitePath}" "${dbPath}" "SELECT DISTINCT package_name FROM PackageConfigBean WHERE package_name NOT IN ('oplus.cosa.common.model.config', 'oplus.cosa.default.model.config') AND package_name LIKE 'com.%' ORDER BY package_name;"`;
            
            const { stdout } = await this.execCommand(command);
            
            if (!stdout || stdout.trim() === '') {
                fileListElement.innerHTML = '<div class="no-files">未找到游戏配置</div>';
                return;
            }
            
            const packages = stdout.trim().split('\n').filter(pkg => pkg.trim() !== '' && pkg.includes('.'));
            
            if (packages.length === 0) {
                fileListElement.innerHTML = '<div class="no-files">未找到游戏配置</div>';
                return;
            }
            
            fileListElement.innerHTML = '';
            
            packages.forEach((packageName, index) => {
                const fileItem = document.createElement('div');
                fileItem.className = 'file-item';
                fileItem.innerHTML = `
                    <label class="file-label">
                        <input type="checkbox" name="config-package" value="${packageName}" ${index === 0 ? 'checked' : ''}>
                        <span class="file-name">${packageName}</span>
                        <span class="file-path">${packageName.replace('.', '/')}.json</span>
                    </label>
                `;
                fileListElement.appendChild(fileItem);
            });
            
        } catch (error) {
            console.error('加载包名列表失败:', error);
            fileListElement.innerHTML = '<div class="error-files">加载包名列表失败: ' + error.message + '</div>';
        }
    }
    
    // 修复的loadFileList方法：只搜索json目录
    async loadFileList() {
        const fileListElement = document.getElementById('file-list');
        if (!fileListElement) return;
        
        try {
            fileListElement.innerHTML = '<div class="loading-files">正在搜索配置文件...</div>';
            
            // 只搜索json目录，不再搜索output目录
            const searchPaths = [
                '/data/adb/modules/RemoteConfigOverride/json/'
            ];
            
            let allFiles = [];
            
            for (const searchPath of searchPaths) {
                try {
                    const { stdout } = await this.execCommand(`find "${searchPath}" -name "*.json" -type f 2>/dev/null || echo ""`);
                    
                    if (stdout && stdout.trim() !== '') {
                        const files = stdout.trim().split('\n').filter(file => file.trim() !== '');
                        allFiles = allFiles.concat(files);
                    }
                } catch (error) {
                    console.debug(`搜索路径 ${searchPath} 失败:`, error);
                }
            }
            
            if (allFiles.length === 0) {
                fileListElement.innerHTML = '<div class="no-files">未找到配置文件</div>';
                return;
            }
            
            fileListElement.innerHTML = '';
            
            allFiles.forEach((filePath, index) => {
                const fileName = filePath.split('/').pop() || filePath;
                const fileItem = document.createElement('div');
                fileItem.className = 'file-item';
                fileItem.innerHTML = `
                    <label class="file-label">
                        <input type="radio" name="config-file" value="${filePath}" ${index === 0 ? 'checked' : ''}>
                        <span class="file-name">${fileName}</span>
                        <span class="file-path">${filePath}</span>
                    </label>
                `;
                fileListElement.appendChild(fileItem);
            });
            
        } catch (error) {
            console.error('加载文件列表失败:', error);
            fileListElement.innerHTML = '<div class="error-files">加载文件列表失败</div>';
        }
    }
    
    // 添加模态框按钮
    addButton(text, type, isPrimary = false) {
        const button = document.createElement('button');
        button.className = `modal-btn ${type} ${isPrimary ? 'primary' : ''}`;
        button.textContent = text;
        button.addEventListener('click', () => {
            this.handleButtonClick(type);
        });
        this.modalButtons.appendChild(button);
    }
    
    // 处理按钮点击
    handleButtonClick(type) {
        if (type === 'confirm') {
            if (this.modalTitle.textContent === '输入配置路径') {
                const input = document.getElementById('modal-input');
                this.currentModalResolve(input ? input.value : '');
            } else if (this.modalTitle.textContent === '选择配置文件') {
                const selectedFile = document.querySelector('input[name="config-file"]:checked');
                this.currentModalResolve(selectedFile ? selectedFile.value : '');
            } else if (this.modalTitle.textContent === '请选择要提取的游戏包名:') {
                const selectedPackages = Array.from(document.querySelectorAll('input[name="config-package"]:checked'))
                    .map(input => input.value);
                this.currentModalResolve(selectedPackages);
            } else {
                this.currentModalResolve(true);
            }
        } else if (type === 'all' || type === 'select') {
            if (this.modalTitle.textContent === '请选择要提取的游戏包名:') {
                if (type === 'all') {
                    this.currentModalResolve('all');
                } else if (type === 'select') {
                    // 获取选中的包名（checkbox单选）
                    const selectedPackage = document.querySelector('input[name="extract-package"]:checked');
                    this.currentModalResolve(selectedPackage ? selectedPackage.value : null);
                }
            }
        } else {
            this.currentModalResolve(false);
        }
        this.modal.classList.remove('active');
    }
    
    // 检查数据库和sqlite工具方法
    async checkDatabaseAndSqlite(sqlitePath, dbPath) {
        try {
            const sqliteExists = await this.execCommand(`ls "${sqlitePath}" 2>/dev/null && echo "exists" || echo "not_exists"`);
            if (!sqliteExists.stdout || sqliteExists.stdout.includes('not_exists')) {
                console.log(`SQLite工具不存在: ${sqlitePath}`);
                return false;
            }
            
            const sqliteExecutable = await this.execCommand(`[ -x "${sqlitePath}" ] && echo "executable" || echo "not_executable"`);
            if (!sqliteExecutable.stdout || sqliteExecutable.stdout.includes('not_executable')) {
                console.log(`SQLite工具不可执行: ${sqlitePath}`);
                return false;
            }
            
            const dbExists = await this.execCommand(`ls "${dbPath}" 2>/dev/null && echo "exists" || echo "not_exists"`);
            if (!dbExists.stdout || dbExists.stdout.includes('not_exists')) {
                console.log(`数据库文件不存在: ${dbPath}`);
                return false;
            }
            
            const testQuery = await this.execCommand(`"${sqlitePath}" "${dbPath}" "SELECT 1;" 2>&1`);
            if (testQuery.stderr && testQuery.stderr.includes('Error')) {
                console.log(`数据库查询失败: ${dbPath}`);
                return false;
            }
            
            console.log(`数据库路径可用: ${dbPath}`);
            return true;
            
        } catch (error) {
            console.debug(`检查数据库失败 ${dbPath}:`, error.message);
            return false;
        }
    }
    
    // 获取可用数据库路径方法
    async getAvailableDatabasePath() {
        const sqlitePath = '/data/adb/modules/RemoteConfigOverride/tool/sqlite3';
        const dbPaths = [
            '/data/user_de/0/com.oplus.cosa/databases/db_game_database',
            '/data/user/0/com.oplus.cosa/databases/db_game_database'
        ];
        
        console.log('开始检测数据库路径...');
        
        for (const dbPath of dbPaths) {
            console.log(`尝试检测路径: ${dbPath}`);
            try {
                const isAvailable = await this.checkDatabaseAndSqlite(sqlitePath, dbPath);
                if (isAvailable) {
                    console.log(`找到可用数据库: ${dbPath}`);
                    return dbPath;
                }
            } catch (error) {
                console.log(`检测路径 ${dbPath} 时出错:`, error.message);
                continue;
            }
        }
        
        console.log('所有数据库路径都不可用');
        return null;
    }
    
    // 提取本地配置方法
    async extractLocalConfigs() {
        try {
            this.updateStatus('正在准备提取配置...', 'loading');
            
            const mode = await this.showModal({
                type: 'mode-select',
                title: '选择提取模式'
            });
            
            if (!mode) {
                this.updateStatus('操作已取消', 'ready');
                return;
            }
            
            const modeText = mode === '1' ? '整理模式' : '原始模式';
            
            // 如果是模式1，显示包名选择器
            if (mode === '1') {
                const result = await this.showModal({
                    type: 'package-select',
                    title: '请选择要提取的游戏包名:'
                });
                
                if (!result) {
                    this.updateStatus('操作已取消', 'ready');
                    return;
                }
                
                if (result === 'all') {
                    await this.executeYtCommand('1', modeText + ' - 全部包名');
                } else if (result) {
                    await this.executeYtCommand(`1 ${result}`, modeText + ' - ' + result);
                } else {
                    this.updateStatus('请选择一个包名', 'error');
                    return;
                }
            } else {
                // 模式2直接执行
                await this.executeYtCommand('2', modeText);
            }
            
        } catch (e) {
            console.error('提取配置失败:', e);
            this.updateStatus('提取失败: ' + e.message, 'error');
            
            await this.showModal({
                type: 'alert',
                title: '提取失败',
                message: `❌ 错误: ${e.message || '未知错误'}`
            });
        }
    }
    
    // 执行yt命令
    async executeYtCommand(commandArgs, modeText) {
        try {
            this.updateStatus(`正在执行${modeText}...`, 'loading');
            
            const YT_TOOL_PATH = '/data/adb/modules/RemoteConfigOverride/tool/yt';
            
            const command = `"${YT_TOOL_PATH}" ${commandArgs}`;
            console.log('执行命令:', command);
            
            const { stdout, stderr } = await this.execCommand(command);
            
            if (stderr && stderr.includes('Error')) {
                throw new Error(stderr);
            }
            
            let message = `${modeText}执行成功！\n\n`;
            
            if (stdout && stdout.trim()) {
                message += `📋 执行输出:\n${stdout}`;
            }
            
            if (stderr && stderr.trim()) {
                message += `\n\n⚠️ 警告信息:\n${stderr}`;
            }
            
            await this.showModal({
                type: 'alert',
                title: '提取完成',
                message: message
            });
            
            this.updateStatus(`${modeText}执行完成`, 'success');
            
        } catch (e) {
            throw e;
        }
    }
    
    async loadConfig() {
        try {
            const selectedPath = await this.showModal({
                type: 'file-select',
                title: '选择配置文件',
                message: '请从列表中选择一个配置文件:'
            });
            
            if (!selectedPath) return;
            
            // 保存当前配置文件路径
            this.currentConfigPath = selectedPath;
            
            this.updateStatus('正在加载...', 'loading');
            
            const { stdout } = await this.execCommand(`cat "${this.currentConfigPath}"`);
            if (!stdout) throw new Error('配置文件为空');
            
            // 使用原生JSON解析，不再使用自定义解析功能
            this.config = JSON.parse(stdout);
            this.originalConfig = JSON.parse(JSON.stringify(this.config));
            
            this.renderConfig();
            
            this.updateStatus('配置加载成功', 'success');
            
            setTimeout(() => {
                const otherBtn = document.querySelector('.filter-btn[data-filter="other"]');
                if (otherBtn) otherBtn.click();
            }, 100);
            
            return true;
        } catch (e) {
            console.error('加载失败:', e);
            this.updateStatus('加载失败: ' + e.message, 'error');
            
            this.showModal({
                type: 'alert',
                title: '加载失败',
                message: e.message || '未知错误'
            });
            
            return false;
        }
    }
    
    async saveConfig() {
        try {
            this.updateStatus('正在保存...', 'loading');
            
            if (!this.config) {
                throw new Error('没有可保存的配置');
            }
            
            // 检查是否有当前配置文件路径
            if (!this.currentConfigPath) {
                throw new Error('未指定配置文件路径，请先加载配置文件');
            }
            
            const tempPath = '/data/local/tmp/config_temp.json';
            // 使用原生JSON序列化，不再使用自定义序列化功能
            const configStr = JSON.stringify(this.config, null, 2);
            
            const configDir = this.currentConfigPath.substring(0, this.currentConfigPath.lastIndexOf('/'));
            await this.execCommand(`mkdir -p "${configDir}"`);
            
            await this.execCommand(`echo '${configStr.replace(/'/g, "'\\''")}' > "${tempPath}"`);
            
            await this.execCommand(`mv -f "${tempPath}" "${this.currentConfigPath}"`);
            
            this.updateStatus('保存成功', 'success');
            return true;
        } catch (e) {
            console.error('保存失败:', e);
            this.updateStatus('保存失败: ' + e.message, 'error');
            
            this.showModal({
                type: 'alert',
                title: '保存失败',
                message: e.message || '未知错误'
            });
            
            return false;
        }
    }
    
    // 重置配置功能已移除
    
    renderConfig() {
        const configContainer = document.getElementById('config-container');
        if (!configContainer) {
            console.error('Config container not found');
            return;
        }
        
        configContainer.innerHTML = '';
        
        if (!this.config) {
            configContainer.innerHTML = '<div class="config-item">没有可用的配置数据</div>';
            return;
        }
        
        this.addEditableItem(configContainer, 'package_name', this.config.package_name);
        this.addEditableItem(configContainer, 'feature_flag', this.config.feature_flag);
        
        if (this.config.game_zone) {
            const gz = this.config.game_zone;
            this.addEditableItem(configContainer, 'game_zone.white_list', gz.white_list);
            
            if (gz.bind_list) {
                this.addEditableItem(configContainer, 'game_zone.bind_list', gz.bind_list, true);
            }
            
            this.addEditableItem(configContainer, 'game_zone.fixed_critical_task', gz.fixed_critical_task);
        }
        
        if (this.config.gpu_config) {
            for (const gpuId in this.config.gpu_config) {
                const gpu = this.config.gpu_config[gpuId];
                this.addEditableItem(configContainer, `gpu_config.${gpuId}.c0`, gpu.c0);
                this.addEditableItem(configContainer, `gpu_config.${gpuId}.c1`, gpu.c1);
                this.addEditableItem(configContainer, `gpu_config.${gpuId}.c2`, gpu.c2);
                this.addEditableItem(configContainer, `gpu_config.${gpuId}.fps`, gpu.fps);
            }
        }
        
        if (this.config.gpa_config) {
            for (const gpaId in this.config.gpa_config) {
                const gpa = this.config.gpa_config[gpaId];
                this.addEditableItem(configContainer, `gpa_config.${gpaId}.cl`, gpa.cl);
                this.addEditableItem(configContainer, `gpa_config.${gpaId}.ch`, gpa.ch);
                this.addEditableItem(configContainer, `gpa_config.${gpaId}.sm`, gpa.sm);
                this.addEditableItem(configContainer, `gpa_config.${gpaId}.gf`, gpa.gf);
                this.addEditableItem(configContainer, `gpa_config.${gpaId}.gm`, gpa.gm);
                
                if (gpa.mema) {
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.beta`, gpa.mema.beta, false, `${gpaId}.beta:0-5`);
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.mode`, gpa.mema.mode, false, `${gpaId}.mode:0-5`);
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.tl`, gpa.mema.tl, false, `${gpaId}.tl:0-5`);
                    
                    if (gpa.mema.custom) {
                        for (const customKey in gpa.mema.custom) {
                            const customValue = gpa.mema.custom[customKey];
                            if (typeof customValue === 'object' && customValue !== null) {
                                if (customValue.mode !== undefined) {
                                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.custom.${customKey}.mode`, customValue.mode, false, `${gpaId}.mode:6-7`);
                                }
                                if (customValue.tl !== undefined) {
                                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.custom.${customKey}.tl`, customValue.tl, false, `${gpaId}.tl:6-7`);
                                }
                                if (customValue.beta !== undefined) {
                                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.mema.custom.${customKey}.beta`, customValue.beta, false, `${gpaId}.beta:6-7`);
                                }
                            }
                        }
                    }
                }
                
                if (gpa.es4g) {
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.es4g.state`, gpa.es4g.state);
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.es4g.clist`, gpa.es4g.clist);
                    this.addEditableItem(configContainer, `gpa_config.${gpaId}.es4g.fps`, gpa.es4g.fps);
                }
            }
        }
        
        // 将freqstep独立显示在最后
        if (this.config.fps_stabilizer?.freqStep) {
            this.addEditableItem(configContainer, 'fps_stabilizer.freqStep', this.config.fps_stabilizer.freqStep);
        }
    }
    
    // 注释功能修复：正确处理displayName的注释查找
    getConfigComment(keyPath, displayName = null) {
        const comments = {
            'package_name': '这里填写应用的包名，例如 com.example.app',
            'c0': 'GPU频率档位1',
            'c1': 'GPU频率档位2',
            'c2': 'GPU频率档位3',
            'fps': '目标帧率',
            'cl': '大核最低频率',
            'ch': '大核最高频率',
            'sm': '小核最大频率',
            'gf': '超大核最低频率 (默认2)',
            'gm': '超大核最高频率',
            'freqStep': 'freqstep是4个数值一组，分别对应性能不足升频时大核上限，大核下限，超大核上限，超大核下限(参照ch cl等值)。数值的组数对应booststep的数值数量，booststep中有几个数值，freqstep有几组对应的升频档位',
            'es4g.state': '启用scx调速器',
            'es4g.fps': '目标帧率',
            'es4g.clist': '使用的核心列表',
            'beta:0-5': '数字越大，就会越偏向更高的频率 (0-5核心)',
            'mode:0-5': '2模式会更加激进一点 (0-5核心)',
            'tl:0-5': '升频幅度和目标的最大负载压力，一般不动 (0-5核心)',
            'beta:6-7': '数字越大，就会越偏向更高的频率 (6-7核心)',
            'mode:6-7': '2模式会更加激进一点 (6-7核心)',
            'tl:6-7': '升频幅度和目标的最大负载压力，一般不动 (6-7核心)',
            'white_list': '白名单线程',
            'bind_list': '绑定 CPU 核心（g_0对应0-7核, g_10对应0-5核，g_11对应6-7核）',
            'fixed_critical_task': '关键线程，会自动绑定0-7'
        };
        
        // 如果提供了displayName，优先使用displayName查找
        if (displayName) {
            // 直接尝试匹配displayName
            if (comments[displayName]) {
                return comments[displayName];
            }
            
            // 对于如"144.tl:0-5"这样的displayName，尝试匹配最后的部分
            const displayParts = displayName.split('.');
            const lastDisplayPart = displayParts[displayParts.length - 1];
            if (comments[lastDisplayPart]) {
                return comments[lastDisplayPart];
            }
        }
        
        // 如果没有displayName或没有匹配到，使用keyPath查找
        if (comments[keyPath]) {
            return comments[keyPath];
        }
        
        // 尝试获取基本部分的注释
        const parts = keyPath.split('.');
        const lastPart = parts[parts.length - 1];
        
        // 对于es4g.state这种，直接匹配es4g.state
        if (parts.length >= 2) {
            const lastTwoParts = parts.slice(-2).join('.');
            if (comments[lastTwoParts]) {
                return comments[lastTwoParts];
            }
        }
        
        // 尝试匹配最后一个部分
        return comments[lastPart] || null;
    }
    
    addEditableItem(container, keyPath, value, isBindList = false, displayName = null) {
        const item = document.createElement('div');
        item.className = 'config-item';
        item.dataset.key = keyPath;
        
        const nameElement = document.createElement('div');
        nameElement.className = 'config-name';
        
        let finalDisplayName = displayName || keyPath;
        
        // 修改：保持显示名称的修改逻辑
        if (finalDisplayName.startsWith('gpa_config.')) {
            // 移除 "gpa_config." 前缀用于显示
            finalDisplayName = finalDisplayName.replace('gpa_config.', '');
        } 
        // 其他配置保持原样
        else if (finalDisplayName.startsWith('gpu_config.')) {
            finalDisplayName = finalDisplayName.replace('gpu_config.', '');
        } else if (finalDisplayName.startsWith('game_zone.')) {
            finalDisplayName = finalDisplayName.replace('game_zone.', '');
        } else if (finalDisplayName.startsWith('fps_stabilizer.')) {
            finalDisplayName = finalDisplayName.replace('fps_stabilizer.', '');
        }
        
        nameElement.textContent = finalDisplayName;
        
        // 修复：同时传入keyPath和displayName进行注释查找
        const comment = this.getConfigComment(keyPath, displayName);
        if (comment) {
            const commentElement = document.createElement('div');
            commentElement.className = 'config-comment';
            commentElement.textContent = comment;
            nameElement.appendChild(commentElement);
        }
        
        item.appendChild(nameElement);
        
        const valueElement = document.createElement('div');
        valueElement.className = 'config-value';
        
        const editor = document.createElement('div');
        editor.className = 'editable';
        editor.contentEditable = true;
        
        if (Array.isArray(value)) {
            editor.textContent = value.join(', ');
        } else if (typeof value === 'object' && value !== null && !isBindList) {
            // 使用原生JSON.stringify，不再使用自定义序列化
            editor.textContent = JSON.stringify(value, null, 2);
        } else if (isBindList) {
            const bindListEntries = [];
            for (const [key, val] of Object.entries(value)) {
                bindListEntries.push(`${key}: ${val}`);
            }
            editor.textContent = bindListEntries.join(', ');
        } else {
            editor.textContent = value;
        }
        
        editor.addEventListener('blur', () => {
            try {
                let newValue;
                if (isBindList) {
                    const entries = editor.textContent.split(',').map(entry => entry.trim()).filter(entry => entry);
                    const bindListObj = {};
                    const duplicateKeys = [];
                    
                    entries.forEach(entry => {
                        const match = entry.match(/^([^:]+)[:\s]+(.+)$/);
                        if (match) {
                            const key = match[1].trim();
                            const val = match[2].trim();
                            if (key && val) {
                                if (bindListObj.hasOwnProperty(key)) {
                                    duplicateKeys.push(key);
                                }
                                bindListObj[key] = val;
                            }
                        } else {
                            throw new Error(`格式错误: "${entry}" - 应为"键: 值"格式`);
                        }
                    });
                    
                    if (duplicateKeys.length > 0) {
                        throw new Error(`存在重复的键: ${duplicateKeys.join(', ')}`);
                    }
                    
                    newValue = bindListObj;
                    
                    const formattedEntries = [];
                    for (const [key, val] of Object.entries(bindListObj)) {
                        formattedEntries.push(`${key}: ${val}`);
                    }
                    editor.textContent = formattedEntries.join(', ');
                } else {
                    if (Array.isArray(value)) {
                        newValue = editor.textContent.split(',').map(s => s.trim());
                    } else if (typeof value === 'number') {
                        newValue = Number(editor.textContent);
                    } else if (typeof value === 'boolean') {
                        newValue = editor.textContent.toLowerCase() === 'true';
                    } else if (typeof value === 'object' && value !== null) {
                        // 使用原生JSON.parse，不再使用自定义解析
                        newValue = JSON.parse(editor.textContent);
                    } else {
                        newValue = editor.textContent;
                    }
                }
                
                this.updateConfigValue(keyPath, newValue);
            } catch (e) {
                if (Array.isArray(value)) {
                    editor.textContent = value.join(', ');
                } else if (typeof value === 'object' && value !== null && !isBindList) {
                    editor.textContent = JSON.stringify(value, null, 2);
                } else if (isBindList) {
                    const bindListEntries = [];
                    for (const [key, val] of Object.entries(value)) {
                        bindListEntries.push(`${key}: ${val}`);
                    }
                    editor.textContent = bindListEntries.join(', ');
                } else {
                    editor.textContent = value;
                }
                
                this.showModal({
                    type: 'alert',
                    title: '格式错误',
                    message: e.message
                });
            }
        });
        
        valueElement.appendChild(editor);
        item.appendChild(valueElement);
        container.appendChild(item);
    }
    
    updateConfigValue(keyPath, value) {
        if (keyPath.startsWith('fps_stabilizer.')) {
            if (!this.config.fps_stabilizer) {
                this.config.fps_stabilizer = {};
            }
            this.config.fps_stabilizer.freqStep = value;
            return;
        }
        
        const keys = keyPath.split('.');
        let obj = this.config;
        
        for (let i = 0; i < keys.length - 1; i++) {
            if (!obj[keys[i]]) obj[keys[i]] = {};
            obj = obj[keys[i]];
        }
        
        obj[keys[keys.length - 1]] = value;
    }
    
    updateStatus(text, status) {
        const statusEl = document.getElementById('status');
        if (statusEl) {
            statusEl.textContent = text;
            statusEl.className = `status-indicator ${status}`;
        }
    }
    
    async execCommand(command) {
        return new Promise((resolve, reject) => {
            const callbackId = `ksu_${Date.now()}`;
            
            window[callbackId] = (errno, stdout, stderr) => {
                delete window[callbackId];
                if (errno === 0) {
                    resolve({ errno, stdout, stderr });
                } else {
                    reject(new Error(stderr || `错误码: ${errno}`));
                }
            };
            
            try {
                ksu.exec(command, JSON.stringify({}), callbackId);
            } catch (e) {
                delete window[callbackId];
                reject(e);
            }
        });
    }
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    const editor = new ConfigEditor();
    
    // 按钮事件
    document.getElementById('load-btn').addEventListener('click', () => editor.loadConfig());
    document.getElementById('save-btn').addEventListener('click', () => editor.saveConfig());
    // reset-btn现在用于提取配置功能
    document.getElementById('reset-btn').addEventListener('click', () => editor.extractLocalConfigs());
    
    // 筛选按钮事件
    const filterButtons = document.querySelectorAll('.filter-btn');
    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');
            
            const filter = button.dataset.filter;
            const items = document.querySelectorAll('.config-container .config-item');
            
            items.forEach(item => {
                const key = item.dataset.key || '';
                const shouldShow = 
                    (filter === 'gpu' && key.startsWith('gpu_config')) || 
                    (filter === 'gpa' && key.startsWith('gpa_config')) || 
                    (filter === 'game_zone' && key.startsWith('game_zone')) ||
                    (filter === 'other' && !key.startsWith('gpu_config') && 
                     !key.startsWith('gpa_config') && 
                     !key.startsWith('game_zone'));
                
                item.style.display = shouldShow ? '' : 'none';
            });
        });
    });
    
    // 不再自动加载配置，等待用户点击加载按钮
    // 初始加载配置已移除
});