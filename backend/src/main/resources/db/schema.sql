-- MySQL 数据库初始化脚本
-- 创建支付处理系统数据库

-- 创建数据库
CREATE DATABASE IF NOT EXISTS payment_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE payment_db;

-- 账户表
DROP TABLE IF EXISTS account;

-- 账户表
CREATE TABLE account (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账户ID',
    account_name VARCHAR(64) NOT NULL UNIQUE COMMENT '账户名（唯一）',
    currency     VARCHAR(8)  NOT NULL COMMENT '币种，如 CNY、USD、EUR',
    balance     DECIMAL(19,4) NOT NULL DEFAULT 0 COMMENT '余额',
    version     BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- 支付主表
DROP TABLE IF EXISTS payments;

-- 支付主表
CREATE TABLE payments (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key     VARCHAR(64) NOT NULL UNIQUE,
    source_account      VARCHAR(50) NOT NULL,
    destination_account VARCHAR(50) NOT NULL,
    from_account_id     BIGINT NULL COMMENT '付款账户ID',
    to_account_id       BIGINT NULL COMMENT '收款账户ID',
    exchange_rate       DECIMAL(19,8) NULL COMMENT '汇率快照（跨币种时记录）',
    amount              DECIMAL(18,2) NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status              VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50) NULL,
    error_message       VARCHAR(255) NULL,
    reference           VARCHAR(255) NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_amount CHECK (amount > 0),
    CONSTRAINT chk_accounts_diff CHECK (source_account <> destination_account),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- 状态历史表
CREATE TABLE payment_history (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id   BIGINT NOT NULL,
    from_status  VARCHAR(20) NULL,
    to_status    VARCHAR(20) NOT NULL,
    reason       VARCHAR(255) NULL,
    triggered_by VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    INDEX idx_payment_id (payment_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付历史记录表';

-- 示例账户数据
INSERT INTO account (account_name, currency, balance) VALUES
    ('alice', 'CNY', 100000.0000),
    ('bob',   'USD', 50000.0000),
    ('carol', 'EUR', 80000.0000);
