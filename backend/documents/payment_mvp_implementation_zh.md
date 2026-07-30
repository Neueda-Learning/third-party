# 支付处理系统 MVP 实现方案（中文）

## 0. 已确认约束

- 技术栈：Spring Boot 3.2 + JDK 21 + REST API
- 默认币种：`CNY`
- 金额类型：`BigDecimal`
- 幂等策略：重复请求返回已存在支付单（不报错）
- 15分钟冗余：支付处于 `SENT` 后超过15分钟未完成，自动转 `FAILED`

---

## 1. 目标与范围（MVP）

本版本只做最小闭环：

1. 创建支付
2. 查询支付详情
3. 查询支付列表（按状态过滤）
4. 推进支付状态（校验合法状态流转）
5. 查询支付状态历史（审计轨迹）
6. 自动超时失败（`SENT` 15分钟）

不在MVP范围内：认证鉴权、真实外部支付网关、批量支付、多币种换汇、消息队列。

---

## 2. 分层架构

- `controller`：REST接口、参数校验、HTTP状态码
- `service`：业务规则（状态机、幂等、15分钟超时）
- `repository`：数据库访问（Spring Data JPA/Jdbc）
- `model/entity`：实体与枚举
- `exception`：统一异常与错误码
- `scheduler`：超时扫描任务

---

## 3. 实体设计

## 3.1 Payment（支付主单）

- `id: Long`（主键）
- `requestNo: String`（业务请求号，唯一）
- `idempotencyKey: String`（幂等键，唯一）
- `amount: BigDecimal`（金额，`>0`，建议2位小数）
- `currency: String`（默认`CNY`）
- `status: PaymentStatus`（CREATED/VALIDATED/SENT/COMPLETED/FAILED）
- `errorCode: String?`（失败时填写）
- `errorMessage: String?`（失败时填写）
- `createdAt: LocalDateTime`
- `updatedAt: LocalDateTime`
- `version: Long`（乐观锁）

## 3.2 PaymentStatusHistory（状态历史）

- `id: Long`（主键）
- `paymentId: Long`（关联支付ID）
- `fromStatus: PaymentStatus?`
- `toStatus: PaymentStatus`
- `reason: String?`
- `changedBy: String`（MVP默认`SYSTEM`）
- `changedAt: LocalDateTime`

## 3.3 IdempotencyRecord（可选）

> 最简版可不单独建表，直接在 `payment.idempotency_key` 做唯一约束。

- `idempotencyKey: String`（主键）
- `paymentId: Long`
- `requestHash: String`（可选，防止同key不同内容）
- `createdAt: LocalDateTime`

---

## 4. 数据库设计（DDL建议）

```sql
CREATE TABLE payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(50) NULL,
  error_message VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_payment_request_no UNIQUE (request_no),
  CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payment_status_created
  ON payment(status, created_at);

CREATE TABLE payment_status_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  reason VARCHAR(255) NULL,
  changed_by VARCHAR(64) NOT NULL,
  changed_at DATETIME NOT NULL,
  CONSTRAINT fk_hist_payment
    FOREIGN KEY (payment_id) REFERENCES payment(id)
);

CREATE INDEX idx_hist_payment_time
  ON payment_status_history(payment_id, changed_at);
```

说明：

- 金额统一 `DECIMAL(18,2)`，避免浮点误差。
- `currency` 默认 `CNY`；MVP建议仅允许 `CNY`。
- 使用 `version` 做并发更新保护（乐观锁）。

---

## 5. 状态机与超时策略

## 5.1 状态定义

- `CREATED`：已创建，待校验
- `VALIDATED`：校验通过，待发送
- `SENT`：已发送，待完成确认
- `COMPLETED`：完成
- `FAILED`：失败

## 5.2 合法流转

- `CREATED -> VALIDATED`
- `VALIDATED -> SENT`
- `SENT -> COMPLETED`
- `CREATED/VALIDATED/SENT -> FAILED`

禁止逆向流转，如：`COMPLETED -> CREATED`。

## 5.3 15分钟冗余规则

