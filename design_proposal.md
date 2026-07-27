# 支付处理系统 — 基础设计方案

> 技术栈：Java 21 · Spring Boot 3.2 · Spring Data JPA · H2（开发）/ PostgreSQL（生产）· Maven · JUnit 5 + Mockito

---

## 目录

1. [实体设计](#1-实体设计)
2. [数据库设计](#2-数据库设计)
3. [分层架构与包结构](#3-分层架构与包结构)
4. [API 端点设计（Controller 层）](#4-api-端点设计controller-层)
5. [业务逻辑层（Service 层）](#5-业务逻辑层service-层)
6. [15 分钟状态延迟机制](#6-15-分钟状态延迟机制)
7. [状态迁移规则](#7-状态迁移规则)
8. [错误码与异常处理](#8-错误码与异常处理)
9. [Mock 测试策略](#9-mock-测试策略)
10. [pom.xml 依赖](#10-pomxml-依赖)
11. [待确认问题](#11-待确认问题)

---

## 1. 实体设计

### 1.1 Payment（支付主表）

| 字段名 | Java 类型 | 说明 |
|---|---|---|
| `id` | `UUID` | 主键，系统生成 |
| `idempotencyKey` | `String` | 客户端提供的幂等键，唯一索引 |
| `sourceAccount` | `String` | 源账户号 |
| `destinationAccount` | `String` | 目标账户号 |
| `amount` | `BigDecimal` | 金额（> 0，最多 2 位小数） |
| `currency` | `String` | ISO 4217 币种代码（如 USD） |
| `status` | `PaymentStatus`（枚举） | 当前状态 |
| `errorCode` | `String` | 失败时的错误码（可为 null） |
| `errorMessage` | `String` | 失败时的错误描述（可为 null） |
| `reference` | `String` | 备注/描述（可选） |
| `createdAt` | `LocalDateTime` | 创建时间 |
| `updatedAt` | `LocalDateTime` | 最后更新时间 |

```java
public enum PaymentStatus {
    CREATED, VALIDATED, SENT, COMPLETED, FAILED
}
```

### 1.2 PaymentStatusHistory（状态历史表）

每次状态变更都记录一条，形成完整审计轨迹。

| 字段名 | Java 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 主键，自增 |
| `payment` | `Payment`（ManyToOne） | 关联支付 |
| `fromStatus` | `PaymentStatus` | 变更前状态（null 表示初始创建） |
| `toStatus` | `PaymentStatus` | 变更后状态 |
| `triggeredBy` | `String` | 触发来源（如 `"API"`, `"SCHEDULER"`） |
| `note` | `String` | 备注（如错误信息摘要） |
| `changedAt` | `LocalDateTime` | 变更时间戳 |

### 1.3 实体关系图

```
Payment (1) ──────< PaymentStatusHistory (N)
   id (PK)              id (PK)
   idempotencyKey        payment_id (FK)
   sourceAccount         fromStatus
   destinationAccount    toStatus
   amount                triggeredBy
   currency              note
   status                changedAt
   errorCode
   errorMessage
   reference
   createdAt
   updatedAt
```

---

## 2. 数据库设计

### 2.1 DDL（PostgreSQL）

```sql
CREATE TABLE payment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64)    NOT NULL UNIQUE,
    source_account      VARCHAR(50)    NOT NULL,
    destination_account VARCHAR(50)    NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)        NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(50),
    error_message       VARCHAR(255),
    reference           VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_status_history (
    id           BIGSERIAL   PRIMARY KEY,
    payment_id   UUID        NOT NULL REFERENCES payment(id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    triggered_by VARCHAR(50) NOT NULL,
    note         VARCHAR(255),
    changed_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_payment_status        ON payment(status);
CREATE INDEX idx_payment_updated_at    ON payment(updated_at);
CREATE INDEX idx_history_payment_id    ON payment_status_history(payment_id);
```

### 2.2 开发环境：H2 内存数据库（application-dev.yml）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:paymentdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

### 2.3 生产环境：PostgreSQL（application-prod.yml）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/paymentdb
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
```

---

## 3. 分层架构与包结构

```
架构分层：
┌─────────────────────────────┐
│   Controller 层（REST API）  │  ← 接收 HTTP 请求，参数校验，返回响应
├─────────────────────────────┤
│   Service 层（业务逻辑）     │  ← 幂等性检查、状态机、校验规则
├─────────────────────────────┤
│   Repository 层（数据访问）  │  ← Spring Data JPA，事务管理
├─────────────────────────────┤
│   Database（H2 / PostgreSQL）│
└─────────────────────────────┘

包结构：
src/main/java/org/example/
├── PaymentApplication.java
├── controller/
│   └── PaymentController.java
├── service/
│   ├── PaymentService.java              （接口）
│   └── PaymentServiceImpl.java          （实现）
├── repository/
│   ├── PaymentRepository.java
│   └── PaymentStatusHistoryRepository.java
├── entity/
│   ├── Payment.java
│   ├── PaymentStatus.java               （枚举）
│   └── PaymentStatusHistory.java
├── dto/
│   ├── CreatePaymentRequest.java
│   ├── PaymentResponse.java
│   ├── PaymentSummaryResponse.java
│   ├── StatusHistoryResponse.java
│   └── ErrorResponse.java
├── exception/
│   ├── PaymentNotFoundException.java
│   ├── InvalidStatusTransitionException.java
│   ├── DuplicatePaymentException.java
│   ├── ValidationException.java
│   └── GlobalExceptionHandler.java      （@RestControllerAdvice）
└── scheduler/
    └── PaymentStatusScheduler.java      （15 分钟状态推进）
```

---

## 4. API 端点设计（Controller 层）

### 4.1 端点总览

| 方法 | 路径 | 说明 | 响应码 |
|---|---|---|---|
| `POST` | `/api/v1/payments` | 创建支付 | 201 / 200（幂等） |
| `GET` | `/api/v1/payments/{id}` | 查询单笔支付详情 | 200 / 404 |
| `GET` | `/api/v1/payments` | 查询支付列表（分页+筛选） | 200 |
| `GET` | `/api/v1/payments/{id}/history` | 查询支付状态历史 | 200 / 404 |
| `POST` | `/api/v1/payments/{id}/process` | 手动推进状态（演示用） | 200 / 400 |

---

### 4.2 创建支付

**POST** `/api/v1/payments`

请求头（可选）：
```
Idempotency-Key: client-generated-unique-key
```

请求体：
```json
{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 100.50,
  "currency": "USD",
  "reference": "Invoice #123"
}
```

响应 `201 Created`（首次创建）/ `200 OK`（幂等重复）：
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "idempotencyKey": "client-generated-unique-key",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 100.50,
  "currency": "USD",
  "status": "CREATED",
  "reference": "Invoice #123",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-07-27T10:00:00",
  "updatedAt": "2026-07-27T10:00:00"
}
```

---

### 4.3 查询支付列表

**GET** `/api/v1/payments?status=CREATED&page=0&size=20`

| 查询参数 | 类型 | 说明 |
|---|---|---|
| `status` | String（可选） | 按状态筛选 |
| `page` | int（默认 0） | 页码 |
| `size` | int（默认 20） | 每页条数 |

响应 `200 OK`：
```json
{
  "content": [
    {
      "id": "...",
      "amount": 100.50,
      "currency": "USD",
      "status": "CREATED",
      "createdAt": "2026-07-27T10:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

---

### 4.4 查询支付状态历史

**GET** `/api/v1/payments/{id}/history`

响应 `200 OK`：
```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "triggeredBy": "API",
    "note": "支付已创建",
    "changedAt": "2026-07-27T10:00:00"
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "triggeredBy": "SCHEDULER",
    "note": null,
    "changedAt": "2026-07-27T10:15:00"
  }
]
```

---

### 4.5 手动推进状态（演示/测试用）

**POST** `/api/v1/payments/{id}/process`

将当前状态推进到下一个合法状态，忽略 15 分钟等待限制。

响应 `200 OK`：返回更新后的完整支付对象。

---

## 5. 业务逻辑层（Service 层）

### 5.1 创建支付流程

```
createPayment(request, idempotencyKey):
  1. 若 idempotencyKey 为空 → 服务端生成 UUID 作为 key
  2. 查询 paymentRepository.findByIdempotencyKey(key)
     → 若存在 → 直接返回已有支付（幂等，HTTP 200）
  3. 执行字段校验（见 5.3）
     → 校验失败 → 抛出 ValidationException
  4. 构建 Payment 对象（status = CREATED）
  5. 保存 payment
  6. 写入 PaymentStatusHistory（fromStatus=null, toStatus=CREATED, triggeredBy="API"）
  7. 返回 PaymentResponse（HTTP 201）
```

### 5.2 状态推进流程（Scheduler 或手动触发）

```
advanceStatus(paymentId, targetStatus, triggeredBy):
  1. 查询支付（不存在 → PaymentNotFoundException）
  2. 校验迁移合法性（见第 7 节）
     → 非法 → 抛出 InvalidStatusTransitionException
  3. 更新 payment.status = targetStatus
  4. 若 targetStatus = FAILED → 设置 errorCode 和 errorMessage
  5. 更新 payment.updatedAt = now()
  6. 写入 PaymentStatusHistory
  7. 保存并返回
```

### 5.3 校验规则

| 规则 | 错误码 |
|---|---|
| `amount > 0` | `INVALID_AMOUNT` |
| `amount ≤ 1,000,000` | `INVALID_AMOUNT` |
| `amount` 最多 2 位小数 | `INVALID_AMOUNT` |
| `currency` 必须在白名单内（USD/EUR/GBP/CNY/JPY） | `INVALID_CURRENCY` |
| `sourceAccount ≠ destinationAccount` | `INVALID_ACCOUNT` |
| 账户格式：字母数字，3–20 位 | `INVALID_ACCOUNT` |

---

## 6. 15 分钟状态延迟机制

### 6.1 设计思路

支付创建后不立即推进，由 Spring `@Scheduled` 定时任务每分钟扫描数据库，
将已在当前状态停留超过 15 分钟的支付自动推进到下一状态，模拟真实支付网络延迟。

### 6.2 配置（application.yml）

```yaml
payment:
  scheduler:
    enabled: true
    delay-minutes: 15          # 每个状态最短停留时间
    cron: "0 * * * * *"        # 每分钟执行一次
    failure-rate: 0.1          # SENT→COMPLETED 阶段 10% 随机失败率
```

### 6.3 Scheduler 执行逻辑

```
每分钟触发 processPayments():

  cutoff = now() - 15分钟

  Step 1: 查询 status=CREATED  AND updatedAt < cutoff
          → 批量推进至 VALIDATED

  Step 2: 查询 status=VALIDATED AND updatedAt < cutoff
          → 批量推进至 SENT

  Step 3: 查询 status=SENT AND updatedAt < cutoff
          → 随机决定：
              90% → 推进至 COMPLETED
              10% → 推进至 FAILED（errorCode=PROCESSING_ERROR）

  每次迁移均写入 PaymentStatusHistory（triggeredBy="SCHEDULER"）
```

### 6.4 Repository 查询方法

```java
// 在 PaymentRepository 中
List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime cutoff);
```

### 6.5 测试环境关闭/加速

```yaml
# application-test.yml
payment:
  scheduler:
    enabled: false      # 测试中关闭自动调度
    delay-minutes: 0    # 集成测试时设为 0 可立即推进
```

---

## 7. 状态迁移规则

### 7.1 合法迁移表

```
CREATED   → VALIDATED  ✓
CREATED   → FAILED     ✓
VALIDATED → SENT       ✓
VALIDATED → FAILED     ✓
SENT      → COMPLETED  ✓
SENT      → FAILED     ✓

COMPLETED → 任何状态   ✗  （终态，不可迁移）
FAILED    → 任何状态   ✗  （终态，不可迁移）
其他所有未列出的迁移    ✗
```

### 7.2 实现方式（状态机 EnumMap）

```java
private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS =
    Map.of(
        PaymentStatus.CREATED,   Set.of(VALIDATED, FAILED),
        PaymentStatus.VALIDATED, Set.of(SENT, FAILED),
        PaymentStatus.SENT,      Set.of(COMPLETED, FAILED)
    );

public void validateTransition(PaymentStatus from, PaymentStatus to) {
    Set<PaymentStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
    if (!allowed.contains(to)) {
        throw new InvalidStatusTransitionException(from, to);
    }
}
```

---

## 8. 错误码与异常处理

### 8.1 统一错误响应结构

```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "支付 ID [abc123] 不存在",
  "timestamp": "2026-07-27T10:00:00"
}
```

### 8.2 错误码映射表

| 错误码 | HTTP 状态 | 触发场景 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 字段校验失败 |
| `INSUFFICIENT_FUNDS` | 400 | 余额不足（模拟） |
| `INVALID_ACCOUNT` | 400 | 账户格式非法或相同 |
| `INVALID_CURRENCY` | 400 | 不支持的币种 |
| `INVALID_AMOUNT` | 400 | 金额非法 |
| `DUPLICATE_PAYMENT` | 409 | 幂等键已存在且内容不同 |
| `INVALID_STATUS_TRANSITION` | 400 | 非法状态迁移 |
| `PAYMENT_NOT_FOUND` | 404 | 支付 ID 不存在 |
| `PROCESSING_ERROR` | 500 | 内部处理错误 |
| `NETWORK_ERROR` | 503 | 模拟网络通信失败 |

### 8.3 GlobalExceptionHandler 职责

`@RestControllerAdvice` 统一捕获：
- 自定义异常 → 对应错误码和 HTTP 状态
- `MethodArgumentNotValidException` → 400 + 字段校验详情
- `Exception`（兜底） → 500 + `PROCESSING_ERROR`

---

## 9. Mock 测试策略

### 9.1 测试分层总览

| 测试类型 | 注解/工具 | 测试目标 |
|---|---|---|
| 单元测试 | `@ExtendWith(MockitoExtension.class)` | Service 业务逻辑，隔离 Repository |
| Controller 测试 | `@WebMvcTest` + MockMvc | HTTP 端点、请求/响应格式、状态码 |
| 集成测试 | `@SpringBootTest` + H2 | 完整流程端到端验证 |
| Scheduler 测试 | `@SpringBootTest` + 配置覆盖 | 状态自动推进逻辑 |

---

### 9.2 Service 单元测试（PaymentServiceImplTest）

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentStatusHistoryRepository historyRepository;
    @InjectMocks PaymentServiceImpl paymentService;

    // 正常创建
    @Test
    void createPayment_成功创建_返回CREATED状态() {
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse resp = paymentService.createPayment(validRequest(), "key-001");

        assertThat(resp.status()).isEqualTo(PaymentStatus.CREATED);
        verify(historyRepository).save(any(PaymentStatusHistory.class));
    }

    // 幂等性：重复提交返回已有支付
    @Test
    void createPayment_幂等键重复_返回已有支付不重复保存() {
        Payment existing = buildPayment(PaymentStatus.CREATED);
        when(paymentRepository.findByIdempotencyKey("key-001"))
                .thenReturn(Optional.of(existing));

        PaymentResponse resp = paymentService.createPayment(validRequest(), "key-001");

        assertThat(resp.id()).isEqualTo(existing.getId());
        verify(paymentRepository, never()).save(any());
    }

    // 非法迁移抛出异常
    @Test
    void advanceStatus_COMPLETED到CREATED_抛出InvalidStatusTransitionException() {
        Payment completed = buildPayment(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(any())).thenReturn(Optional.of(completed));

        assertThrows(InvalidStatusTransitionException.class,
                () -> paymentService.advanceStatus(completed.getId(),
                        PaymentStatus.CREATED, "API"));
    }

    // 金额为负抛出校验异常
    @Test
    void createPayment_金额为负_抛出ValidationException() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "ACC001", "ACC002", BigDecimal.valueOf(-1), "USD", null);

        assertThrows(ValidationException.class,
                () -> paymentService.createPayment(req, "key-002"));
    }

    // 源账户与目标账户相同
    @Test
    void createPayment_源目账户相同_抛出ValidationException() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "ACC001", "ACC001", BigDecimal.TEN, "USD", null);

        assertThrows(ValidationException.class,
                () -> paymentService.createPayment(req, "key-003"));
    }

    // 不支持的币种
    @Test
    void createPayment_非法币种_抛出ValidationException() {
        CreatePaymentRequest req = new CreatePaymentRequest(
                "ACC001", "ACC002", BigDecimal.TEN, "XYZ", null);

        assertThrows(ValidationException.class,
                () -> paymentService.createPayment(req, "key-004"));
    }

    // 支付不存在
    @Test
    void getPayment_不存在_抛出PaymentNotFoundException() {
        when(paymentRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.getPayment(UUID.randomUUID()));
    }
}
```

---

### 9.3 Controller 测试（PaymentControllerTest）

```java
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PaymentService paymentService;

    // 创建成功返回 201
    @Test
    void POST_payments_成功返回201() throws Exception {
        PaymentResponse mockResp = buildPaymentResponse(PaymentStatus.CREATED);
        when(paymentService.createPayment(any(), any())).thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "sourceAccount": "ACC001",
                              "destinationAccount": "ACC002",
                              "amount": 100.00,
                              "currency": "USD"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    // 支付不存在返回 404
    @Test
    void GET_payments_id_不存在返回404() throws Exception {
        when(paymentService.getPayment(any()))
                .thenThrow(new PaymentNotFoundException("not-found"));

        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
    }

    // 非法迁移返回 400
    @Test
    void POST_process_非法迁移返回400() throws Exception {
        when(paymentService.manualProcess(any()))
                .thenThrow(new InvalidStatusTransitionException(
                        PaymentStatus.COMPLETED, PaymentStatus.CREATED));

        mockMvc.perform(post("/api/v1/payments/{id}/process", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // 缺少必填字段返回 400
    @Test
    void POST_payments_缺少amount字段返回400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "sourceAccount": "ACC001",
                              "destinationAccount": "ACC002",
                              "currency": "USD"
                            }
                        """))
                .andExpect(status().isBadRequest());
    }
}
```

---

### 9.4 Scheduler 集成测试（PaymentStatusSchedulerTest）

```java
@SpringBootTest
@TestPropertySource(properties = {
    "payment.scheduler.enabled=true",
    "payment.scheduler.delay-minutes=0"
})
class PaymentStatusSchedulerTest {

    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentStatusHistoryRepository historyRepository;
    @Autowired PaymentStatusScheduler scheduler;

    @Test
    void scheduler_CREATED推进到VALIDATED() {
        Payment p = savePayment(PaymentStatus.CREATED);
        scheduler.processPayments();
        Payment updated = paymentRepository.findById(p.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }

    @Test
    void scheduler_VALIDATED推进到SENT() {
        Payment p = savePayment(PaymentStatus.VALIDATED);
        scheduler.processPayments();
        Payment updated = paymentRepository.findById(p.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SENT);
    }

    @Test
    void scheduler_完整流程CREATED到COMPLETED() {
        Payment p = savePayment(PaymentStatus.CREATED);
        scheduler.processPayments(); // CREATED → VALIDATED
        scheduler.processPayments(); // VALIDATED → SENT
        scheduler.processPayments(); // SENT → COMPLETED/FAILED

        Payment updated = paymentRepository.findById(p.getId()).orElseThrow();
        assertThat(updated.getStatus()).isIn(PaymentStatus.COMPLETED, PaymentStatus.FAILED);

        List<PaymentStatusHistory> history = historyRepository.findByPaymentId(p.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(3);
    }
}
```

---

### 9.5 测试覆盖场景矩阵

| 测试场景 | 测试类 | 优先级 |
|---|---|---|
| 正常创建支付 | ServiceTest + ControllerTest | P0 |
| 幂等键重复提交 | ServiceTest | P0 |
| 金额为负/零 | ServiceTest | P0 |
| 不支持的币种 | ServiceTest | P0 |
| 源目账户相同 | ServiceTest | P0 |
| 支付不存在 | ServiceTest + ControllerTest | P0 |
| 非法状态迁移 | ServiceTest + ControllerTest | P0 |
| Scheduler 状态推进 | SchedulerTest | P1 |
| 完整生命周期 E2E | SchedulerTest（集成） | P1 |
| 状态历史记录正确 | SchedulerTest | P1 |
| 缺少必填字段 | ControllerTest | P1 |
| 分页查询 | ControllerTest | P2 |
| 按状态筛选 | ControllerTest | P2 |

---

## 10. pom.xml 依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>org.example</groupId>
    <artifactId>PaymentProcessing</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <!-- Bean Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <!-- H2（开发/测试） -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- PostgreSQL（生产） -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <!-- Swagger / OpenAPI UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.5.0</version>
        </dependency>
        <!-- 测试（包含 JUnit 5、Mockito、MockMvc、AssertJ） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 11. 待确认问题

以下问题请与讲师（客户）确认后再开始实现：

| # | 问题 | 建议默认方案 |
|---|---|---|
| 1 | **账户是否需要独立 Account 实体和表？** | MVP 阶段账户仅为字符串，无需独立表 |
| 2 | **15 分钟延迟是否同时提供手动触发接口 `/process`？** | 强烈建议保留，演示和测试更方便 |
| 3 | **FAILED 触发比例是否需要可配置？** | 通过 `application.yml` 配置，默认 10% |
| 4 | **支持的币种白名单？** | 初版：USD、EUR、GBP、CNY、JPY |
| 5 | **前端技术是否已确定（影响 CORS 配置）？** | 暂开放所有 origin，生产前收紧 |
| 6 | **幂等键由客户端传还是服务端生成？** | 客户端传 Header，服务端未收到则自动生成 |

---

*文档版本：v1.0 | 创建日期：2026-07-27 | 技术栈：Java 21 · Spring Boot 3.2*

