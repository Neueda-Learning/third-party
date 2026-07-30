const state = {
    activeTab: "create",
    page: 0,
    pageSize: 20,
    status: "",
    selectedPaymentId: null,
    locale: normalizeLocale(localStorage.getItem("ui-locale")),
    selectedPayment: null,
    selectedHistory: []
};

const i18n = {
    zh: {
        "app.title": "支付处理系统",
        "app.subtitle": "快速创建支付、追踪状态与历史，围绕幂等与可追溯性设计。",
        "hero.loggedIn": "已登录用户",
        "hero.currency": "系统币种",
        "hero.scheduler": "调度周期",
        "hero.timeout": "超时阈值",
        "tab.create": "创建支付",
        "tab.list": "支付列表",
        "tab.detail": "支付详情",
        "tab.refresh": "刷新当前列表",
        "create.title": "创建支付",
        "create.desc": "填写关键字段并提交。系统基于幂等键防止重复创建。",
        "field.source": "付款账户",
        "field.destination": "收款账户",
        "field.amount": "金额（CNY）",
        "field.reference": "参考号 / 备注",
        "field.idempotency": "幂等键",
        "btn.regenerate": "重新生成",
        "btn.create": "创建支付",
        "btn.creating": "提交中...",
        "btn.fillDemo": "填充示例",
        "btn.reset": "重置",
        "btn.query": "查询",
        "btn.queryDetail": "查询详情",
        "btn.detail": "详情",
        "hint.default": "金额必须大于 0，且付款账户和收款账户不能相同。",
        "hint.valid": "字段已通过前端校验，可以提交。",
        "list.title": "支付列表",
        "list.desc": "支持状态筛选与分页。点击行可直接查看详情。",
        "list.status": "状态",
        "list.pageSize": "每页",
        "list.initial": "点击“查询”加载数据",
        "list.loading": "加载中...",
        "list.empty": "当前筛选条件下无数据",
        "detail.title": "支付详情",
        "detail.desc": "输入支付ID，查看当前状态、错误信息与完整状态轨迹。",
        "detail.flow": "状态流程",
        "detail.history": "状态变更历史",
        "detail.idLabel": "支付ID",
        "detail.empty": "尚未查询任何支付记录",
        "detail.loading": "正在加载支付详情...",
        "detail.invalidId": "请输入有效的支付ID",
        "detail.noReason": "无补充说明",
        "status.all": "全部",
        "col.source": "付款方",
        "col.destination": "收款方",
        "col.amount": "金额",
        "col.status": "状态",
        "col.createdAt": "创建时间",
        "col.action": "操作",
        "footer": "Thrid-Party | 2026",
        "ph.source": "例如 ACC001",
        "ph.destination": "例如 ACC002",
        "ph.amount": "1500.50",
        "ph.reference": "例如 INV-20260727",
        "ph.detailId": "输入支付ID，例如 1",
        "msg.requireFields": "请填写付款账户、收款账户和金额",
        "msg.accountSame": "付款账户和收款账户不能相同",
        "msg.amountInvalid": "金额必须大于 0",
        "msg.createOk": "支付创建成功，ID: {id}",
        "msg.createDup": "重复请求，返回已有记录，ID: {id}",
        "msg.queryFail": "查询失败：{message}",
        "msg.listFail": "列表加载失败：{message}",
        "msg.detailFail": "详情加载失败：{message}",
        "label.id": "支付ID",
        "label.idempotency": "幂等键",
        "label.source": "付款账户",
        "label.destination": "收款账户",
        "label.amount": "金额",
        "label.status": "当前状态",
        "label.reference": "参考号",
        "label.createdAt": "创建时间",
        "label.updatedAt": "更新时间",
        "label.errorCode": "错误码",
        "label.errorMessage": "错误信息",
        "label.initial": "初始",
        "flow.created": "创建",
        "flow.validated": "验证",
        "flow.sent": "发送",
        "flow.completed": "完成",
        "flow.failed": "失败",
        "flow.current": "当前状态",
        "flow.currentEn": "Current Status",
        "flow.currentZh": "当前状态"
    },
    en: {
        "app.title": "Payment Processing System",
        "app.subtitle": "Create payments, track status and history with idempotency-first design.",
        "hero.loggedIn": "Signed-in User",
        "hero.currency": "Currency",
        "hero.scheduler": "Scheduler",
        "hero.timeout": "Timeout",
        "tab.create": "Create",
        "tab.list": "Payments",
        "tab.detail": "Detail",
        "tab.refresh": "Refresh Current List",
        "create.title": "Create Payment",
        "create.desc": "Submit key fields. Idempotency key protects against duplicate creation.",
        "field.source": "Source Account",
        "field.destination": "Destination Account",
        "field.amount": "Amount (CNY)",
        "field.reference": "Reference / Note",
        "field.idempotency": "Idempotency Key",
        "btn.regenerate": "Regenerate",
        "btn.create": "Create Payment",
        "btn.creating": "Submitting...",
        "btn.fillDemo": "Fill Demo",
        "btn.reset": "Reset",
        "btn.query": "Query",
        "btn.queryDetail": "Get Detail",
        "btn.detail": "Detail",
        "hint.default": "Amount must be greater than 0, and source/destination accounts must differ.",
        "hint.valid": "Front-end validation passed. Ready to submit.",
        "list.title": "Payment List",
        "list.desc": "Filter by status and page through results. Click any row to open details.",
        "list.status": "Status",
        "list.pageSize": "Page Size",
        "list.initial": "Click \"Query\" to load data",
        "list.loading": "Loading...",
        "list.empty": "No results for current filters",
        "detail.title": "Payment Detail",
        "detail.desc": "Input payment ID to inspect status, errors and full transition history.",
        "detail.flow": "Status Flow",
        "detail.history": "Status History",
        "detail.idLabel": "Payment ID",
        "detail.empty": "No payment queried yet",
        "detail.loading": "Loading payment detail...",
        "detail.invalidId": "Please enter a valid payment ID",
        "detail.noReason": "No additional reason",
        "status.all": "All",
        "col.source": "Source",
        "col.destination": "Destination",
        "col.amount": "Amount",
        "col.status": "Status",
        "col.createdAt": "Created At",
        "col.action": "Action",
        "footer": "Thrid-Party | 2026",
        "ph.source": "e.g. ACC001",
        "ph.destination": "e.g. ACC002",
        "ph.amount": "1500.50",
        "ph.reference": "e.g. INV-20260727",
        "ph.detailId": "Enter payment ID, e.g. 1",
        "msg.requireFields": "Please fill source account, destination account and amount",
        "msg.accountSame": "Source and destination accounts cannot be the same",
        "msg.amountInvalid": "Amount must be greater than 0",
        "msg.createOk": "Payment created, ID: {id}",
        "msg.createDup": "Duplicate request, existing payment returned, ID: {id}",
        "msg.queryFail": "Query failed: {message}",
        "msg.listFail": "Failed to load list: {message}",
        "msg.detailFail": "Failed to load detail: {message}",
        "label.id": "Payment ID",
        "label.idempotency": "Idempotency Key",
        "label.source": "Source Account",
        "label.destination": "Destination Account",
        "label.amount": "Amount",
        "label.status": "Current Status",
        "label.reference": "Reference",
        "label.createdAt": "Created At",
        "label.updatedAt": "Updated At",
        "label.errorCode": "Error Code",
        "label.errorMessage": "Error Message",
        "label.initial": "Initial",
        "flow.created": "Created",
        "flow.validated": "Validated",
        "flow.sent": "Sent",
        "flow.completed": "Completed",
        "flow.failed": "Failed",
        "flow.current": "Current",
        "flow.currentEn": "Current Status",
        "flow.currentZh": "Current Status"
    }
};

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    applyLocale();
    generateIdempotencyKey();
    showTab("create");
});

