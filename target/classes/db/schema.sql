-- MySQL 数据库初始化脚本
-- 创建支付处理系统数据库

-- 创建数据库
CREATE DATABASE IF NOT EXISTS payment_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE payment_db;

-- 支付主表
DROP TABLE IF EXISTS payment_history;
DROP TABLE IF EXISTS payments;

CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '系统生成的唯一ID',
    idempotency_key VARCHAR(64) NOT NULL UNIQUE COMMENT '幂等键（客户端提供，唯一）',
    source_account VARCHAR(50) NOT NULL COMMENT '付款账户',
    destination_account VARCHAR(50) NOT NULL COMMENT '收款账户',
    amount DECIMAL(18, 2) NOT NULL COMMENT '金额（2位小数）',
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种（仅支持CNY）',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '当前状态',
    error_code VARCHAR(50) NULL COMMENT '失败错误码',
    error_message VARCHAR(255) NULL COMMENT '失败错误信息',
    reference VARCHAR(255) NULL COMMENT '参考号/备注',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    CONSTRAINT chk_amount CHECK (amount > 0),
    CONSTRAINT chk_accounts_diff CHECK (source_account <> destination_account),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付记录表';

-- 状态历史表
CREATE TABLE payment_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    payment_id BIGINT NOT NULL COMMENT '关联支付ID',
    from_status VARCHAR(20) NULL COMMENT '变更前状态',
    to_status VARCHAR(20) NOT NULL COMMENT '变更后状态',
    reason VARCHAR(255) NULL COMMENT '变更原因',
    triggered_by VARCHAR(50) NOT NULL COMMENT '触发方（API/SCHEDULER/SYSTEM）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',

    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    INDEX idx_payment_id (payment_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付历史记录表';

-- 插入示例数据（可选）
-- 演示数据用于测试

INSERT INTO payments (idempotency_key, source_account, destination_account, amount, currency, status, reference)
VALUES
    ('demo-key-001', 'ACC001', 'ACC002', 1500.50, 'CNY', 'CREATED', 'INV-20260727-001'),
    ('demo-key-002', 'ACC003', 'ACC004', 2500.00, 'CNY', 'VALIDATED', 'INV-20260727-002'),
    ('demo-key-003', 'ACC005', 'ACC006', 3000.25, 'CNY', 'SENT', 'INV-20260727-003');

-- 插入对应的历史记录
INSERT INTO payment_history (payment_id, from_status, to_status, reason, triggered_by)
VALUES
    (1, NULL, 'CREATED', 'Payment created', 'API'),
    (2, NULL, 'CREATED', 'Payment created', 'API'),
    (2, 'CREATED', 'VALIDATED', 'Validation passed', 'SCHEDULER'),
    (3, NULL, 'CREATED', 'Payment created', 'API'),
    (3, 'CREATED', 'VALIDATED', 'Validation passed', 'SCHEDULER'),
    (3, 'VALIDATED', 'SENT', 'Payment sent', 'SCHEDULER');

