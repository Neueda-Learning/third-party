# 系统架构说明

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Web UI 前端层                             │
│  HTML + CSS + JavaScript                                        │
│  - 创建支付表单                                                  │
│  - 支付列表展示                                                  │
│  - 支付详情与历史时间线                                          │
│  - 手动状态推进操作                                              │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP/REST API
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Controller 层 (REST API)                     │
│  - PaymentController: 8个REST端点                               │
│  - HomeController: 前端模板提供                                 │
│  - GlobalExceptionHandler: 统一异常处理                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Service 层 (业务逻辑)                        │
│  - PaymentService: 业务接口                                     │
│  - PaymentServiceImpl: 业务实现                                  │
│    * 创建支付 + 幂等性检查                                      │
│    * 参数校验 (金额、币种、账户)                                │
│    * 状态转移管理                                              │
│    * 历史记录生成                                              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│               DAO 层 (数据访问 - JDBC Template)                 │
│  - PaymentDao: 支付记录CRUD                                     │
│  - PaymentHistoryDao: 历史记录操作                              │
│  - 事务管理 @Transactional                                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MySQL 数据库                               │
│  - payments: 支付记录表                                         │
│  - payment_history: 历史记录表                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Scheduler 定时任务                           │
│  - PaymentTimeoutScheduler                                      │
│  - 每60秒执行一次超时检测                                       │
│  - CREATED/VALIDATED/SENT 状态自动推进                          │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 请求流程

### 创建支付的完整流程

```
1. 客户端 (Web UI 或 API)
        │
        └─> POST /api/payments + Idempotency-Key
                │
                ▼
2. PaymentController.createPayment()
        │
        ├─> 参数绑定和校验 (@Valid)
        │
        └─> PaymentService.createPayment()
                │
                ├─> 参数校验 (PaymentValidator)
                │   ├─ 检查金额 > 0
                │   ├─ 检查币种 = CNY
                │   └─ 检查账户不相同
                │
                ├─> 幂等键检查
                │   ├─ SELECT * FROM payments WHERE idempotency_key = ?
                │   ├─ 若存在且内容同：返回已有支付 (200)
                │   ├─ 若存在且内容异：抛出409异常
                │   └─ 若不存在：继续创建
                │
                ├─> 创建支付 (INSERT)
                │   ├─ INSERT INTO payments (...)
                │   ├─ 获取自增ID
                │   └─ status = 'CREATED'
                │
                └─> 记录历史 (INSERT)
                    └─ INSERT INTO payment_history (null -> CREATED)
                        │
                        ▼
3. 响应到客户端
        │
        └─> 201 Created + Payment 对象
```

### 自动超时推进流程

```
Scheduler (每60秒执行一次)
        │
        ├─> 检查 CREATED 状态
        │   │
        │   └─> SELECT * FROM payments 
        │       WHERE status = 'CREATED' 
        │       AND updated_at < (now - 15分钟)
        │       │
        │       └─> 批量 UPDATE 为 VALIDATED
        │           并记入历史表
        │
        ├─> 检查 VALIDATED 状态
        │   │
        │   └─> SELECT * FROM payments 
        │       WHERE status = 'VALIDATED' 
        │       AND updated_at < (now - 15分钟)
        │       │
        │       └─> 批量 UPDATE 为 SENT
        │           并记入历史表
        │
        └─> 检查 SENT 状态
            │
            └─> SELECT * FROM payments 
                WHERE status = 'SENT' 
                AND updated_at < (now - 15分钟)
                │
                ├─> 按概率决定：
                │   ├─ 90% -> UPDATE 为 COMPLETED
                │   └─ 10% -> UPDATE 为 FAILED
                │
                └─> 记入历史表
```

## 📊 数据模型

### payments 表

```sql
CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,           -- 唯一ID
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,   -- 幂等键
    source_account VARCHAR(50) NOT NULL,            -- 付款账户
    destination_account VARCHAR(50) NOT NULL,       -- 收款账户
    amount DECIMAL(18, 2) NOT NULL,                 -- 金额 (Q: 为什么用DECIMAL?)
    currency VARCHAR(3) DEFAULT 'CNY',              -- 币种
    status VARCHAR(20) DEFAULT 'CREATED',           -- 状态
    error_code VARCHAR(50),                         -- 错误码 (失败时)
    error_message VARCHAR(255),                     -- 错误信息 (失败时)
    reference VARCHAR(255),                         -- 备注
    version BIGINT DEFAULT 0,                       -- 版本号 (乐观锁)
    created_at TIMESTAMP DEFAULT NOW(),             -- 创建时间
    updated_at TIMESTAMP DEFAULT NOW() ON UPDATE,   -- 更新时间
    
    -- 约束定义
    CHECK (amount > 0),
    CHECK (source_account != destination_account),
    CREATE INDEX idx_status (status),
    CREATE INDEX idx_updated_at (updated_at),
    CREATE INDEX idx_idempotency_key (idempotency_key)
);
```

