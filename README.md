# 支付处理系统 - 项目框架代码

本project是基于设计文档 `design_final.md` 生成的完整项目框架代码。

## 📋 项目结构

```
payment-system/
├── pom.xml                                  # Maven配置文件
├── src/main/java/com/team/payment/
│   ├── PaymentApplication.java             # Spring Boot启动入口
│   │
│   ├── controller/
│   │   ├── HomeController.java             # 首页控制器
│   │   └── PaymentController.java          # 支付API控制器 (8个REST端点)
│   │
│   ├── service/
│   │   ├── PaymentService.java             # 业务接口
│   │   └── PaymentServiceImpl.java          # 业务实现
│   │
│   ├── dao/
│   │   ├── PaymentDao.java                 # 支付DAO
│   │   └── PaymentHistoryDao.java          # 历史记录DAO
│   │
│   ├── entity/
│   │   ├── Payment.java                    # 支付实体
│   │   ├── PaymentHistory.java             # 历史记录实体
│   │   └── PaymentStatus.java              # 状态枚举
│   │
│   ├── dto/
│   │   ├── CreatePaymentRequest.java
│   │   ├── PaymentResponse.java
│   │   ├── HistoryResponse.java
│   │   ├── PaymentListResponse.java
│   │   └── ErrorResponse.java
│   │
│   ├── exception/
│   │   ├── PaymentException.java           # 基类异常
│   │   ├── PaymentNotFoundException.java
│   │   ├── InvalidStatusTransitionException.java
│   │   ├── ValidationException.java
│   │   └── GlobalExceptionHandler.java     # 全局异常处理
│   │
│   ├── statemachine/
│   │   └── PaymentStateMachine.java        # 状态机规则定义
│   │
│   └── scheduler/
│       └── PaymentTimeoutScheduler.java    # 15分钟超时定时任务
│
├── src/main/resources/
│   ├── application.yml                     # 通用配置
│   ├── application-dev.yml                 # 开发环境配置
│   ├── application-prod.yml                # 生产环境配置
│   ├── templates/
│   │   └── index.html                      # 前端首页
│   ├── static/
│   │   ├── css/
│   │   │   └── style.css                   # 前端样式
│   │   └── js/
│   │       ├── api.js                      # API调用模块
│   │       └── main.js                     # 主业务逻辑
│   └── db/
│       └── schema.sql                      # 数据库初始化脚本
│
├── src/test/java/com/team/payment/
│   ├── service/
│   │   └── PaymentServiceTest.java         # service单元测试
│   └── controller/
│       └── PaymentControllerTest.java      # controller集成测试
│
└── README.md                                # 本文档
```

## 🚀 快速开始

### 前置要求

- JDK 21+
- MySQL 8.0+
- Maven 3.9+

### 1. 数据库初始化

登录MySQL，执行 `schema.sql` 脚本：

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

或者在MySQL客户端中执行：

```sql
CREATE DATABASE IF NOT EXISTS payment_db;
USE payment_db;
-- 复制schema.sql中的所有SQL语句
```

### 2. 修改数据库连接配置

编辑 `src/main/resources/application-dev.yml`，修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_db_dev
    username: root
    password: root
```

### 3. 编译和运行

```bash
# 编译
mvn clean package

# 开发环境运行
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# 或者生成JAR后运行
java -jar target/payment-system-1.0.0.jar --spring.profiles.active=dev
```

### 4. 访问应用

打开浏览器访问：http://localhost:8080/

## 📡 API 端点

所有API都在 `/api/payments` 前缀下：

### 创建支付
```
POST /api/payments
Header: Idempotency-Key: client-generated-key
Body: {
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1500.50,
  "currency": "CNY",
  "reference": "INV-001"
}

Response: 201 Created (新建) / 200 OK (重复) / 409 Conflict / 400 Bad Request
```

### 查询详情
```
GET /api/payments/{id}
Response: 200 OK / 404 Not Found
```

### 列表查询（分页+筛选）
```
GET /api/payments?status=CREATED&page=0&size=20
Response: 200 OK
```

### 查询历史
```
GET /api/payments/{id}/history
Response: 200 OK / 404 Not Found
```

### 手动状态推进（演示用）
```
POST /api/payments/{id}/validate    # CREATED -> VALIDATED
POST /api/payments/{id}/send        # VALIDATED -> SENT
POST /api/payments/{id}/complete    # SENT -> COMPLETED
POST /api/payments/{id}/fail?errorCode=X&errorMessage=Y

Response: 200 OK / 400 Bad Request
```

## 🔄 业务流程

### 正常流程

1. **客户端创建支付**：POST /api/payments（需提供Idempotency-Key）
   - 服务端进行参数校验
   - 幂等键检查：存在返回200，不存在创建新记录返回201
   - 初始状态为 CREATED

2. **Scheduler自动处理**（每60秒执行一次）：
   - CREATED 满15分钟 → VALIDATED
   - VALIDATED 满15分钟 → SENT
   - SENT 满15分钟 → COMPLETED（成功90%）或 FAILED（失败10%）

3. **客户端查询**：GET /api/payments/{id}
   - 查看当前状态和历史记录

### 幂等性处理

```
首次: POST /api/payments -H "Idempotency-Key: key-001"
返回: 201 Created + 新建的payment对象

重复（内容一致）: POST /api/payments -H "Idempotency-Key: key-001"
返回: 200 OK + 已存在的payment对象

冲突（内容不一致）: POST /api/payments -H "Idempotency-Key: key-001" (不同的金额)
返回: 409 Conflict + 错误信息
```

## 📊 状态机规则

```
CREATED
  ├─> VALIDATED  (15分钟后，Scheduler推进)
  └─> FAILED     (参数验证失败或手动操作)