function normalizeLocale(locale) {
    if (typeof locale !== "string") {
        return "zh";
    }
    const normalized = locale.trim().toLowerCase();
    if (normalized.startsWith("en")) {
        return "en";
    }
    return "zh";
}

function bindEvents() {
    document.querySelectorAll("[data-tab]").forEach((button) => {
        button.addEventListener("click", () => showTab(button.dataset.tab));
    });

    document.getElementById("createForm").addEventListener("submit", handleCreatePayment);
    document.getElementById("createForm").addEventListener("reset", () => {
        window.setTimeout(generateIdempotencyKey, 0);
        hideFeedback();
    });
    document.getElementById("generateKeyBtn").addEventListener("click", generateIdempotencyKey);
    document.getElementById("fillDemoBtn").addEventListener("click", fillDemoData);

    document.getElementById("queryBtn").addEventListener("click", () => loadPayments(true));
    document.getElementById("statusFilter").addEventListener("change", () => {
        state.status = document.getElementById("statusFilter").value;
        state.page = 0;
        loadPayments();
    });
    document.getElementById("pageSize").addEventListener("change", () => {
        state.pageSize = Number(document.getElementById("pageSize").value);
        state.page = 0;
        loadPayments();
    });
    document.getElementById("quickRefreshBtn").addEventListener("click", () => loadPayments());
    document.getElementById("languageToggleBtn").addEventListener("click", toggleLocale);

    document.getElementById("detailQueryBtn").addEventListener("click", handleDetailQuery);
    document.getElementById("detailPaymentId").addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            handleDetailQuery();
        }
    });

    ["sourceAccount", "destinationAccount", "amount"].forEach((id) => {
        document.getElementById(id).addEventListener("input", updateCreateHint);
    });
}