**Q: 为什么金额用 DECIMAL(18,2) 而不是 DOUBLE?**
A: DECIMAL 是精确计算，不存在浮点误差。DOUBLE 在金融场景中会导致精度丢失。

### payment_history 表

```sql
CREATE TABLE payment_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,           -- 自增ID
    payment_id BIGINT NOT NULL,                     -- 关联支付ID
    from_status VARCHAR(20),                        -- 变更前状态 (初始为null)
    to_status VARCHAR(20) NOT NULL,                 -- 变更后状态
    reason VARCHAR(255),                            -- 变更原因
    triggered_by VARCHAR(50) NOT NULL,              -- 触发方 (API/SCHEDULER/SYSTEM)
    created_at TIMESTAMP DEFAULT NOW(),             -- 记录时间
    
    -- 关联约束
    FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CREATE INDEX idx_payment_id (payment_id),
    CREATE INDEX idx_created_at (created_at)
);
```

## 🔐 并发控制和幂等性

### 幂等性机制

```
Request 1: Idempotency-Key: "key-001"
├─ SELECT * FROM payments WHERE idempotency_key = "key-001"
│  └─ 返回 NULL (不存在)
│
└─ INSERT INTO payments (idempotency_key, ...)
   └─ UNIQUE 约束保证只能插入一次
   
Request 2: Idempotency-Key: "key-001" (重复)
├─ SELECT * FROM payments WHERE idempotency_key = "key-001"
│  └─ 返回之前创建的记录
│
└─ 返回已有支付 (200 OK)

Request 3: Idempotency-Key: "key-001" + 不同内容
├─ SELECT * FROM payments WHERE idempotency_key = "key-001"
│  └─ 返回之前创建的记录 (不同金额)
│
└─ 比对内容不同
   └─ 返回 409 Conflict
```

### 乐观锁（可选）

虽然当前实现中未使用乐观锁，但可以通过version字段支持：

```java
// 并发更新时检查版本号
String sql = "UPDATE payments SET version = version + 1, status = ? " +
             "WHERE id = ? AND version = ?";

updated = jdbcTemplate.update(sql, newStatus, id, currentVersion);

if (updated == 0) {
    // version 不匹配，说明有其他线程修改过，需要重试或冲突处理
}
```

## 🎯 状态机设计

### 状态转移图

```
┌──────────┐
│ CREATED  │ ◄─── 初始状态（创建支付时）
└──────┬───┘
       │
       ├─────────────────────────┐
       │                         │
       ▼ (15分钟后)             ▼ (参数验证失败)
   ┌───────────┐             ┌────────┐
   │ VALIDATED │             │ FAILED │ ◄─── 终态
   └───┬───────┘             └────────┘
       │
       ├─────────────────────────┐
       │                         │
       ▼ (15分钟后)             ▼ (手动)
   ┌─────────┐              ┌────────┐
   │  SENT   │  ────────┐   │ FAILED │
   └────┬────┘          │   └────────┘
        │               │
        ├───────────────┼──────────────┐
        │               │              │
        ▼ (15分钟后)    │         ▼ (手动)
    [90% 成功]     (10% 失败) ┌───────────┐
    ┌─────────────┐          │ COMPLETED │ ◄─── 终态
    │ COMPLETED   │          └───────────┘
    └─────────────┘
```

### 状态机规则

| 当前状态 | 允许转移到 | 触发条件 |
|---------|-----------|---------|
| CREATED | VALIDATED | 15分钟后 (Scheduler) |
| CREATED | FAILED | 参数验证失败 (Service) |
| VALIDATED | SENT | 15分钟后 (Scheduler) |
| VALIDATED | FAILED | 手动操作 (API) |
| SENT | COMPLETED | 15分钟后，概率90% (Scheduler) |
| SENT | FAILED | 15分钟后，概率10% (Scheduler) 或 手动操作 (API) |
| COMPLETED | - | 不可转移 (终态) |
| FAILED | - | 不可转移 (终态) |

## 💾 事务管理

### ACID保证

所有涉及多表更新的操作都在事务内完成：

```java
@Transactional
public Payment create(Payment payment) {
    // 1. INSERT payments
    // 2. INSERT payment_history
    // 
    // 如果步骤2失败，整个事务回滚，步骤1也被撤销
    // 确保 payments 和 payment_history 数据一致性
}
```