VALIDATED
  ├─> SENT       (15分钟后，Scheduler推进)
  └─> FAILED     (手动操作)

SENT
  ├─> COMPLETED  (15分钟后，90%概率，Scheduler推进)
  ├─> FAILED     (15分钟后，10%概率，或手动操作)

COMPLETED (终态)
FAILED (终态)
```

## ⚙️ 配置项

在 `application.yml` 或 `application-dev.yml` 中配置：

```yaml
payment:
  scheduler:
    enabled: true              # 是否启用定时任务（测试可关闭）
    timeout-minutes: 15        # 超时时间（分钟）
    execution-interval-seconds: 60  # 执行间隔（秒）
    failure-rate: 0.1          # SENT超时时失败比例（10%）
```

## 🧪 测试

### 运行所有测试

```bash
mvn test
```

### 测试覆盖

- **P0（关键）**：创建、幂等、校验、查询、状态机、历史记录
- **P1（重要）**：15分钟超时推进、分页、并发处理
- **P2（增强）**：错误处理、traceId链路追踪

## 👥 四人Team分工

### 成员A：创建与幂等
- POST /api/payments 接口
- 参数校验逻辑
- 幂等键检查与冲突处理
- 支付创建

### 成员B：状态机与流程
- 状态迁移规则实现
- 手动推进接口（validate/send/complete/fail）
- 状态机校验逻辑

### 成员C：查询与历史
- GET /api/payments/{id} 详情查询
- GET /api/payments 列表查询（分页+筛选）
- GET /api/payments/{id}/history 历史查询

### 成员D：质量、Scheduler、工程化
- GlobalExceptionHandler 全局异常处理
- PaymentTimeoutScheduler 定时任务
- traceId 链路追踪
- 单元测试与集成测试
- CI/CD配置

## 💡 关键技术点

### 幂等性设计

- **客户端**：每次请求生成唯一的 Idempotency-Key
- **服务端**：通过 UNIQUE 约束保证幂等键唯一性
  - 新请求（key不存在）→ 创建新记录，返回201
  - 重复请求（key存在+内容同）→ 返回已有记录，返回200
  - 冲突请求（key存在+内容异）→ 返回409

### JDBC Template使用

```java
// PaymentDao中的典型用法：

// 1. 插入并获取自增ID
KeyHolder keyHolder = new GeneratedKeyHolder();
jdbcTemplate.update(con -> {
    PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    // 设置参数
    return ps;
}, keyHolder);
payment.setId(keyHolder.getKey().longValue());

// 2. 查询映射
List<Payment> results = jdbcTemplate.query(sql, PAYMENT_ROW_MAPPER);

// 3. 更新
jdbcTemplate.update(sql, param1, param2, ...);
```

### 状态机实现

```java
// PaymentStateMachine中的合法迁移定义
CREATED   → [VALIDATED, FAILED]
VALIDATED → [SENT, FAILED]
SENT      → [COMPLETED, FAILED]
COMPLETED → （不可迁移）
FAILED    → （不可迁移）
```

### 15分钟超时机制

```java
// Scheduler每分钟检测一次
1. 查询 status='CREATED' AND updated_at < (now - 15min) 的支付
2. 批量更新为 VALIDATED
3. 类似处理 VALIDATED → SENT
4. SENT → COMPLETED/FAILED（按概率区分）
5. 每次迁移记录历史
```

### 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException e) {
        // 根据错误码返回对应HTTP状态码
        // 同时返回统一的错误格式 + traceId
    }
}
```

## 📝 前端特性

- **创建支付**：表单校验、自动生成幂等键
- **列表查询**：分页、按状态筛选
- **详情展示**：完整支付信息、状态标签样式
- **历史时间线**：可视化状态变更历程
- **操作按钮**：根据当前状态动态显示可用的状态推进操作
- **即时刷新**：实时展示状态变化

## 🐛 常见问题

### Q: 如何修改超时时间？
A: 编辑 `application.yml`，修改 `payment.scheduler.timeout-minutes`

### Q: 如何关闭Scheduler用于单元测试？
A: 在 `application-test.yml` 中设置 `payment.scheduler.enabled: false`

### Q: 如何构造幂等键冲突场景？
A: 使用相同的 Idempotency-Key 提交不同金额的请求

### Q: 支持多币种吗？
A: 当前仅支持CNY，扩展方向可参考设计文档第15章

### Q: 支持什么样的并发控制？
A: 使用 UNIQUE 约束和乐观锁（version字段），保证线程安全

## 📚 扩展方向

核心功能完成后，可按优先级扩展：

- **P1**：认证鉴权（JWT/OAuth2）、多币种支持
- **P2**：批量支付、Webhook通知、撤销/冲正
- **P3**：报表统计、审计日志、性能优化

## 🤝 协作流程

### 分支策略

```
main（主分支）
  ├─ feature/A-create-payment
  ├─ feature/B-state-machine
  ├─ feature/C-query-history
  └─ feature/D-scheduler-quality
```

### 代码提交

1. 在功能分支开发
2. 单元测试覆盖率 ≥ 70%
3. 提交 PR 进行代码审查
4. Merge 到 main 分支

## 📞 技术支持

有问题请参考：
- 设计文档：`design_final.md`
- API文档：Swagger (启用时)
- 代码注释：各类文件中详细的中文注释

---

**项目启动日期**：2026-07-27  
**技术栈**：Spring Boot 3.2 + Java 21 + JDBC Template + MySQL 8.0+

