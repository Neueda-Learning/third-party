const API_BASE_URL = "/api/payments";

class PaymentAPI {
    static async createPayment(payload, idempotencyKey) {
        return this.#request(API_BASE_URL, {
            method: "POST",
            headers: {
                "Idempotency-Key": idempotencyKey
            },
            body: JSON.stringify(payload)
        });
    }

    static async listPayments({ page = 0, size = 20, status = "" } = {}) {
        const params = new URLSearchParams({
            page: String(page),
            size: String(size)
        });
        if (status) {
            params.set("status", status);
        }
        return this.#request(`${API_BASE_URL}?${params.toString()}`, { method: "GET" });
    }

    static async getPaymentDetail(paymentId) {
        return this.#request(`${API_BASE_URL}/${paymentId}`, { method: "GET" });
    }

    static async getPaymentHistory(paymentId) {
        return this.#request(`${API_BASE_URL}/${paymentId}/history`, { method: "GET" });
    }

    static async #request(url, options = {}) {
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

            const data = await this.#readJson(response);

            if (response.ok) {
                return {
                    ok: true,
                    status: response.status,
                    data
                };
            }

            return {
                ok: false,
                status: response.status,
                error: this.#normalizeError(data, response.status)
            };
        } catch (error) {
            const message = error.name === "AbortError" ? "请求超时，请稍后重试" : "网络异常，请检查连接后重试";
            return {
                ok: false,
                status: 0,
                error: {
                    errorCode: "NETWORK_ERROR",
                    message
                }
            };
        } finally {
            clearTimeout(timeoutId);
        }
    }

    static async #readJson(response) {
        try {
            return await response.json();
        } catch (_error) {
            return null;
        }
    }

    static #normalizeError(payload, status) {
        if (payload && typeof payload === "object") {
            return {
                errorCode: payload.errorCode || payload.code || `HTTP_${status}`,
                message: payload.message || "请求失败，请稍后重试"
            };
        }

        return {
            errorCode: `HTTP_${status}`,
            message: "请求失败，请稍后重试"
        };
    }
}