### 事务隔离级别

Spring Boot 默认使用数据库的默认隔离级别（MySQL 8.0 为 REPEATABLE READ）。

对于支付系统：
- **不需要 SERIALIZABLE**：当前设计中没有并发冲突
- **REPEATABLE READ 足以**：可以防止脏读和不可重复读

## 🔍 索引策略

```sql
-- 1. 幂等键查询加速
CREATE UNIQUE INDEX uk_idempotency_key ON payments(idempotency_key);
-- 用途: SELECT * FROM payments WHERE idempotency_key = ?

-- 2. 状态筛选加速
CREATE INDEX idx_status ON payments(status);
-- 用途: SELECT * FROM payments WHERE status = 'CREATED'

-- 3. 超时检测加速
CREATE INDEX idx_updated_at ON payments(updated_at);
-- 用途: SELECT * FROM payments WHERE status = 'CREATED' AND updated_at < now() - interval 15 minute

-- 4. 历史查询加速
CREATE INDEX idx_payment_id ON payment_history(payment_id);
-- 用途: SELECT * FROM payment_history WHERE payment_id = ?

-- 5. 覆盖索引（可选优化）
CREATE INDEX idx_status_updated_at ON payments(status, updated_at);
-- 用途: SELECT id FROM payments WHERE status = 'CREATED' AND updated_at < ?
```

## 🔌 API 设计原则

### RESTful 原则

```
资源: /api/payments/{id}

操作:
- GET    /api/payments/{id}          # 查询单个
- GET    /api/payments               # 列表查询
- POST   /api/payments               # 创建
- POST   /api/payments/{id}/action   # 特殊操作
```

### 错误响应格式

```json
{
  "errorCode": "INVALID_AMOUNT",
  "message": "金额必须大于0",
  "timestamp": "2026-07-27T10:00:00",
  "traceId": "uuid-xxx"
}
```

### HTTP 状态码

| 状态码 | 含义 | 场景 |
|-------|------|------|
| 200 OK | 成功 | 幂等重复请求、查询、状态转移 |
| 201 Created | 创建成功 | 首次创建支付 |
| 400 Bad Request | 客户端错误 | 参数校验失败、非法状态转移 |
| 404 Not Found | 资源不存在 | 支付ID不存在 |
| 409 Conflict | 冲突 | 幂等键重复且内容不同 |
| 500 Internal Server Error | 服务器错误 | 未捕获异常 |

## 🧵 线程安全

### 关键点

1. **DAO层**：JDBC Template 是线程安全的，DataSource 管理连接池
2. **Service层**：无状态服务，不保存线程相关的数据
3. **UNIQUE约束**：数据库级别保证幂等键唯一性，即使多个线程同时插入
4. **@Transactional**：Spring 自动管理事务，保证原子性

```java
// 并发场景示例：两个线程同时创建相同幂等键的支付
Thread 1:
  SELECT * FROM payments WHERE idempotency_key = 'key'  // 返回 null
  INSERT INTO payments (...) VALUES (...)                // 成功

Thread 2:
  SELECT * FROM payments WHERE idempotency_key = 'key'  // 返回 null (此时Thread1还未提交)
  INSERT INTO payments (...) VALUES (...)                // 失败！UNIQUE 约束冲突
  
  =>处理异常，重新查询已存在的记录
```

## 📈 性能优化建议

### 1. 缓存层
```java
// 可以在Service层添加缓存
@Cacheable(value = "payment", key = "#paymentId")
public Payment getById(Long paymentId) {
    return paymentDao.findById(paymentId);
}
```

### 2. 批量操作
```java
// Scheduler 中可以使用批量更新提升性能
batchUpdatePayments(payments);
```

### 3. 读写分离
```
Master (写)      Slave (读)
  │                  │
  └─> INSERT   ┌─────┘
      UPDATE   │
      DELETE   │ SELECT
```

## 🔐 安全考虑

### 当前版本（MVP）
- 未实现认证鉴权
- `@CrossOrigin(origins = "*")` 开放所有跨域请求
- 生产环境需要加固

### 安全加固建议
1. 添加 JWT 认证
2. 限制 CORS 源
3. 添加请求签名验证
4. 实现速率限制 (Rate Limiting)
5. 定期更新依赖

---

**该架构文档对应代码参考**：
- 架构实现：各个类文件中的包组织
- 数据库设计：`schema.sql`
- REST API：`PaymentController.java`
- 业务逻辑：`PaymentServiceImpl.java`
- 状态机：`PaymentStateMachine.java`
- 定时任务：`PaymentTimeoutScheduler.java`

