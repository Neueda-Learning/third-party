# 支付处理系统完整设计方案

> **确认需求**：JDBC Template · MySQL · Long自增ID · String幂等键 · CNY单币种 · 从CREATED起算15分钟超时 · Scheduler可配置

---

## 📋 目录

1. [技术方案确认](#1-技术方案确认)
2. [系统架构](#2-系统架构)
3. [数据模型设计](#3-数据模型设计)
4. [数据库设计](#4-数据库设计)
5. [API设计](#5-api-设计)
6. [业务流程设计](#6-业务流程设计)
7. [状态机规则](#7-状态机规则)
8. [幂等性设计](#8-幂等性设计)
9. [15分钟超时机制](#9-15分钟超时机制)
10. [错误处理](#10-错误处理)
11. [测试覆盖策略](#11-测试覆盖策略)
12. [四人Team分工](#12-四人team分工)
13. [协作流程建议](#13-协作流程建议)
14. [配置与部署](#14-配置与部署)
15. [可扩展方向](#15-可扩展方向)
16. [总结](#16-总结)

---

## 1. 技术方案确认

### 1.1 技术栈定性

| 层面 | 技术选型 | 说明 |
|---|---|---|
| **后端框架** | Spring Boot 3.2 | 快速搭建REST服务 |
| **JDK版本** | Java 21 | 最新LTS版本 |
| **ORM方案** | JDBC Template | 底层SQL执行，便于精细化控制 |
| **数据库** | MySQL 8.0+ | 生产广泛应用 |
| **主键策略** | Long AUTO_INCREMENT | MySQL自增ID |
| **幂等键** | String VARCHAR(64) UNIQUE | 客户端提供 |
| **币种策略** | 仅支持CNY | 单币种简化 |
| **前端** | HTML+CSS+JS | 原生实现 |
| **版本控制** | Git分支+PR | 规范化流程 |

### 1.2 核心依赖

- `spring-boot-starter-web`：REST API支持
- `spring-boot-starter-jdbc`：JDBC Template
- `mysql-connector-java`/`mysql-connector-j`：MySQL驱动
- `spring-boot-starter-validation`：参数校验
- `spring-boot-starter-test`：测试框架（JUnit5+Mockito）
- `lombok`（可选）：减少样板代码

---

## 2. 系统架构

### 2.1 分层架构

```
┌─────────────────────────────────────────┐
│          前端 Web UI                     │
│  创建 / 列表 / 详情 / 历史 / 错误展示    │
└──────────────────┬──────────────────────┘
                   │ HTTP/REST
┌──────────────────▼──────────────────────┐
│     Controller 层（API端点）             │
│  接收请求 → 参数校验 → 返回响应          │
├──────────────────────────────────────────┤
│     Service 层（业务规则）               │
│  幂等检查 → 状态机校验 → 流程执行        │
├──────────────────────────────────────────┤
│     DAO 层（JDBC Template访问）         │
│  SQL执行 → 事务管理 → 结果映射           │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│         MySQL 数据库                     │
│  payments + payment_history              │
└──────────────────────────────────────────┘
```

### 2.2 包结构

```
payment-system/
├── src/main/java/com/team/payment/
│   ├── PaymentApplication.java          # 启动入口
│   │
│   ├── controller/
│   │   └── PaymentController.java       # REST接口
│   │
│   ├── service/
│   │   ├── PaymentService.java          # 业务接口
│   │   ├── PaymentServiceImpl.java       # 业务实现
│   │   └── PaymentValidator.java        # 校验逻辑
│   │
│   ├── dao/
│   │   ├── PaymentDao.java              # Payment数据访问
│   │   └── PaymentHistoryDao.java       # 历史记录访问
│   │
│   ├── entity/
│   │   ├── Payment.java                 # 支付实体
│   │   ├── PaymentHistory.java          # 历史记录实体
│   │   └── PaymentStatus.java           # 状态枚举
│   │
│   ├── dto/
│   │   ├── CreatePaymentRequest.java
│   │   ├── PaymentResponse.java
│   │   ├── PaymentListResponse.java
│   │   ├── HistoryResponse.java
│   │   └── ErrorResponse.java
│   │
│   ├── exception/
│   │   ├── PaymentException.java        # 基类
│   │   ├── PaymentNotFoundException.java
│   │   ├── InvalidStatusTransitionException.java
│   │   ├── ValidationException.java
│   │   └── GlobalExceptionHandler.java
│   │
│   ├── statemachine/
│   │   └── PaymentStateMachine.java     # 状态机规则
│   │
│   └── scheduler/
│       └── PaymentTimeoutScheduler.java # 定时超时检测
│
├── src/main/resources/
│   ├── application.yml                  # 通用配置
│   ├── application-dev.yml              # 开发配置
│   ├── application-prod.yml             # 生产配置
│   └── db/
│       └── schema.sql                   # 数据库初始化脚本
│
└── src/test/java/com/team/payment/
    ├── service/
    │   └── PaymentServiceTest.java      # 单元测试
    ├── controller/
    │   └── PaymentControllerTest.java   # 集成测试
    └── scheduler/
        └── PaymentTimeoutSchedulerTest.java
```

---

## 3. 数据模型设计

### 3.1 Payment实体

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | 系统生成的唯一ID |
| `idempotencyKey` | String(64) | NOT NULL, UNIQUE | 幂等键（客户端提供） |
| `sourceAccount` | String(50) | NOT NULL | 付款账户 |
| `destinationAccount` | String(50) | NOT NULL | 收款账户 |
| `amount` | Decimal(18,2) | NOT NULL, > 0 | 金额（2位小数） |
| `currency` | String(3) | NOT NULL, DEFAULT='CNY' | 币种（仅CNY） |
| `status` | String(20) | NOT NULL, DEFAULT='CREATED' | 当前状态 |
| `errorCode` | String(50) | NULL | 失败错误码 |
| `errorMessage` | String(255) | NULL | 失败错误信息 |
| `reference` | String(255) | NULL | 参考号/备注 |
| `version` | Long | NOT NULL, DEFAULT=0 | 版本号（并发控制） |
| `createdAt` | Timestamp | NOT NULL, DEFAULT=NOW() | 创建时间 |
| `updatedAt` | Timestamp | NOT NULL, DEFAULT=NOW() | 更新时间 |

### 3.2 PaymentHistory实体

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | 自增ID |
| `paymentId` | Long | NOT NULL, FK payment(id) | 关联支付 |
| `fromStatus` | String(20) | NULL | 变更前状态（初始为NULL） |
| `toStatus` | String(20) | NOT NULL | 变更后状态 |
| `reason` | String(255) | NULL | 变更原因 |
| `triggeredBy` | String(50) | NOT NULL | 触发方（API/SCHEDULER/SYSTEM） |
| `createdAt` | Timestamp | NOT NULL, DEFAULT=NOW() | 记录时间 |

### 3.3 PaymentStatus枚举

```
CREATED     → 已创建，待校验
VALIDATED   → 校验通过，待发送
SENT        → 已发送，待完成
COMPLETED   → 完成（终态）
FAILED      → 失败（终态）
```

---

## 4. 数据库设计

### 4.1 DDL脚本（MySQL）

```sql
-- 支付主表
CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    source_account VARCHAR(50) NOT NULL,
    destination_account VARCHAR(50) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(255) NULL,
    reference VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_amount CHECK (amount > 0),
    CONSTRAINT chk_accounts_diff CHECK (source_account <> destination_account),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at),
    INDEX idx_idempotency_key (idempotency_key)
);

-- 状态历史表
CREATE TABLE payment_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NULL,
    triggered_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_created_at (created_at)
);
```

### 4.2 索引策略

- `payments.idempotency_key`：幂等键查询加速
- `payments.status`：按状态筛选加速
- `payments.updated_at`：按更新时间筛选加速（超时检测用）
- `payment_history.payment_id`：历史记录关联查询加速

---

## 5. API设计

### 5.1 API清单

| 方法 | 端点 | 说明 | 请求头 | 响应码 |
|---|---|---|---|---|
| POST | `/api/payments` | 创建支付 | Idempotency-Key | 201/200/400/409 |
| GET | `/api/payments/{id}` | 查询详情 | - | 200/404 |
| GET | `/api/payments` | 列表查询（分页+筛选） | - | 200 |
| GET | `/api/payments/{id}/history` | 查询历史 | - | 200/404 |
| POST | `/api/payments/{id}/validate` | 手动验证（演示用） | - | 200/400 |
| POST | `/api/payments/{id}/send` | 手动发送（演示用） | - | 200/400 |
| POST | `/api/payments/{id}/complete` | 手动完成（演示用） | - | 200/400 |
| POST | `/api/payments/{id}/fail` | 手动失败（演示用） | - | 200/400 |

### 5.2 创建支付API

**请求**
```
POST /api/payments
Header: Idempotency-Key: client-key-001
Content-Type: application/json

{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1500.50,
  "currency": "CNY",
  "reference": "INV-20260727"
}
```

**响应（201 Created）**
```json
{
  "id": 1,
  "idempotencyKey": "client-key-001",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1500.50,
  "currency": "CNY",
  "status": "CREATED",
  "reference": "INV-20260727",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-07-27T10:00:00",
  "updatedAt": "2026-07-27T10:00:00"
}
```

**幂等重复响应（200 OK）**
- 同Idempotency-Key + 同内容 → HTTP 200，返回已存在支付

**冲突响应（409 Conflict）**
- 同Idempotency-Key + 不同内容 → HTTP 409

### 5.3 查询列表API

**请求**
```
GET /api/payments?status=CREATED&page=0&size=20
```

**响应**
```json
{
  "content": [
    {
      "id": 1,
      "idempotencyKey": "...",
      "amount": 1500.50,
      "status": "CREATED",
      "createdAt": "2026-07-27T10:00:00"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "currentPage": 0,
  "pageSize": 20
}
```

### 5.4 查询历史API

**请求**
```
GET /api/payments/{id}/history
```

**响应**
```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "reason": "Payment created",
    "triggeredBy": "API",
    "createdAt": "2026-07-27T10:00:00"
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "reason": "Validation passed",
    "triggeredBy": "SCHEDULER",
    "createdAt": "2026-07-27T10:15:05"
  }
]
```

---

## 6. 业务流程设计

### 6.1 正常流程

```
用户创建支付
    ↓
API校验参数 → 失败返回400
    ↓
检查幂等键 → 已存在返回200或409
    ↓
创建Payment记录（status=CREATED）
    ↓
写入PaymentHistory（null → CREATED）
    ↓
返回201 + Payment信息
    ↓
[Scheduler每1分钟检测一次]
    ↓
CREATED满15分钟 → VALIDATED
    ↓
VALIDATED满15分钟 → SENT
    ↓
SENT满15分钟 → COMPLETED 或 FAILED
    ↓
[可选]用户查询详情/历史
```

### 6.2 异常流程

```
任意状态检测到业务异常
    ↓
标记为FAILED + 记录errorCode + errorMessage
    ↓
写入PaymentHistory（前状态 → FAILED）
    ↓
用户查询到失败状态，查看错误信息
```

### 6.3 幂等流程

```
首次请求：[key不存在] → 创建新支付 → 返回201
    ↓
重复请求：[key存在 + 内容一致] → 返回已有支付 → HTTP 200
    ↓
冲突请求：[key存在 + 内容不一致] → 返回409 Conflict
```

---

## 7. 状态机规则

### 7.1 合法迁移

```
CREATED   → VALIDATED
CREATED   → FAILED
VALIDATED → SENT
VALIDATED → FAILED
SENT      → COMPLETED
SENT      → FAILED
COMPLETED → （不可迁移）
FAILED    → （不可迁移）
```

### 7.2 迁移校验

| 迁移规则 | 触发条件 | 执行器 |
|---|---|---|
| CREATED → VALIDATED | 满15分钟 | Scheduler |
| VALIDATED → SENT | 满15分钟 | Scheduler |
| SENT → COMPLETED | 满15分钟 + 成功 | Scheduler |
| SENT → FAILED | 满15分钟 + 失败 | Scheduler |
| * → FAILED | 校验失败/异常 | Service/Scheduler |
| COMPLETED/FAILED | 不可转移 | Service检查 |

### 7.3 事务一致性

- 每次状态迁移必须在**单个数据库事务**内完成
- 同时更新：`payments.status` + 写入 `payment_history`
- 确保主表与历史表数据一致性

---

## 8. 幂等性设计

### 8.1 目标

用户重复提交同一支付请求时，不应创建重复的支付记录。

### 8.2 实现方案

**客户端**：每次请求生成唯一的 Idempotency-Key，并在请求头中传递

**服务端流程**：
```
1. 从请求头提取 Idempotency-Key
2. 查询 payments.idempotency_key 是否存在
   - 若不存在 → 创建新支付，返回 201
   - 若存在 → 检查请求内容
     - 内容一致 → 返回已有支付，HTTP 200
     - 内容不一致 → 返回 409 Conflict
```

### 8.3 数据库保障

```sql
ALTER TABLE payments 
ADD UNIQUE INDEX uk_idempotency_key (idempotency_key);
```

- 即使并发请求到达，UNIQUE约束也能保证只创建一条记录
- 重复请求查询到已存在的记录，正常返回

---

## 9. 15分钟超时机制

### 9.1 设计原理

模拟真实支付网络的处理延迟。从 `CREATED` 创建时刻开始，每个状态停留不超过 15 分钟，之后自动推进到下一个状态。

### 9.2 超时检测逻辑

**Scheduler执行周期**：1分钟执行一次

**每次执行流程**：
```
1. 计算超时临界时间：cutoff = NOW() - 15分钟

2. 扫描CREATED状态：
   SELECT * FROM payments WHERE status='CREATED' AND created_at < cutoff
   → 批量更新为 VALIDATED

3. 扫描VALIDATED状态：
   SELECT * FROM payments WHERE status='VALIDATED' AND updated_at < cutoff
   → 批量更新为 SENT

4. 扫描SENT状态：
   SELECT * FROM payments WHERE status='SENT' AND updated_at < cutoff
   → 按随机比例（如10%）推进为FAILED，其余为COMPLETED

5. 每次迁移都记入 payment_history
```

### 9.3 配置参数

**application.yml**
```yaml
payment:
  scheduler:
    enabled: true              # 是否启用Scheduler
    timeout-minutes: 15        # 超时时间（分钟）
    execution-interval: 60     # 执行间隔（秒）
    failure-rate: 0.1          # SENT同超时时失败比例
```

### 9.4 开发与测试环境

```yaml
# application-test.yml
payment:
  scheduler:
    enabled: false             # 测试中关闭自动调度
    timeout-minutes: 0         # 或设为0立即推进
```

---

## 10. 错误处理

### 10.1 错误码体系

| 错误码 | HTTP状态 | 场景 |
|---|---|---|
| VALIDATION_FAILED | 400 | 字段校验失败 |
| INVALID_AMOUNT | 400 | 金额格式不合法（≤0或超精度） |
| INVALID_CURRENCY | 400 | 币种不是CNY |
| INVALID_ACCOUNT | 400 | 账户相同或格式非法 |
| DUPLICATE_IDEMPOTENCY_KEY | 409 | 幂等键重复且内容不同 |
| INVALID_STATUS_TRANSITION | 400 | 非法状态迁移 |
| PAYMENT_NOT_FOUND | 404 | 支付ID不存在 |
| PROCESSING_ERROR | 500 | 内部异常 |
| NETWORK_TIMEOUT | 503 | 模拟网络超时失败 |

### 10.2 错误响应格式

```json
{
  "errorCode": "INVALID_AMOUNT",
  "message": "金额必须大于 0",
  "timestamp": "2026-07-27T10:00:00",
  "traceId": "uuid-xxx"
}
```

### 10.3 全局异常处理

- 统一在 `GlobalExceptionHandler` 捕获所有异常
- 自定义异常 → 对应错误码 + HTTP状态
- 参数校验异常 → 400 + VALIDATION_FAILED
- 未捕获异常 → 500 + PROCESSING_ERROR

---

## 11. 测试覆盖策略

### 11.1 测试分层

| 测试类型 | 框架 | 涵盖范围 | P级 |
|---|---|---|---|
| 单元测试 | JUnit5 + Mockito | 业务逻辑隔离 | P0 |
| 集成测试 | MockMvc + H2 | 端到端流程 | P0 |
| Scheduler测试 | @SpringBootTest | 超时推进逻辑 | P1 |

### 11.2 测试用例优先级

**P0（关键）**
1. ✅ 创建支付 - 正常路径
2. ✅ 幂等键重复 - 首次201，重复200
3. ✅ 金额校验 - 金额≤0、超精度、无法解析
4. ✅ 币种校验 - 非CNY拒绝
5. ✅ 账户校验 - 源目账户相同拒绝
6. ✅ 查询支付 - 存在返回200，不存在返回404
7. ✅ 状态迁移 - 合法迁移、非法迁移
8. ✅ 查询历史 - 历史记录顺序正确

**P1（重要）**
9. ✅ 15分钟超时推进 - CREATED→VALIDATED→SENT→COMPLETED
10. ✅ 超时失败场景 - SENT满15分钟转FAILED
11. ✅ 分页查询 - 分页参数有效
12. ✅ 按状态筛选 - 筛选结果正确
13. ✅ 并发冲突 - 同时创建返回409
14. ✅ 手动状态推进 - API调用推进状态

**P2（增强）**
15. ✅ 错误信息展示 - 错误码和message符合规范
16. ✅ traceId传递 - 日志链路追踪

### 11.3 测试范围矩阵

```
功能模块              单元测试  集成测试  Scheduler测试
创建支付 + 幂等       ✅       ✅       
参数校验              ✅       ✅
状态机校验            ✅       ✅
查询接口              ✅       ✅
超时推进              ✅       ✅       ✅
历史记录              ✅       ✅
异常处理              ✅       ✅
```

---

## 12. 四人Team分工

虽然每人参与前后端，但按"纵向切片"分工，各自负责一个业务域的前后端完整实现。

### 12.1 成员A：创建与幂等

**后端职责**
- POST /api/payments 接口实现
- 参数校验逻辑（金额、币种、账户）
- 幂等键检查与冲突处理
- 支付创建与入库

**前端职责**
- 创建支付页面UI
- 表单字段校验提示
- 提交成功/失败反馈
- 跳转到详情页

**测试职责**
- 单位测试：校验规则
- 集成测试：创建流程
- 幂等测试：重复提交

---

### 12.2 成员B：状态机与流程

**后端职责**
- 状态迁移规则实现
- 手动推进接口：validate/send/complete/fail
- 状态机校验逻辑
- 错误状态处理

**前端职责**
- 详情页设计与展示
- 状态显示与样式
- 操作按钮（推进/失败）
- 状态变化实时刷新

**测试职责**
- 合法迁移测试
- 非法迁移返回400
- 手动推进接口测试
- 并发冲突处理

---

### 12.3 成员C：查询与历史

**后端职责**
- GET /api/payments/{id} 详情查询
- GET /api/payments 列表查询（分页+筛选）
- GET /api/payments/{id}/history 历史查询
- 查询性能优化

**前端职责**
- 支付列表页面UI
- 分页控件与筛选UI
- 历史时间线展示
- 搜索与排序功能

**测试职责**
- 查询接口测试
- 分页参数测试
- 筛选结果正确性
- 历史记录顺序

---

### 12.4 成员D：质量、Scheduler与工程化

**后端职责**
- GlobalExceptionHandler 全局异常处理
- PaymentTimeoutScheduler 定时任务实现
- traceId 链路追踪
- 并发控制（乐观锁）
- 性能测试与优化

**前端职责**
- 错误提示页面展示
- 加载态与空状态处理
- 失败重试UI
- 响应式布局

**测试与工程职责**
- 单元测试覆盖率 ≥ 70%
- 集成测试全流程
- Scheduler超时测试
- CI/CD流程配置
- API文档维护
- 演示脚本准备

---

## 13. 协作流程建议

### 13.1 分支策略

```
main（主分支，保持可运行）
  ↓
feature/A-create-payment        (成员A)
feature/B-state-machine         (成员B)
feature/C-query-history         (成员C)
feature/D-scheduler-quality     (成员D)
  ↓
通过 PR review，merge回main
  ↓
每日早8点合并一次，减少冲突
```

### 13.2 5天迭代计划

| 日期 | 阶段 | 成员职责 |
|---|---|---|
| **Day 1** | 基础搭建 | 建库建表+项目骨架+4个API接口原型+前端页面框架 |
| **Day 2** | 核心功能 | 创建+幂等逻辑+状态机+详情页 |
| **Day 3** | 查询扩展 | 列表查询+分页筛选+历史记录+时间线 |
| **Day 4** | Scheduler+测试 | 超时处理+单元&集成测试+异常处理 |
| **Day 5** | 联调&文档 | 端到端验证+性能测试+API文档+演示脚本 |

### 13.3 每日节奏

```
08:00 - 站会（15分钟）：同步昨日进度、今日计划、阻塞点
12:00 - 午间review：前后端联调进度
16:00 - 下午对齐：合并检查冲突、回归测试
17:30 - EOD提交：Pull Request审查
```

---

## 14. 配置与部署

### 14.1 配置文件参考

**application.yml** - 通用配置

**application-dev.yml** - 开发配置
- 本地MySQL连接
- Scheduler启用
- 开启SQL日志

**application-prod.yml** - 生产配置
- 生产MySQL连接
- Scheduler禁用（由专司调度）
- 关闭SQL日志

### 14.2 依赖清单

```
Spring Boot 3.2.x
  spring-boot-starter-web
  spring-boot-starter-jdbc
  spring-boot-starter-validation
  spring-boot-starter-test

Database
  mysql-connector-j (最新驱动)

Logging
  spring-boot-starter-logging

Tools (可选)
  lombok
  springdoc-openapi (Swagger文档)
```

---

## 15. 可扩展方向

核心功能完成后，可按优先级扩展：

| 优先级 | 功能 | 工作量 | 说明 |
|---|---|---|---|
| P1 | 认证鉴权 | 中 | 集成OAuth2或JWT |
| P1 | 多币种支持 | 小 | 支持USD/EUR/GBP |
| P2 | 批量支付 | 大 | 一次提交多笔支付 |
| P2 | Webhook通知 | 中 | 状态变化推送客户端 |
| P3 | 撤销/冲正 | 中 | 已完成支付允许撤销 |
| P3 | 报表统计 | 中 | 支付金额/成功率统计 |

---

## 16. 总结

本设计方案采用**JDBC Template + MySQL + Long自增ID + String幂等键 + CNY单币种 + 15分钟超时 + 可配置Scheduler** 的技术策略，具有以下优势：

✅ **技术简洁**：JDBC直接操作SQL，无ORM框架包袱  
✅ **性能可控**：JDBC Template性能稳定，易于调优  
✅ **行为明确**：15分钟固定超时，演示清晰  
✅ **易于测试**：Scheduler可配置开关，便于单元测试  
✅ **分工清晰**：四人纵向切片，高效并行开发  
✅ **可扩展**：核心功能稳定后可逐步扩展新功能  

**此设计方案已完整覆盖所有需求，为团队快速交付提供清晰的技术指导。** ✅

