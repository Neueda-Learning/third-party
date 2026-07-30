/* ================================================================
   main.js
================================================================ */
'use strict';

let listPage = 0;
let listSize = 20;
let listStatus = '';
let listLoaded = false;

document.addEventListener('DOMContentLoaded', () => {
    initNav();
    initCreateForm();
    initListView();
    initDetailView();
    genIdempotencyKey();
});

// ── Navigation ───────────────────────────────────────────────────
function initNav() {
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', e => {
            e.preventDefault();
            switchView(item.dataset.view);
        });
    });
}

function switchView(viewId) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.getElementById('view-' + viewId)?.classList.add('active');
    document.querySelector(`.nav-item[data-view="${viewId}"]`)?.classList.add('active');
    if (viewId === 'list') loadList();
}

// ── Idempotency key ───────────────────────────────────────────────
function genIdempotencyKey() {
    const key = `pay-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    document.getElementById('idempotencyKey').value = key;
}

// ── Create ────────────────────────────────────────────────────────
function initCreateForm() {
    document.getElementById('regenKeyBtn').addEventListener('click', genIdempotencyKey);

    document.getElementById('createForm').addEventListener('reset', () => {
        setTimeout(genIdempotencyKey, 0);
        hideFeedback('createFeedback');
    });

    document.getElementById('createForm').addEventListener('submit', async e => {
        e.preventDefault();
        const btn = e.target.querySelector('[type=submit]');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span> 提交中…';

        const body = {
            sourceAccount:      document.getElementById('sourceAccount').value.trim(),
            destinationAccount: document.getElementById('destinationAccount').value.trim(),
            amount:             parseFloat(document.getElementById('amount').value),
            currency:           'CNY',
            reference:          document.getElementById('reference').value.trim() || undefined,
        };
        const idempotencyKey = document.getElementById('idempotencyKey').value.trim();

        try {
            const result = await PaymentAPI.createPayment(body, idempotencyKey);
            if (result.code === 'SUCCESS') {
                const isNew = result.status === 201;
                showFeedback('createFeedback', 'success',
                    isNew
                        ? `<i class="fa-solid fa-circle-check"></i> 支付创建成功，ID: <strong>${result.data.id}</strong>，状态: ${result.data.status}`
                        : `<i class="fa-solid fa-circle-info"></i> 幂等返回已存在支付，ID: <strong>${result.data.id}</strong>`
                );
                document.getElementById('createForm').reset();
            } else if (result.code === 'CONFLICT') {
                showFeedback('createFeedback', 'error',
                    `<i class="fa-solid fa-circle-xmark"></i> 幂等键冲突：${result.data?.message}`);
            } else {
                showFeedback('createFeedback', 'error',
                    `<i class="fa-solid fa-circle-xmark"></i> ${result.data?.message ?? '提交失败'}`);
            }
        } catch (err) {
            showFeedback('createFeedback', 'error',
                `<i class="fa-solid fa-circle-xmark"></i> 请求失败：${err.message}`);
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-paper-plane"></i> 提交';
        }
    });
}

// ── List ─────────────────────────────────────────────────────────
function initListView() {
    document.getElementById('queryBtn').addEventListener('click', () => { listPage = 0; loadList(); });
    document.getElementById('statusFilter').addEventListener('change', () => { listPage = 0; loadList(); });
    document.getElementById('pageSize').addEventListener('change', () => { listPage = 0; loadList(); });
}

async function loadList() {
    listStatus = document.getElementById('statusFilter').value;
    listSize   = parseInt(document.getElementById('pageSize').value);

    const tbody = document.getElementById('tableBody');
    tbody.innerHTML = `<tr><td colspan="8" class="table-placeholder"><span class="spinner"></span> 加载中…</td></tr>`;
    document.getElementById('totalCount').textContent = '';
    document.getElementById('pagination').innerHTML = '';

    try {
        const result = await PaymentAPI.listPayments(listPage, listSize, listStatus);
        if (result.code !== 'SUCCESS') {
            tbody.innerHTML = `<tr><td colspan="8" class="table-placeholder">查询失败：${result.data?.message ?? '未知错误'}</td></tr>`;
            return;
        }
        const { content, totalElements, totalPages } = result.data;
        renderTable(content);
        document.getElementById('totalCount').textContent = `共 ${totalElements ?? content.length} 条`;
        renderPagination(totalPages, listPage);
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="8" class="table-placeholder">请求失败：${err.message}</td></tr>`;
    }
}

