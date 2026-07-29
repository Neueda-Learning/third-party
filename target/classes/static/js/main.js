/**
 * 主业务逻辑模块
 * 处理页面交互和API调用
 */

let currentPage = 0;
let currentPageSize = 20;
let currentStatus = '';

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', function () {
    initPageEvents();
    showModule('create');
    generateIdempotencyKey();
});

/**
 * 初始化页面事件
 */
function initPageEvents() {
    // 导航菜单
    document.querySelectorAll('nav a').forEach(link => {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            const target = this.getAttribute('href');
            showModule(target.substring(1));
        });
    });

    // 创建支付表单
    document.getElementById('createForm').addEventListener('submit', handleCreatePayment);
    document.getElementById('generateKeyBtn').addEventListener('click', generateIdempotencyKey);

    // 列表查询
    document.getElementById('queryBtn').addEventListener('click', handleListPayments);
    document.getElementById('statusFilter').addEventListener('change', () => {
        currentPage = 0;
        handleListPayments();
    });
    document.getElementById('pageSize').addEventListener('change', () => {
        currentPage = 0;
        handleListPayments();
    });

    // 详情查询
    document.getElementById('detailQueryBtn').addEventListener('click', handleGetDetail);
    document.getElementById('detailPaymentId').addEventListener('keypress', function (e) {
        if (e.key === 'Enter') {
            handleGetDetail();
        }
    });
}

/**
 * 模块切换
 */
function showModule(moduleName) {
    document.querySelectorAll('.module').forEach(m => m.classList.remove('active'));
    const targetModule = document.getElementById(moduleName);
    if (targetModule) {
        targetModule.classList.add('active');
    }

    document.querySelectorAll('nav a').forEach(a => a.classList.remove('active'));
    document.querySelector(`nav a[href="#${moduleName}"]`)?.classList.add('active');
}

/**
 * 生成幂等键
 */
function generateIdempotencyKey() {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 9);
    const key = `payment-${timestamp}-${random}`;
    document.getElementById('idempotencyKey').value = key;
}

/**
 * 处理创建支付
 */
async function handleCreatePayment(e) {
    e.preventDefault();

    const sourceAccount = document.getElementById('sourceAccount').value.trim();
    const destinationAccount = document.getElementById('destinationAccount').value.trim();
    const amount = document.getElementById('amount').value.trim();
    const currency = 'CNY';
    const reference = document.getElementById('reference').value.trim();
    const idempotencyKey = document.getElementById('idempotencyKey').value.trim();

    if (!sourceAccount || !destinationAccount || !amount) {
        showResult('createResult', '请填写所有必需字段', 'error');
        return;
    }

    const paymentData = {
        sourceAccount,
        destinationAccount,
        amount: parseFloat(amount),
        currency,
        reference: reference || undefined
    };

    try {
        const result = await PaymentAPI.createPayment(paymentData, idempotencyKey);

        if (result.code === 'SUCCESS') {
        console.log('创建支付结果:', result.status, result.data);
            const isNew =  result.status === 201;
            const message = isNew
                ? `✓ 支付创建成功! ID: ${result.data.id}`
                : `✓ 重复请求，返回已存在的支付! ID: ${result.data.id}`;

            showResult('createResult', message, 'success');
            showPaymentDetail(result.data);

            // 重置表单
            document.getElementById('createForm').reset();
            generateIdempotencyKey();

        } else if (result.code === 'CONFLICT') {
            showResult('createResult', `✗ 幂等键冲突: ${result.data.message}`, 'error');
        } else {
            showResult('createResult', `✗ 创建失败: ${result.data.message}`, 'error');
        }
    } catch (err) {
        showResult('createResult', `✗ 请求失败: ${err.message}`, 'error');
    }
}

/**
 * 处理列表查询
 */
async function handleListPayments() {
    const statusFilter = document.getElementById('statusFilter').value;
    const pageSizeSelect = document.getElementById('pageSize').value;
    currentPageSize = parseInt(pageSizeSelect);
    currentStatus = statusFilter;

    const result = await PaymentAPI.listPayments(currentPage, currentPageSize, statusFilter);

    if (result.code === 'SUCCESS') {
        renderPaymentTable(result.data.content);
        renderPagination(result.data.totalPages, currentPage);
    } else {
        showAlert('paymentTable', `查询失败: ${result.data.message}`, 'error');
    }
}

/**
 * 渲染支付表格
 */
