# 基础版开发方案 — Payment Processing System

> **技术栈**：Java 21 · Spring Boot 3.2 · REST API · JPA · PostgreSQL  
> **目标**：搭建可运行的最小闭环框架，优先保证交付进度

---

## 目录

1. [项目结构](#1-项目结构)
2. [实体设计（Entity）](#2-实体设计entity)
3. [数据库设计（DDL）](#3-数据库设计ddl)
4. [状态机规则（枚举 + 迁移表）](#4-状态机规则)
5. [Repository 层](#5-repository-层)
6. [Service 层](#6-service-层)
7. [Controller 层](#7-controller-层)
8. [DTO 设计（请求/响应）](#8-dto-设计)
9. [全局异常处理](#9-全局异常处理)
10. [配置文件](#10-配置文件)
11. [依赖（pom.xml 关键部分）](#11-依赖pomxml)
12. [启动顺序建议](#12-启动顺序建议)
13. [⚠️ 待确认问题清单](#13-待确认问题清单)

---

## 1. 项目结构

```
payment-service/
├── src/main/java/com/team/payment/
│   ├── PaymentApplication.java          # 启动入口
│   │
│   ├── controller/
│   │   └── PaymentController.java       # REST 接口层
│   │
│   ├── service/
│   │   ├── PaymentService.java          # 业务核心（接口）
│   │   ├── PaymentServiceImpl.java      # 业务核心（实现）
│   │   └── ValidationService.java      # 字段校验逻辑
│   │
│   ├── domain/
│   │   ├── Payment.java                 # 支付实体
│   │   ├── PaymentStatusHistory.java    # 状态历史实体
│   │   └── PaymentStatus.java          # 状态枚举
│   │
│   ├── repository/
│   │   ├── PaymentRepository.java       # JPA Repository
│   │   └── PaymentStatusHistoryRepository.java
│   │
│   ├── dto/
│   │   ├── CreatePaymentRequest.java    # 创建支付请求
│   │   ├── PaymentResponse.java         # 支付响应
│   │   ├── TransitionRequest.java       # 状态流转请求（可选）
│   │   └── ErrorResponse.java           # 统一错误响应
│   │
│   ├── exception/
│   │   ├── PaymentNotFoundException.java
│   │   ├── InvalidStatusTransitionException.java
│   │   ├── ValidationException.java
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │
│   └── statemachine/
│       └── PaymentStateMachine.java     # 状态迁移规则
│
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   └── db/migration/                   # Flyway 数据库迁移（可选）
│       └── V1__init_tables.sql
│
└── src/test/java/com/team/payment/
    ├── service/
    │   └── PaymentServiceTest.java      # 单元测试
    └── controller/
        └── PaymentControllerTest.java   # 集成测试（MockMvc）
```

---

## 2. 实体设计（Entity）

### 2.1 PaymentStatus 枚举

```java
public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED;

    // 是否是终态（终态不可再迁移）
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
```

---

### 2.2 Payment 实体

```java
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 幂等键，客户端提供，唯一
    @Column(name = "idempotency_key", unique = true, nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "source_account", nullable = false, length = 64)
    private String sourceAccount;

    @Column(name = "destination_account", nullable = false, length = 64)
    private String destinationAccount;

    @Column(length = 140)
    private String reference;  // 备注，可选

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;  // 失败时记录

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PaymentStatus.CREATED;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters / Setters（或用 Lombok @Data）
}
```

---

### 2.3 PaymentStatusHistory 实体

```java
@Entity
@Table(name = "payment_status_history")
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联支付（多对一）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private PaymentStatus fromStatus;  // 首次创建时为 null

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private PaymentStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy;  // "API" 或 "SYSTEM"

    @Column(length = 255)
    private String reason;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @PrePersist
    public void prePersist() {
        changedAt = LocalDateTime.now();
    }

    // Getters / Setters
}
```

---

## 3. 数据库设计（DDL）

```sql
-- payments 主表
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64)    NOT NULL UNIQUE,
    amount              DECIMAL(18, 2) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)        NOT NULL,
    source_account      VARCHAR(64)    NOT NULL,
    destination_account VARCHAR(64)    NOT NULL,
    reference           VARCHAR(140),
    status              VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    error_code          VARCHAR(64),
    error_message       VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT chk_accounts_diff CHECK (source_account <> destination_account)
);

-- 状态历史表
CREATE TABLE payment_status_history (
    id            BIGSERIAL   PRIMARY KEY,
    payment_id    UUID        NOT NULL REFERENCES payments(id),
    from_status   VARCHAR(20),
    to_status     VARCHAR(20) NOT NULL,
    changed_at    TIMESTAMP   NOT NULL DEFAULT now(),
    triggered_by  VARCHAR(50) NOT NULL,
    reason        VARCHAR(255),
    error_code    VARCHAR(64)
);

-- 索引（查询加速）
CREATE INDEX idx_payments_status     ON payments (status);
CREATE INDEX idx_payments_idempotency ON payments (idempotency_key);
CREATE INDEX idx_history_payment_id  ON payment_status_history (payment_id);
```

> **说明：**
> - `gen_random_uuid()` 是 PostgreSQL 内置函数，MySQL 请改为 `UUID()`
> - `CONSTRAINT chk_accounts_diff` 在数据库层面也保证源/目标不同
> - 如果用 MySQL，`UUID` 类型改为 `VARCHAR(36)`

---

## 4. 状态机规则

### 4.1 PaymentStateMachine（核心校验类）

```java
@Component
public class PaymentStateMachine {

    // 合法迁移表：当前状态 → 允许迁移到的目标状态集合
    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS =
        Map.of(
            PaymentStatus.CREATED,   Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
            PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT,      PaymentStatus.FAILED),
            PaymentStatus.SENT,      Set.of(PaymentStatus.COMPLETED,  PaymentStatus.FAILED),
            PaymentStatus.COMPLETED, Set.of(),  // 终态，不可迁移
            PaymentStatus.FAILED,    Set.of()   // 终态，不可迁移
        );

    /**
     * 校验状态迁移是否合法，不合法则抛出异常
     */
    public void validate(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidStatusTransitionException(
                String.format("不允许从 %s 迁移到 %s", from, to)
            );
        }
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
```

---

## 5. Repository 层

### 5.1 PaymentRepository

```java
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // 按状态查询
    List<Payment> findByStatus(PaymentStatus status);

    // 幂等键查询（判断重复提交）
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
```

### 5.2 PaymentStatusHistoryRepository

```java
public interface PaymentStatusHistoryRepository
        extends JpaRepository<PaymentStatusHistory, Long> {

    // 查询某笔支付的全部历史，按时间升序
    List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId);
}
```

---

## 6. Service 层

### 6.1 ValidationService（字段校验）

```java
@Service
public class ValidationService {

    private static final Set<String> SUPPORTED_CURRENCIES =
        Set.of("USD", "EUR", "GBP", "CNY", "JPY");

    private static final BigDecimal MAX_AMOUNT =
        new BigDecimal("1000000");

    public void validate(CreatePaymentRequest req) {
        // 金额校验
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("INVALID_AMOUNT", "金额必须大于 0");
        }
        if (req.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("INVALID_AMOUNT", "金额不能超过 1,000,000");
        }
        if (req.getAmount().scale() > 2) {
            throw new ValidationException("INVALID_AMOUNT", "金额最多保留 2 位小数");
        }

        // 币种校验
        if (!SUPPORTED_CURRENCIES.contains(req.getCurrency())) {
            throw new ValidationException("INVALID_CURRENCY",
                "不支持的币种：" + req.getCurrency());
        }

        // 账户校验
        if (req.getSourceAccount().equals(req.getDestinationAccount())) {
            throw new ValidationException("VALIDATION_FAILED",
                "源账户和目标账户不能相同");
        }
    }
}
```

---

### 6.2 PaymentServiceImpl（业务核心）

```java
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentStateMachine stateMachine;
    private final ValidationService validationService;

    // ────────────────────────────────────────
    // 创建支付（含幂等 + 自动流转）
    // ────────────────────────────────────────
    @Override
    public PaymentResponse createPayment(CreatePaymentRequest req) {

        // 1. 幂等检查：相同 key 直接返回已有支付
        Optional<Payment> existing =
            paymentRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // 2. 字段校验
        validationService.validate(req);

        // 3. 创建支付对象
        Payment payment = new Payment();
        payment.setIdempotencyKey(req.getIdempotencyKey());
        payment.setAmount(req.getAmount());
        payment.setCurrency(req.getCurrency());
        payment.setSourceAccount(req.getSourceAccount());
        payment.setDestinationAccount(req.getDestinationAccount());
        payment.setReference(req.getReference());
        payment.setStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        // 4. 记录 CREATED 历史
        recordHistory(payment, null, PaymentStatus.CREATED, "API", "支付创建", null);

        // 5. 自动流转（模拟处理）
        autoProcess(payment);

        return toResponse(payment);
    }

    // ────────────────────────────────────────
    // 自动流转逻辑（MVP：模拟内部处理）
    // ────────────────────────────────────────
    private void autoProcess(Payment payment) {
        // CREATED → VALIDATED
        transition(payment, PaymentStatus.VALIDATED, "SYSTEM", "校验通过", null);

        // VALIDATED → SENT
        transition(payment, PaymentStatus.SENT, "SYSTEM", "发送成功", null);

        // SENT → COMPLETED
        transition(payment, PaymentStatus.COMPLETED, "SYSTEM", "处理完成", null);

        // ⚠️ 如需模拟失败，可在此注入随机失败逻辑，演示更丰富
        // 例如：if (new Random().nextInt(10) < 2) { transition(FAILED...) }
    }

    // ────────────────────────────────────────
    // 状态迁移（含状态机校验 + 历史写入）
    // ────────────────────────────────────────
    private void transition(Payment payment, PaymentStatus target,
                            String triggeredBy, String reason, String errorCode) {
        stateMachine.validate(payment.getStatus(), target);
        PaymentStatus previous = payment.getStatus();
        payment.setStatus(target);
        if (errorCode != null) {
            payment.setErrorCode(errorCode);
            payment.setErrorMessage(reason);
        }
        paymentRepository.save(payment);
        recordHistory(payment, previous, target, triggeredBy, reason, errorCode);
    }

    // ────────────────────────────────────────
    // 查询支付详情
    // ────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        return toResponse(payment);
    }

    // ────────────────────────────────────────
    // 按状态筛选
    // ────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(PaymentStatus status) {
        List<Payment> payments = (status == null)
            ? paymentRepository.findAll()
            : paymentRepository.findByStatus(status);
        return payments.stream().map(this::toResponse).toList();
    }

    // ────────────────────────────────────────
    // 查询状态历史
    // ────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<PaymentStatusHistory> getHistory(UUID id) {
        // 先确认支付存在
        if (!paymentRepository.existsById(id)) {
            throw new PaymentNotFoundException(id);
        }
        return historyRepository.findByPaymentIdOrderByChangedAtAsc(id);
    }

    // ────────────────────────────────────────
    // 手动触发失败（演示用，可选）
    // ────────────────────────────────────────
    @Override
    public PaymentResponse failPayment(UUID id, String errorCode, String reason) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        transition(payment, PaymentStatus.FAILED, "API", reason, errorCode);
        return toResponse(payment);
    }

    // ────────────────────────────────────────
    // 内部工具方法
    // ────────────────────────────────────────
    private void recordHistory(Payment payment, PaymentStatus from, PaymentStatus to,
                                String triggeredBy, String reason, String errorCode) {
        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setPayment(payment);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setTriggeredBy(triggeredBy);
        history.setReason(reason);
        history.setErrorCode(errorCode);
        historyRepository.save(history);
    }

    private PaymentResponse toResponse(Payment p) {
        PaymentResponse resp = new PaymentResponse();
        resp.setId(p.getId());
        resp.setStatus(p.getStatus());
        resp.setAmount(p.getAmount());
        resp.setCurrency(p.getCurrency());
        resp.setSourceAccount(p.getSourceAccount());
        resp.setDestinationAccount(p.getDestinationAccount());
        resp.setReference(p.getReference());
        resp.setErrorCode(p.getErrorCode());
        resp.setErrorMessage(p.getErrorMessage());
        resp.setCreatedAt(p.getCreatedAt());
        resp.setUpdatedAt(p.getUpdatedAt());
        return resp;
    }
}
```

---

## 7. Controller 层

```java
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── POST /api/v1/payments ──────────────────────
    // 创建支付
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestBody @Valid CreatePaymentRequest req) {
        PaymentResponse resp = paymentService.createPayment(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ── GET /api/v1/payments/{id} ──────────────────
    // 查询支付详情
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    // ── GET /api/v1/payments?status=COMPLETED ──────
    // 按状态筛选（status 可不传，不传返回全部）
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(paymentService.listPayments(status));
    }

    // ── GET /api/v1/payments/{id}/history ──────────
    // 查询状态历史
    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentStatusHistory>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getHistory(id));
    }

    // ── POST /api/v1/payments/{id}/fail ─────────────
    // 手动标记失败（演示用）
    @PostMapping("/{id}/fail")
    public ResponseEntity<PaymentResponse> fail(
            @PathVariable UUID id,
            @RequestBody FailRequest req) {
        return ResponseEntity.ok(paymentService.failPayment(
            id, req.getErrorCode(), req.getReason()));
    }
}
```

> **接口汇总：**
>
> | 方法 | 路径 | 说明 |
> |---|---|---|
> | POST | `/api/v1/payments` | 创建支付 |
> | GET | `/api/v1/payments/{id}` | 查询详情 |
> | GET | `/api/v1/payments?status=` | 按状态筛选 |
> | GET | `/api/v1/payments/{id}/history` | 状态历史 |
> | POST | `/api/v1/payments/{id}/fail` | 手动标记失败（演示用） |

---

## 8. DTO 设计

### 8.1 CreatePaymentRequest

```java
public class CreatePaymentRequest {

    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @NotBlank(message = "币种不能为空")
    @Size(min = 3, max = 3, message = "币种必须是 3 位字母")
    private String currency;

    @NotBlank(message = "付款账户不能为空")
    private String sourceAccount;

    @NotBlank(message = "收款账户不能为空")
    private String destinationAccount;

    private String reference;  // 可选

    // Getters / Setters
}
```

### 8.2 PaymentResponse

```java
public class PaymentResponse {
    private UUID id;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String sourceAccount;
    private String destinationAccount;
    private String reference;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters / Setters
}
```

### 8.3 ErrorResponse（统一错误格式）

```java
public class ErrorResponse {
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    // Getters
}
```

### 8.4 FailRequest（手动失败请求）

```java
public class FailRequest {
    private String errorCode;  // 如 NETWORK_ERROR
    private String reason;
    // Getters / Setters
}
```

---

## 9. 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 支付不存在
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PAYMENT_NOT_FOUND", ex.getMessage()));
    }

    // 非法状态迁移
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_STATUS_TRANSITION", ex.getMessage()));
    }

    // 字段校验失败（自定义）
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    // Bean Validation（@Valid 注解触发）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_FAILED", message));
    }

    // 兜底：内部异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("PROCESSING_ERROR", "服务器内部异常，请稍后重试"));
    }
}
```

---

## 10. 配置文件

### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: payment-service

  datasource:
    url: jdbc:postgresql://localhost:5432/payment_db
    username: ${DB_USER:postgres}
    password: ${DB_PASS:postgres}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate        # 生产不自动建表，用 Flyway 或手动脚本
    show-sql: true              # 开发期间打印 SQL，上生产改为 false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # 开发阶段可改为 ddl-auto: create-drop 自动建表
```

> **快速开发时**，可以把 `ddl-auto` 改为 `create-drop`（每次启动重建表），稳定后再改回 `validate` 配合手动建表脚本。

---

## 11. 依赖（pom.xml 关键部分）

```xml
<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Spring Boot Web（REST API） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Bean Validation（@Valid 注解） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- PostgreSQL 驱动 -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok（简化 Getter/Setter/构造函数） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Swagger（API 文档，可选） -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.3.0</version>
    </dependency>
</dependencies>
```

---

## 12. 启动顺序建议

```
Day1 开始推荐顺序：

Step 1  建 Spring Boot 项目骨架（Spring Initializr）
        → 选 Web + JPA + Validation + PostgreSQL + Lombok

Step 2  建数据库（PostgreSQL）
        → 执行 DDL 脚本建两张表

Step 3  写 PaymentStatus 枚举 + Payment 实体 + PaymentStatusHistory 实体
        → 验证：ddl-auto=create 启动后表能自动生成

Step 4  写 Repository 层（2个接口）
        → 不需要实现类，JPA 自动生成

Step 5  写 PaymentStateMachine（状态机规则）
        → 写 4 个单元测试验证迁移是否正确

Step 6  写 ValidationService + Exception 类

Step 7  写 PaymentServiceImpl（先只做 createPayment）
        → 用 Postman 测试 POST /api/v1/payments 能存库

Step 8  补全其他 Service 方法 + Controller

Step 9  写 GlobalExceptionHandler
        → 测试各种错误场景返回正确格式

Step 10 补充单元测试（覆盖率 ≥ 70%）
```

---

## 13. ⚠️ 待确认问题清单

在开始写代码前，团队需要拍板以下问题：

### 技术选型确认

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| T1 | 数据库用哪个？ | PostgreSQL / MySQL / H2（内存） | PostgreSQL，或开发用 H2 跑单测 |
| T2 | 用 Lombok 吗？ | 是 / 否 | 强烈建议用，省去大量 Getter/Setter |
| T3 | 加 Swagger 吗？ | 是 / 否 | 加，不难，演示神器（`springdoc-openapi`） |
| T4 | 数据库连接：本地安装 还是 Docker？ | 本地 / Docker Compose | Docker 更统一，避免环境差异 |

### 设计决策确认

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| D1 | 支付 ID 用什么类型？ | UUID（随机）/ Long（自增）| UUID，更专业，不暴露数量信息 |
| D2 | 自动流转 vs 手动推进？ | 自动（创建后一次跑完）/ 手动（每步调接口） | **自动**，3天内最快实现 |
| D3 | 幂等失败行为？ | 返回已有支付（200）/ 报错（409） | **返回已有支付**，体验好 |
| D4 | 失败场景怎么演示？ | 固定某些情况失败 / 随机失败 / 手动调 `/fail` | **手动调 `/fail` 接口**，最可控 |
| D5 | history 接口返回 Entity 还是 DTO？ | Entity 直接返回 / 转成 DTO | 先直接返回 Entity，快；后期改 DTO |

### 分工确认

| # | 问题 |
|---|---|
| P1 | A 和 B 共用一个数据库实例，还是各自本地数据库开发？ |
| P2 | 前端 D 用什么框架？React / Vue / 纯 HTML+JS？ |
| P3 | Git 仓库放在哪里？GitHub / GitLab / Bitbucket？ |
| P4 | 所有人用同一台机器演示，还是各自本机演示？ |

---

> 以上问题建议 **Day1 上午站会时（30分钟）全部拍板**，避免后续返工。  
> 拍板后把答案填到 `project_plan.md` 的对应章节。

