// 后端服务地址 —— 前后端分离后需要指向后端实际地址
// 开发时默认 http://localhost:8080，生产部署时改为对应域名
const BACKEND_BASE = "http://localhost:8080";

const API_BASE_URL = `${BACKEND_BASE}/api/payments`;
const ACCOUNT_BASE_URL = `${BACKEND_BASE}/api/accounts`;

// ─── 公共 HTTP 工具 ───────────────────────────────────────────────────────────

async function apiRequest(url, options = {}) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 12000);

    try {
        const response = await fetch(url, {
            method: options.method || "GET",
            headers: {
                "Content-Type": "application/json",
                ...(options.headers || {})
            },
            body: options.body,
            signal: controller.signal
        });

        const data = await readJson(response);

        if (response.ok) {
            return { ok: true, status: response.status, data };
        }

        return {
            ok: false,
            status: response.status,
            error: normalizeError(data, response.status)
        };
    } catch (error) {
        const message = error.name === "AbortError"
            ? "请求超时，请稍后重试"
            : "网络异常，请检查连接后重试";
        return { ok: false, status: 0, error: { errorCode: "NETWORK_ERROR", message } };
    } finally {
        clearTimeout(timeoutId);
    }
}

async function readJson(response) {
    try {
        return await response.json();
    } catch (_) {
        return null;
    }
}

function normalizeError(payload, status) {
    if (payload && typeof payload === "object") {
        return {
            errorCode: payload.errorCode || payload.code || `HTTP_${status}`,
            message: payload.message || "请求失败，请稍后重试"
        };
    }
    return { errorCode: `HTTP_${status}`, message: "请求失败，请稍后重试" };
}

// ─── 支付 API ─────────────────────────────────────────────────────────────────

class PaymentAPI {
    static async createPayment(payload, idempotencyKey) {
        return apiRequest(API_BASE_URL, {
            method: "POST",
            headers: { "Idempotency-Key": idempotencyKey },
            body: JSON.stringify(payload)
        });
    }

    static async listPayments({ page = 0, size = 20, status = "" } = {}) {
        const params = new URLSearchParams({ page: String(page), size: String(size) });
        if (status) params.set("status", status);
        return apiRequest(`${API_BASE_URL}?${params.toString()}`, { method: "GET" });
    }

    static async getPaymentDetail(paymentId) {
        return apiRequest(`${API_BASE_URL}/${paymentId}`, { method: "GET" });
    }

    static async getPaymentHistory(paymentId) {
        return apiRequest(`${API_BASE_URL}/${paymentId}/history`, { method: "GET" });
    }
}

// ─── 账户 API ─────────────────────────────────────────────────────────────────

class AccountAPI {
    /** POST /api/accounts — 创建账户 */
    static async createAccount(payload) {
        return apiRequest(ACCOUNT_BASE_URL, {
            method: "POST",
            body: JSON.stringify(payload)
        });
    }

    /** GET /api/accounts — 查询全部账户 */
    static async getAllAccounts() {
        return apiRequest(ACCOUNT_BASE_URL, { method: "GET" });
    }

    /** GET /api/accounts/{id} — 按 ID 查询账户 */
    static async getAccountById(id) {
        return apiRequest(`${ACCOUNT_BASE_URL}/${id}`, { method: "GET" });
    }

    /** GET /api/accounts/by-account-name/{accountName} — 按账户名查询账户 */
    static async getAccountByAccountName(accountName) {
        return apiRequest(`${ACCOUNT_BASE_URL}/by-account-name/${encodeURIComponent(accountName)}`, { method: "GET" });
    }

    /** POST /api/accounts/{id}/deposit — 充值 */
    static async deposit(id, amount) {
        return apiRequest(`${ACCOUNT_BASE_URL}/${id}/deposit`, {
            method: "POST",
            body: JSON.stringify({ amount })
        });
    }
}