function showTab(tabName) {
    state.activeTab = tabName;
    document.querySelectorAll("[data-panel]").forEach((panel) => {
        panel.classList.toggle("is-active", panel.id === tabName);
    });
    document.querySelectorAll("[data-tab]").forEach((button) => {
        button.classList.toggle("is-active", button.dataset.tab === tabName);
    });

    if (tabName === "list") {
        loadPayments();
    }
}

function generateIdempotencyKey() {
    const randomPart = Math.random().toString(36).slice(2, 10);
    const key = `pay-${Date.now()}-${randomPart}`;
    document.getElementById("idempotencyKey").value = key;
}

function toggleLocale() {
    setLocale(state.locale === "zh" ? "en" : "zh");
}

function setLocale(locale) {
    state.locale = normalizeLocale(locale);
    localStorage.setItem("ui-locale", state.locale);
    applyLocale();
    updateCreateHint();

    const detailContent = document.getElementById("detailContent");
    if (detailContent && state.selectedPaymentId === null && detailContent.classList.contains("detail-grid--empty")) {
        detailContent.textContent = t("detail.empty");
    }
}

function applyLocale() {
    document.documentElement.lang = state.locale === "zh" ? "zh-CN" : "en";
    document.title = t("app.title");

    document.querySelectorAll("[data-i18n]").forEach((element) => {
        element.textContent = t(element.dataset.i18n);
    });

    document.querySelectorAll("[data-i18n-placeholder]").forEach((element) => {
        element.setAttribute("placeholder", t(element.dataset.i18nPlaceholder));
    });

    const toggleBtn = document.getElementById("languageToggleBtn");
    if (toggleBtn) {
        toggleBtn.textContent = state.locale === "zh" ? "English" : "中文";
    }

    const detailContent = document.querySelector("[data-empty-text]");
    if (detailContent && state.selectedPaymentId === null && detailContent.classList.contains("detail-grid--empty")) {
        detailContent.textContent = t("detail.empty");
    }

    if (state.selectedPayment) {
        renderDetail(state.selectedPayment);
        renderStatusFlow(state.selectedPayment, state.selectedHistory);
        renderHistory(state.selectedHistory);
    }
}

function t(key, vars = {}) {
    const dict = i18n[state.locale] || i18n.zh;
    const text = dict[key] || i18n.zh[key] || key;
    return Object.keys(vars).reduce((acc, name) => acc.replace(`{${name}}`, String(vars[name])), text);
}

function fillDemoData() {
    document.getElementById("sourceAccount").value = `ACC${Math.floor(Math.random() * 900 + 100)}`;
    document.getElementById("destinationAccount").value = `ACC${Math.floor(Math.random() * 900 + 100)}`;
    document.getElementById("amount").value = (Math.random() * 9800 + 100).toFixed(2);
    document.getElementById("reference").value = `INV-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}`;
    updateCreateHint();
}

