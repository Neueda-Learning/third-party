/**
 * API 请求模块
 * 封装所有后端API调用
 */

const API_BASE_URL = '/api/payments';

class PaymentAPI {
    /**
     * 创建支付
     */
    static async createPayment(data, idempotencyKey) {
        const response = await fetch(API_BASE_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify(data)
        });

        if (response.status === 201 || response.status === 200) {
            return { code: 'SUCCESS', data: await response.json(), status: response.status };
        } else if (response.status === 409) {
            return { code: 'CONFLICT', data: await response.json(), status: response.status };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error, status: response.status };
        }
    }

    /**
     * 获取支付详情
     */
    static async getPaymentDetail(paymentId) {
        const response = await fetch(`${API_BASE_URL}/${paymentId}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return { code: 'SUCCESS', data: await response.json() };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error, status: response.status };
        }
    }

    /**
     * 获取支付列表
     */
    static async listPayments(page = 0, size = 20, status = null) {
        let url = `${API_BASE_URL}?page=${page}&size=${size}`;
        if (status && status !== '') {
            url += `&status=${status}`;
        }

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return { code: 'SUCCESS', data: await response.json() };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error };
        }
    }

    /**
     * 获取支付历史
     */
    static async getPaymentHistory(paymentId) {
        const response = await fetch(`${API_BASE_URL}/${paymentId}/history`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return { code: 'SUCCESS', data: await response.json() };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error };
        }
    }

    /**
     * 验证支付
     */
    static async validatePayment(paymentId) {
        return this._updatePaymentStatus(paymentId, 'validate');
    }

    /**
     * 发送支付
     */
    static async sendPayment(paymentId) {
        return this._updatePaymentStatus(paymentId, 'send');
    }

    /**
     * 完成支付
     */
    static async completePayment(paymentId) {
        return this._updatePaymentStatus(paymentId, 'complete');
    }

    /**
     * 失败支付
     */
    static async failPayment(paymentId, errorCode = 'MANUAL_FAILURE', errorMessage = '手动标记为失败') {
        const response = await fetch(`${API_BASE_URL}/${paymentId}/fail?errorCode=${errorCode}&errorMessage=${encodeURIComponent(errorMessage)}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return { code: 'SUCCESS', data: await response.json() };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error, status: response.status };
        }
    }

    /**
     * 通用的状态更新方法
     */
    static async _updatePaymentStatus(paymentId, action) {
        const response = await fetch(`${API_BASE_URL}/${paymentId}/${action}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            return { code: 'SUCCESS', data: await response.json() };
        } else {
            const error = await response.json();
            return { code: 'ERROR', data: error, status: response.status };
        }
    }
}