- 定时任务每1分钟扫描：`status = SENT` 且 `updated_at < now - 15 minutes`
- 命中则自动转 `FAILED`
- 建议失败码：`NETWORK_TIMEOUT`
- 记录一条 `payment_status_history`

---

## 6. Controller 与 API 清单

## 6.1 PaymentController

### 1) 创建支付
- `POST /api/payments`
- Header：`Idempotency-Key: <key>`

请求示例：
```json
{
  "requestNo": "REQ-20260727-0001",
  "amount": 1200.50,
  "currency": "CNY"
}
```

返回示例：
```json
{
  "id": 1,
  "requestNo": "REQ-20260727-0001",
  "amount": 1200.50,
  "currency": "CNY",
  "status": "CREATED",
  "createdAt": "2026-07-27T10:00:00"
}
```

幂等命中：返回首次创建的支付单（200）。

### 2) 查询详情
- `GET /api/payments/{id}`

### 3) 列表查询
- `GET /api/payments?status=CREATED&page=0&size=20`

## 6.2 PaymentLifecycleController

- `POST /api/payments/{id}/validate`
- `POST /api/payments/{id}/send`
- `POST /api/payments/{id}/complete`
- `POST /api/payments/{id}/fail`

`/fail` 请求示例：
```json
{
  "errorCode": "VALIDATION_FAILED",
  "errorMessage": "amount must be > 0"
}
```

## 6.3 PaymentHistoryController

- `GET /api/payments/{id}/history`

返回示例：
```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "changedAt": "2026-07-27T10:00:00",
    "changedBy": "SYSTEM",
    "reason": "payment created"
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "changedAt": "2026-07-27T10:00:10",
    "changedBy": "SYSTEM",
    "reason": "validation passed"
  }
]
```

---

## 7. 错误码与HTTP状态

建议最小集合：

- `VALIDATION_FAILED` -> `400`
- `INVALID_AMOUNT` -> `400`
- `INVALID_CURRENCY` -> `400`
- `INVALID_STATUS_TRANSITION` -> `400`
- `PAYMENT_NOT_FOUND` -> `404`
- `PROCESSING_ERROR` -> `500`
- `NETWORK_TIMEOUT` -> `503`（超时场景）

说明：幂等重复请求按已确认规则返回已有资源，不走错误码。

---

## 8. 目录结构建议

```text
src/main/java/com/example/payment
  |- controller
  |- service
  |- repository
  |- entity
  |- dto
  |- enums
  |- exception
  |- scheduler
  |- config
```

---

## 9. 开发步骤（建议顺序）

1. 建库建表 + 初始化 Spring Boot 工程
2. 定义 `PaymentStatus` 枚举与实体
3. 完成创建/查询 API
4. 增加状态推进 API 与状态机校验
5. 增加 `payment_status_history` 记录逻辑
6. 增加幂等处理（`Idempotency-Key`）
7. 增加15分钟超时任务
8. 增加统一异常处理与错误码
9. 补齐测试与API文档（OpenAPI）

---

## 10. 测试清单（MVP）

- 正常链路：`CREATED -> VALIDATED -> SENT -> COMPLETED`
- 幂等：同 `Idempotency-Key` 重复提交，返回同一支付单
- 非法流转：例如 `COMPLETED -> SENT`，应返回 `400`
- 金额校验：负数、0、超精度应拒绝
- 币种校验：非 `CNY` 应拒绝（若采用仅CNY策略）
- 超时：`SENT` 超15分钟自动 `FAILED`，并有历史记录

---

## 11. 后续扩展（非MVP）

- 接入真实网关与重试机制
- 支持多币种与汇率
- 批量支付/定时支付
- 事件驱动（MQ）异步状态推进
- 审计增强（操作者、来源系统、链路ID）
- 监控告警（失败率、处理时延）

---

## 12. 关键实现原则

- 金额计算始终使用 `BigDecimal`，统一舍入规则。
- 所有状态变化必须落历史表，保证可审计。
- 用数据库唯一键守住幂等，不仅依赖内存判断。
- 状态机校验集中在 `service`，避免散落在各层。