function renderTable(rows) {
    const tbody = document.getElementById('tableBody');
    if (!rows || rows.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="table-placeholder">暂无数据</td></tr>`;
        return;
    }
    tbody.innerHTML = rows.map(p => `
        <tr>
            <td><strong>${p.id}</strong></td>
            <td><span class="idem-key" title="${p.idempotencyKey}">${p.idempotencyKey}</span></td>
            <td>${p.sourceAccount}</td>
            <td>${p.destinationAccount}</td>
            <td>${p.amount}</td>
            <td>${statusBadge(p.status)}</td>
            <td>${fmtDate(p.createdAt)}</td>
            <td>
                <button class="btn btn-ghost btn-sm" onclick="openDetail(${p.id})">
                    <i class="fa-solid fa-eye"></i> 详情
                </button>
            </td>
        </tr>`).join('');
}

function renderPagination(totalPages, cur) {
    const el = document.getElementById('pagination');
    if (totalPages <= 1) { el.innerHTML = ''; return; }

    let pages = [];
    if (totalPages <= 7) {
        for (let i = 0; i < totalPages; i++) pages.push(i);
    } else {
        pages = [0, 1];
        for (let i = Math.max(2, cur - 1); i <= Math.min(totalPages - 3, cur + 1); i++) pages.push(i);
        pages.push(totalPages - 2, totalPages - 1);
        pages = [...new Set(pages)].sort((a, b) => a - b);
    }

    let html = `<span class="pagination-info">第 ${cur + 1} / ${totalPages} 页</span>`;
    html += `<button ${cur === 0 ? 'disabled' : ''} onclick="gotoPage(${cur - 1})">
                <i class="fa-solid fa-chevron-left"></i>
             </button>`;
    let prev = -1;
    for (const p of pages) {
        if (prev >= 0 && p - prev > 1) html += `<button disabled>…</button>`;
        html += `<button class="${p === cur ? 'active' : ''}" onclick="gotoPage(${p})">${p + 1}</button>`;
        prev = p;
    }
    html += `<button ${cur >= totalPages - 1 ? 'disabled' : ''} onclick="gotoPage(${cur + 1})">
                <i class="fa-solid fa-chevron-right"></i>
             </button>`;
    el.innerHTML = html;
}

function gotoPage(p) { listPage = p; loadList(); }

// ── Detail ───────────────────────────────────────────────────────
function initDetailView() {
    const go = () => {
        const id = parseInt(document.getElementById('detailId').value);
        if (id > 0) loadDetail(id);
    };
    document.getElementById('detailQueryBtn').addEventListener('click', go);
    document.getElementById('detailId').addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
}

window.openDetail = function(id) {
    switchView('detail');
    document.getElementById('detailId').value = id;
    loadDetail(id);
};

async function loadDetail(id) {
    const container = document.getElementById('detailBody');
    container.innerHTML = `<div class="detail-loading"><span class="spinner"></span> 加载中…</div>`;

    try {
        const [detailRes, historyRes] = await Promise.all([
            PaymentAPI.getPaymentDetail(id),
            PaymentAPI.getPaymentHistory(id),
        ]);

        if (detailRes.code !== 'SUCCESS') {
            container.innerHTML = `<div class="detail-loading" style="color:#991b1b">
                <i class="fa-solid fa-circle-xmark"></i> ${detailRes.data?.message ?? '查询失败'}
            </div>`;
            return;
        }

        const p = detailRes.data;
        const errorRows = p.errorCode
            ? `${cell('错误码', p.errorCode)}${cell('错误信息', p.errorMessage ?? '—')}`
            : '';

        container.innerHTML = `
            <div class="detail-result">
                <div class="detail-result-title">
                    <span class="detail-result-id">支付 #${p.id}</span>
                    ${statusBadge(p.status)}
                    <span class="detail-result-time">${fmtDate(p.updatedAt)}</span>
                </div>
                <div class="detail-info-grid">
                    ${cell('付款账户', p.sourceAccount)}
                    ${cell('收款账户', p.destinationAccount)}
                    ${cell('金额', `${p.amount} ${p.currency}`)}
                    ${cell('参考号', p.reference || '—')}
                    ${cell('创建时间', fmtDate(p.createdAt))}
                    ${cell('更新时间', fmtDate(p.updatedAt))}
                    ${errorRows}
                    ${cellFull('幂等键', `<span class="mono-text">${p.idempotencyKey}</span>`)}
                </div>
                ${renderHistory(historyRes)}
            </div>
        `;
    } catch (err) {
        container.innerHTML = `<div class="detail-loading" style="color:#991b1b">
            <i class="fa-solid fa-circle-xmark"></i> 请求失败：${err.message}
        </div>`;
    }
}

function renderHistory(historyRes) {
    if (historyRes.code !== 'SUCCESS' || !historyRes.data?.length) return '';
    const items = historyRes.data.map(h => `
        <div class="timeline-entry">
            <div class="timeline-dot"></div>
            <div class="timeline-content">
                <div class="timeline-status">${h.fromStatus || '初始'} → ${h.toStatus}</div>
                <div class="timeline-meta">
                    <span>${fmtDate(h.createdAt)}</span>
                    ${h.reason ? `<span>${h.reason}</span>` : ''}
                    ${h.triggeredBy ? `<span>触发方：${h.triggeredBy}</span>` : ''}
                </div>
            </div>
        </div>`).join('');
    return `
        <div class="detail-history">
            <div class="history-title"><i class="fa-solid fa-timeline"></i> 状态变更历史</div>
            <div class="timeline-list">${items}</div>
        </div>`;
}

// ── Helpers ───────────────────────────────────────────────────────
function cell(label, value, full = false) {
    return `<div class="detail-cell${full ? ' detail-cell-full' : ''}">
        <div class="detail-cell-label">${label}</div>
        <div class="detail-cell-value">${value}</div>
    </div>`;
}
function cellFull(label, value) { return cell(label, value, true); }

function statusBadge(s) {
    const map = {
        CREATED:   ['badge-created',   '已创建'],
        VALIDATED: ['badge-validated', '已验证'],
        SENT:      ['badge-sent',      '已发送'],
        COMPLETED: ['badge-completed', '已完成'],
        FAILED:    ['badge-failed',    '失败'],
    };
    const [cls, text] = map[s] || ['badge-created', s];
    return `<span class="badge ${cls}">${text}</span>`;
}

function fmtDate(s) {
    if (!s) return '—';
    return new Date(s).toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
}

function showFeedback(id, type, html) {
    const el = document.getElementById(id);
    el.className = `feedback feedback-${type}`;
    el.innerHTML = html;
    el.style.display = 'flex';
    if (type === 'success') setTimeout(() => hideFeedback(id), 6000);
}

function hideFeedback(id) {
    const el = document.getElementById(id);
    if (el) { el.style.display = 'none'; el.innerHTML = ''; }
}