async function handleCreatePayment(event) {
    event.preventDefault();

    const payload = collectCreatePayload();
    const validationMessage = validateCreatePayload(payload);
    if (validationMessage) {
        showFeedback(validationMessage, "error");
        showToast(validationMessage, "error");
        return;
    }

    const submitBtn = document.getElementById("submitCreateBtn");
    submitBtn.disabled = true;
    submitBtn.textContent = t("btn.creating");

    const result = await PaymentAPI.createPayment(payload, document.getElementById("idempotencyKey").value);

    submitBtn.disabled = false;
    submitBtn.textContent = t("btn.create");

    if (result.ok) {
        const isCreated = result.status === 201;
        const message = isCreated ? t("msg.createOk", { id: result.data.id }) : t("msg.createDup", { id: result.data.id });
        showFeedback(message, "success");
        showToast(message, "success");

        state.selectedPaymentId = result.data.id;
        document.getElementById("createForm").reset();
        generateIdempotencyKey();
        updateCreateHint();
    } else {
        const message = `${result.error.errorCode}: ${result.error.message}`;
        showFeedback(message, "error");
        showToast(message, "error");
    }
}

function collectCreatePayload() {
    return {
        sourceAccount: document.getElementById("sourceAccount").value.trim(),
        destinationAccount: document.getElementById("destinationAccount").value.trim(),
        amount: Number(document.getElementById("amount").value),
        currency: "CNY",
        reference: document.getElementById("reference").value.trim() || undefined
    };
}

function validateCreatePayload(payload) {
    if (!payload.sourceAccount || !payload.destinationAccount || !payload.amount) {
        return t("msg.requireFields");
    }
    if (payload.sourceAccount === payload.destinationAccount) {
        return t("msg.accountSame");
    }
    if (Number.isNaN(payload.amount) || payload.amount <= 0) {
        return t("msg.amountInvalid");
    }
    return "";
}

function updateCreateHint() {
    const payload = collectCreatePayload();
    const hint = document.getElementById("createFormHint");
    const error = validateCreatePayload(payload);
    hint.textContent = error || t("hint.valid");
    hint.style.color = error ? "#d14343" : "#15803d";
}

async function loadPayments(force = false) {
    if (!force && state.activeTab !== "list") {
        return;
    }

    state.status = document.getElementById("statusFilter").value;
    state.pageSize = Number(document.getElementById("pageSize").value);

    renderTableLoading();

    const result = await PaymentAPI.listPayments({
        page: state.page,
        size: state.pageSize,
        status: state.status
    });

    if (!result.ok) {
        renderTableEmpty(t("msg.queryFail", { message: result.error.message }));
        showToast(t("msg.listFail", { message: result.error.message }), "error");
        return;
    }

    renderPaymentRows(result.data.content || []);
    renderPagination(result.data.totalPages || 0, state.page);
}

function renderTableLoading() {
    const tbody = document.getElementById("paymentTableBody");
    tbody.innerHTML = `<tr><td colspan="7" class="empty">${escapeHtml(t("list.loading"))}</td></tr>`;
}

function renderTableEmpty(message) {
    const tbody = document.getElementById("paymentTableBody");
    tbody.innerHTML = `<tr><td colspan="7" class="empty">${escapeHtml(message)}</td></tr>`;
    document.getElementById("pagination").innerHTML = "";
}