function renderPaymentTable(payments) {
    const tbody = document.querySelector('#paymentTable tbody');
    tbody.innerHTML = '';

    if (!payments || payments.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="loading">暂无数据</td></tr>';
        return;
    }

    payments.forEach(payment => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${payment.id}</td>
            <td>${payment.idempotencyKey}</td>
            <td>${payment.sourceAccount}</td>
            <td>${payment.destinationAccount}</td>
            <td>${payment.amount}</td>
            <td><span class="status ${payment.status}">${getStatusLabel(payment.status)}</span></td>
            <td>${formatDateTime(payment.createdAt)}</td>
            <td>
                <button class="btn btn-outline" onclick="loadPaymentDetail(${payment.id})">详情</button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

/**
 * 渲染分页
 */
function renderPagination(totalPages, currentPage) {
    const paginationDiv = document.getElementById('pagination');
    paginationDiv.innerHTML = '';

    if (totalPages <= 1) return;

    // 上一页
    if (currentPage > 0) {
        const prevBtn = document.createElement('button');
        prevBtn.textContent = '上一页';
        prevBtn.onclick = () => {
            currentPage--;
            handleListPayments();
        };
        paginationDiv.appendChild(prevBtn);
    }

    // 页码
    for (let i = 0; i < totalPages; i++) {
        const pageBtn = document.createElement('button');
        pageBtn.textContent = (i + 1).toString();
        if (i === currentPage) {
            pageBtn.classList.add('active');
        }
        pageBtn.onclick = () => {
            currentPage = i;
            handleListPayments();
        };
        paginationDiv.appendChild(pageBtn);
    }

    // 下一页
    if (currentPage < totalPages - 1) {
        const nextBtn = document.createElement('button');
        nextBtn.textContent = '下一页';
        nextBtn.onclick = () => {
            currentPage++;
            handleListPayments();
        };
        paginationDiv.appendChild(nextBtn);
    }
}

/**
 * 处理详情查询
 */
async function handleGetDetail() {
    const paymentIdInput = document.getElementById('detailPaymentId');
    const paymentId = parseInt(paymentIdInput.value.trim());

    if (!paymentId) {
        showAlert('detailContent', '请输入支付ID', 'error');
        return;
    }

    await loadPaymentDetail(paymentId);
}

/**
 * 加载支付详情
 */
async function loadPaymentDetail(paymentId) {
    const result = await PaymentAPI.getPaymentDetail(paymentId);

    if (result.code === 'SUCCESS') {
        showPaymentDetail(result.data);
        showModule('detail');
        document.getElementById('detailPaymentId').value = paymentId;

        // 加载历史记录
        const historyResult = await PaymentAPI.getPaymentHistory(paymentId);
        if (historyResult.code === 'SUCCESS') {
            renderPaymentHistory(historyResult.data);
        }

        // 显示操作按钮
        renderOperationButtons(result.data);
    } else {
        showAlert('detailContent', `查询失败: ${result.data.message}`, 'error');
    }
}

/**
 * 显示支付详情
 */
function showPaymentDetail(payment) {
    const detailContent = document.getElementById('detailContent');
    detailContent.innerHTML = `
        <div class="detail-content">
            <div class="detail-item">
                <span class="detail-label">支付ID:</span>
                <span class="detail-value">${payment.id}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">幂等键:</span>
                <span class="detail-value">${payment.idempotencyKey}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">付款账户:</span>
                <span class="detail-value">${payment.sourceAccount}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">收款账户:</span>
                <span class="detail-value">${payment.destinationAccount}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">金额:</span>
                <span class="detail-value">${payment.amount} ${payment.currency}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">状态:</span>
                <span class="detail-value"><span class="status ${payment.status}">${getStatusLabel(payment.status)}</span></span>
            </div>
            ${payment.errorCode ? `
            <div class="detail-item">
                <span class="detail-label">错误码:</span>
                <span class="detail-value">${payment.errorCode}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">错误信息:</span>
                <span class="detail-value">${payment.errorMessage}</span>
            </div>
            ` : ''}
            <div class="detail-item">
                <span class="detail-label">参考号:</span>
                <span class="detail-value">${payment.reference || '-'}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">创建时间:</span>
                <span class="detail-value">${formatDateTime(payment.createdAt)}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">更新时间:</span>
                <span class="detail-value">${formatDateTime(payment.updatedAt)}</span>
            </div>
        </div>
    `;
}

/**
 * 渲染支付历史
 */
function renderPaymentHistory(histories) {
    const historySection = document.getElementById('historySection');
    const timeline = document.getElementById('historyTimeline');

    timeline.innerHTML = '';

    if (!histories || histories.length === 0) {
        historySection.style.display = 'none';
        return;
    }

    historySection.style.display = 'block';

    histories.forEach(record => {
        const item = document.createElement('div');
        item.className = 'timeline-item';
        item.innerHTML = `
            <div class="timeline-item-time">${formatDateTime(record.createdAt)}</div>
            <div class="timeline-item-status">
                ${record.fromStatus || '初始'} → ${record.toStatus}
            </div>
            <div class="timeline-item-reason">${record.reason || '-'}</div>
            <div class="timeline-item-triggered">触发方: ${record.triggeredBy}</div>
        `;
        timeline.appendChild(item);
    });
}

/**
 * 渲染操作按钮
 */
function renderOperationButtons(payment) {
    const operationsSection = document.getElementById('operationsSection');
    const operationButtons = document.getElementById('operationButtons');

    operationButtons.innerHTML = '';

    const buttons = [];

    // 根据当前状态显示可用的操作按钮
    if (payment.status === 'CREATED') {
        buttons.push({
            label: 'CREATED → VALIDATED',
            action: () => updatePaymentStatus(payment.id, 'validatePayment')
        });
    }

    if (payment.status === 'VALIDATED') {
        buttons.push({
            label: 'VALIDATED → SENT',
            action: () => updatePaymentStatus(payment.id, 'sendPayment')
        });
    }

    if (payment.status === 'SENT') {
        buttons.push({
            label: 'SENT → COMPLETED',
            action: () => updatePaymentStatus(payment.id, 'completePayment')
        });
        buttons.push({
            label: 'SENT → FAILED',
            action: () => updatePaymentStatus(payment.id, 'failPayment')
        });
    }

    // 如果还不是终态，允许直接标记为失败
    if (payment.status !== 'COMPLETED' && payment.status !== 'FAILED') {
        buttons.push({
            label: '标记为失败',
            action: () => updatePaymentStatus(payment.id, 'failPayment'),
            className: 'btn-danger'
        });
    }

    // 刷新按钮
    buttons.push({
        label: '刷新',
        action: () => loadPaymentDetail(payment.id)
    });

    buttons.forEach(btn => {
        const button = document.createElement('button');
        button.className = `btn ${btn.className || 'btn-primary'} operation-btn`;
        button.textContent = btn.label;
        button.onclick = btn.action;
        operationButtons.appendChild(button);
    });

    if (buttons.length > 0) {
        operationsSection.style.display = 'block';
    }
}

/**
 * 更新支付状态
 */
async function updatePaymentStatus(paymentId, action) {
    let result;

    switch (action) {
        case 'validatePayment':
            result = await PaymentAPI.validatePayment(paymentId);
            break;
        case 'sendPayment':
            result = await PaymentAPI.sendPayment(paymentId);
            break;
        case 'completePayment':
            result = await PaymentAPI.completePayment(paymentId);
            break;
        case 'failPayment':
            result = await PaymentAPI.failPayment(paymentId);
            break;
        default:
            return;
    }

    if (result.code === 'SUCCESS') {
        showAlert('detailContent', '✓ 状态更新成功', 'success');
        await loadPaymentDetail(paymentId);
    } else {
        showAlert('detailContent', `✗ 更新失败: ${result.data.message}`, 'error');
    }
}

/**
 * 显示提示信息
 */
function showResult(elementId, message, type) {
    const resultDiv = document.getElementById(elementId);
    resultDiv.textContent = message;
    resultDiv.className = `result show ${type}`;

    setTimeout(() => {
        resultDiv.classList.remove('show');
    }, 3000);
}

/**
 * 显示警告信息
 */
function showAlert(elementId, message, type) {
    const container = document.getElementById(elementId);
    if (container) {
        const alert = document.createElement('div');
        alert.className = `alert alert-${type}`;
        alert.textContent = message;
        container.insertBefore(alert, container.firstChild);

        setTimeout(() => {
            alert.remove();
        }, 5000);
    }
}

/**
 * 格式化日期时间
 */
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

/**
 * 获取状态标签
 */
function getStatusLabel(status) {
    const labels = {
        'CREATED': '已创建',
        'VALIDATED': '已验证',
        'SENT': '已发送',
        'COMPLETED': '已完成',
        'FAILED': '失败'
    };
    return labels[status] || status;
}