function renderPaymentRows(payments) {
    const tbody = document.getElementById("paymentTableBody");
    tbody.innerHTML = "";

    if (!payments.length) {
        renderTableEmpty(t("list.empty"));
        return;
    }

    payments.forEach((payment) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>#${payment.id}</td>
            <td>${escapeHtml(payment.sourceAccount || "-")}</td>
            <td>${escapeHtml(payment.destinationAccount || "-")}</td>
            <td>${formatAmount(payment.amount)} ${escapeHtml(payment.currency || "CNY")}</td>
            <td><span class="status-pill status-${escapeHtml(payment.status)}">${escapeHtml(payment.status)}</span></td>
            <td>${formatDateTime(payment.createdAt)}</td>
            <td><button class="btn btn--ghost" type="button" data-id="${payment.id}">${escapeHtml(t("btn.detail"))}</button></td>
        `;

        row.addEventListener("click", () => openDetail(payment.id));
        const detailBtn = row.querySelector("button");
        detailBtn.addEventListener("click", (event) => {
            event.stopPropagation();
            openDetail(payment.id);
        });

        tbody.appendChild(row);
    });
}

function renderPagination(totalPages, currentPage) {
    const container = document.getElementById("pagination");
    container.innerHTML = "";

    if (totalPages <= 1) {
        return;
    }

    const pages = buildPageItems(totalPages, currentPage);

    pages.forEach((item) => {
        const btn = document.createElement("button");
        btn.className = `page-btn${item === currentPage ? " is-active" : ""}`;
        btn.type = "button";
        btn.textContent = String(item + 1);
        btn.addEventListener("click", () => {
            state.page = item;
            loadPayments();
        });
        container.appendChild(btn);
    });
}

function buildPageItems(totalPages, currentPage) {
    const start = Math.max(0, currentPage - 2);
    const end = Math.min(totalPages - 1, currentPage + 2);
    const items = [];
    for (let i = start; i <= end; i++) {
        items.push(i);
    }
    return items;
}

function handleDetailQuery() {
    const raw = document.getElementById("detailPaymentId").value;
    const id = Number(raw);
    if (!id || id < 1) {
        showToast(t("detail.invalidId"), "error");
        return;
    }
    openDetail(id);
}

async function openDetail(paymentId) {
    state.selectedPaymentId = paymentId;
    showTab("detail");
    document.getElementById("detailPaymentId").value = String(paymentId);

    const detailContent = document.getElementById("detailContent");
    detailContent.className = "detail-grid detail-grid--empty";
    detailContent.textContent = t("detail.loading");

    const [detailResult, historyResult] = await Promise.all([
        PaymentAPI.getPaymentDetail(paymentId),
        PaymentAPI.getPaymentHistory(paymentId)
    ]);

    if (!detailResult.ok) {
        state.selectedPayment = null;
        state.selectedHistory = [];
        detailContent.textContent = t("msg.queryFail", { message: detailResult.error.message });
        showToast(t("msg.detailFail", { message: detailResult.error.message }), "error");
        document.getElementById("statusFlowSection").hidden = true;
        document.getElementById("historySection").hidden = true;
        return;
    }

    const historyRecords = historyResult.ok ? historyResult.data : [];
    state.selectedPayment = detailResult.data;
    state.selectedHistory = historyRecords;
    renderDetail(detailResult.data);
    renderStatusFlow(detailResult.data, historyRecords);
    renderHistory(historyRecords);
}

function renderStatusFlow(payment, records) {
    const section = document.getElementById("statusFlowSection");
    const container = document.getElementById("statusFlow");
    if (!payment) {
        section.hidden = true;
        container.innerHTML = "";
        return;
    }

    const normalStatuses = ["CREATED", "VALIDATED", "SENT", "COMPLETED"];
    const labelMap = {
        CREATED: t("flow.created"),
        VALIDATED: t("flow.validated"),
        SENT: t("flow.sent"),
        COMPLETED: t("flow.completed"),
        FAILED: t("flow.failed")
    };

    const reached = new Set();
    reached.add(payment.status);
    (records || []).forEach((record) => {
        if (record && record.toStatus) {
            reached.add(record.toStatus);
        }
    });

    const isFailedTriggered = payment.status === "FAILED" || reached.has("FAILED");
    const statusOrder = [...normalStatuses];
    if (isFailedTriggered) {
        const failedRecord = [...(records || [])].reverse().find((record) => record && record.toStatus === "FAILED");
        const fromStatus = failedRecord && failedRecord.fromStatus;
        const reachedNormalIndex = Math.max(...normalStatuses.map((status, idx) => (reached.has(status) ? idx : -1)));
        const insertAfter = normalStatuses.includes(fromStatus)
            ? normalStatuses.indexOf(fromStatus)
            : Math.max(0, reachedNormalIndex);
        statusOrder.splice(insertAfter + 1, 0, "FAILED");
    }

    const stepClass = (statusCode) => {
        if (statusCode === payment.status) {
            return "flow-step is-current";
        }
        if (reached.has(statusCode)) {
            return "flow-step is-done";
        }
        return "flow-step";
    };

    const linkClass = (idx) => {
        const left = statusOrder[idx];
        const right = statusOrder[idx + 1];
        const active = Boolean(left && right && reached.has(left) && reached.has(right));
        return `flow-link${active ? " is-active" : ""}`;
    };

    const flowParts = [];
    statusOrder.forEach((statusCode, index) => {
        flowParts.push(`<div class="${stepClass(statusCode)} flow-step--${escapeHtml(statusCode)}">${escapeHtml(labelMap[statusCode])}</div>`);
        if (index < statusOrder.length - 1) {
            flowParts.push(`<div class="${linkClass(index)}" aria-hidden="true"></div>`);
        }
    });
    container.innerHTML = `<div class="flow-track${isFailedTriggered ? " flow-track--failed" : ""}">${flowParts.join("")}</div>`;

    const terminal = document.createElement("div");
    terminal.className = "flow-terminal";
    terminal.innerHTML = `
        <div class="flow-terminal-line">${escapeHtml(t("flow.current"))}: <span class="status-pill status-${escapeHtml(payment.status || "UNKNOWN")}">${escapeHtml(payment.status || "-")}</span></div>
    `;
    container.appendChild(terminal);
    section.hidden = false;
}

function renderDetail(payment) {
    const detailContent = document.getElementById("detailContent");
    detailContent.className = "detail-grid";

    const fields = [
        [t("label.id"), `#${payment.id}`],
        [t("label.idempotency"), payment.idempotencyKey || "-"],
        [t("label.source"), payment.sourceAccount || "-"],
        [t("label.destination"), payment.destinationAccount || "-"],
        [t("label.amount"), `${formatAmount(payment.amount)} ${payment.currency || "CNY"}`],
        [t("label.status"), `<span class="status-pill status-${escapeHtml(payment.status)}">${escapeHtml(payment.status)}</span>`],
        [t("label.reference"), payment.reference || "-"],
        [t("label.createdAt"), formatDateTime(payment.createdAt)],
        [t("label.updatedAt"), formatDateTime(payment.updatedAt)]
    ];

    if (payment.errorCode) {
        fields.push([t("label.errorCode"), payment.errorCode]);
    }
    if (payment.errorMessage) {
        fields.push([t("label.errorMessage"), payment.errorMessage]);
    }

    detailContent.innerHTML = fields
        .map(([label, value]) => `
            <article class="detail-item">
                <div class="detail-item__label">${escapeHtml(label)}</div>
                <div class="detail-item__value">${value}</div>
            </article>
        `)
        .join("");
}

function renderHistory(records) {
    const section = document.getElementById("historySection");
    const timeline = document.getElementById("historyTimeline");

    if (!records || !records.length) {
        section.hidden = true;
        timeline.innerHTML = "";
        return;
    }

    section.hidden = false;
    timeline.innerHTML = records
        .map((record) => `
            <article class="timeline-item">
                <div class="timeline-meta">${formatDateTime(record.createdAt)} · <span class="timeline-trigger">${escapeHtml(record.triggeredBy || "SYSTEM")}</span></div>
                <div class="timeline-flow">
                    <span class="status-pill status-${escapeHtml(record.fromStatus || "INITIAL")}">${escapeHtml(record.fromStatus || t("label.initial"))}</span>
                    <span class="timeline-arrow" aria-hidden="true">&rarr;</span>
                    <span class="status-pill status-${escapeHtml(record.toStatus || "UNKNOWN")}">${escapeHtml(record.toStatus || "-")}</span>
                </div>
                <div class="timeline-reason">${escapeHtml(record.reason || t("detail.noReason"))}</div>
            </article>
        `)
        .join("");
}

function showFeedback(message, type) {
    const el = document.getElementById("createResult");
    el.className = `feedback is-show feedback--${type === "success" ? "success" : "error"}`;
    el.textContent = message;
}

function hideFeedback() {
    const el = document.getElementById("createResult");
    el.className = "feedback";
    el.textContent = "";
}

function showToast(message, type = "success") {
    const container = document.getElementById("toastContainer");
    const toast = document.createElement("div");
    toast.className = `toast toast--${type === "error" ? "error" : "success"}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3200);
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) {
        return "-";
    }
    const date = new Date(dateTimeStr);
    return date.toLocaleString(state.locale === "zh" ? "zh-CN" : "en-US", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
}

function formatAmount(amount) {
    const value = Number(amount);
    if (Number.isNaN(value)) {
        return "0.00";
    }
    return value.toFixed(2);
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
}
