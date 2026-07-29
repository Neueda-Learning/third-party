# 支付处理系统 — 项目介绍

> **Payment Processing System**  
> 基于 Spring Boot 3.2 + Java 21 + JDBC Template + MySQL 8.0

---

## 幻灯片 1 · 封面

```
╔══════════════════════════════════════════════════════╗
║                                                      ║
║          💳  支付处理系统                            ║
║          Payment Processing System                   ║
║                                                      ║
║   Spring Boot 3.2 · Java 21 · MySQL 8.0             ║
║                                                      ║
║   四人团队 · 2026-07-27 启动                        ║
║                                                      ║
╚══════════════════════════════════════════════════════╝
```

---

## 幻灯片 2 · 目录

1. 项目背景与目标
2. 技术选型
3. 系统架构
4. 核心数据模型
5. 支付状态机
6. 幂等性设计
7. 超时自动推进机制
8. REST API 一览
9. 前端界面
10. 测试策略
11. 团队分工
12. 扩展方向

---

## 幻灯片 3 · 项目背景与目标

### 背景

> 企业在处理支付业务时，需要一套**可靠、可追溯、支持重试**的支付流转系统。

### 项目目标

| 目标 | 说明 |
|------|------|
| ✅ 可靠创建 | 支持幂等键，防止重复下单 |
| ✅ 状态流转 | 清晰的状态机，历史可追溯 |
| ✅ 自动推进 | Scheduler 定时驱动状态迁移 |
| ✅ 错误处理 | 统一异常格式 + traceId 链路追踪 |
| ✅ 可观测性 | 完整历史记录 + 前端可视化 |

---

## 幻灯片 4 · 技术选型

| 层面 | 技术 | 选型理由 |
|------|------|----------|
| 后端框架 | **Spring Boot 3.2** | 生产级 REST 服务，生态成熟 |
| 编程语言 | **Java 21 (LTS)** | 最新长期支持版，性能优秀 |
| 数据访问 | **JDBC Template** | 精细 SQL 控制，无 ORM 黑盒 |
| 数据库 | **MySQL 8.0+** | 生产广泛，事务/索引支持完善 |
| 前端 | **HTML + CSS + JS** | 原生实现，零框架依赖 |
| 视图引擎 | **Thymeleaf** | 与 Spring 原生集成 |
| 测试框架 | **JUnit 5 + Mockito** | 单元 & 集成测试覆盖 |
| 构建工具 | **Maven 3.9+** | 标准化依赖管理 |
| 版本控制 | **Git 分支 + PR** | 四人协作规范流程 |

---

## 幻灯片 5 · 系统架构

```
┌─────────────────────────────────────────────────┐
│               前端 Web UI                        │
│   创建支付 / 列表查询 / 详情 / 历史时间线         │
└──────────────────┬──────────────────────────────┘
                   │  HTTP / REST
┌──────────────────▼──────────────────────────────┐
│            Controller 层                         │
│  参数校验 → 幂等键提取 → 响应封装                │
├─────────────────────────────────────────────────┤
│            Service 层                            │
│  幂等检查 → 状态机校验 → 业务流程执行            │
├─────────────────────────────────────────────────┤
│            DAO 层 (JDBC Template)                │
│  SQL 执行 → 事务管理 → RowMapper 映射            │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│              MySQL 8.0                           │
│   payments 表  +  payment_history 表             │
└─────────────────────────────────────────────────┘
         ↑
         │  每 60 秒扫描一次
┌────────┴────────────────────────────────────────┐
│         PaymentTimeoutScheduler                  │
│  CREATED→VALIDATED / VALIDATED→SENT / SENT→终态 │
└─────────────────────────────────────────────────┘
```

---

## 幻灯片 6 · 核心数据模型

### payments 表（核心字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | 系统唯一 ID |
| `idempotency_key` | VARCHAR(64) UNIQUE | 客户端幂等键 |
| `source_account` | VARCHAR(50) | 付款账户 |
| `destination_account` | VARCHAR(50) | 收款账户 |
| `amount` | DECIMAL(18,2) | 金额（CNY，>0） |
| `status` | VARCHAR(20) | 当前状态 |
| `version` | BIGINT | 乐观锁版本号 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 最后更新时间 |

### payment_history 表（审计轨迹）

| 字段 | 说明 |
|------|------|
| `payment_id` | 关联主表 |
| `from_status → to_status` | 状态变更记录 |
| `triggered_by` | 触发方（API / SCHEDULER / SYSTEM） |
| `reason` | 变更原因描述 |

---

## 幻灯片 7 · 支付状态机

```
                  ┌─────────────┐
     POST /api/   │             │
     payments ──► │   CREATED   │
                  │             │
                  └──────┬──────┘
                         │ 15 min (Scheduler)
                  ┌──────▼──────┐
                  │             │
                  │  VALIDATED  │
                  │             │
                  └──────┬──────┘
                         │ 15 min (Scheduler)
                  ┌──────▼──────┐
                  │             │
                  │    SENT     │
                  │             │
                  └──┬──────┬───┘
          15min·90%  │      │ 15min·10%
               ┌─────▼─┐  ┌─▼──────┐
               │COMPLETED│  │ FAILED │
               │  (终态) │  │ (终态) │
               └─────────┘  └────────┘

  任意非终态 ──────────────────────────► FAILED
  （手动 fail 接口 / 参数校验失败）
```

### 状态说明

| 状态 | 含义 |
|------|------|
| `CREATED` | 支付已创建，等待校验 |
| `VALIDATED` | 校验通过，等待发送 |
| `SENT` | 已发送到清算网络 |
| `COMPLETED` | 支付成功（终态）|
| `FAILED` | 支付失败（终态）|

---

## 幻灯片 8 · 幂等性设计

### 问题
> 网络抖动时，客户端可能**重复提交**同一笔支付请求。

### 解决方案

```
客户端请求头: Idempotency-Key: client-uuid-001
```

```
第 1 次请求（Key 不存在）
  → 创建新记录
  → 返回 201 Created

第 2 次请求（Key 存在，内容相同）
  → 直接返回已有记录
  → 返回 200 OK   ✅ 安全重试

第 3 次请求（Key 存在，金额不同）
  → 检测到冲突
  → 返回 409 Conflict  ❌ 拒绝
```

### 数据库保障

```sql
idempotency_key VARCHAR(64) NOT NULL UNIQUE
```

数据库唯一约束作为最终防线，即使并发情况下也不会创建重复记录。

---

## 幻灯片 9 · 15 分钟超时自动推进

### 机制

```
PaymentTimeoutScheduler
  ├── @Scheduled(fixedDelay = 60s)   ← 每分钟执行一次
  │
  ├── 查询 CREATED 且 updated_at < now-15min
  │     └── 批量迁移 → VALIDATED
  │
  ├── 查询 VALIDATED 且 updated_at < now-15min
  │     └── 批量迁移 → SENT
  │
  └── 查询 SENT 且 updated_at < now-15min
        ├── 90% → COMPLETED  ✅
        └── 10% → FAILED     ❌
```

### 可配置参数（application.yml）

```yaml
payment:
  scheduler:
    enabled: true                    # 可关闭（测试环境）
    timeout-minutes: 15              # 超时时间
    execution-interval-seconds: 60   # 执行间隔
    failure-rate: 0.1                # SENT 失败概率
```

---

## 幻灯片 10 · REST API 一览

| Method | 路径 | 说明 | 成功码 |
|--------|------|------|--------|
| `POST` | `/api/payments` | 创建支付（幂等） | 201 / 200 |
| `GET` | `/api/payments/{id}` | 查询单笔详情 | 200 |
| `GET` | `/api/payments` | 列表查询（分页+筛选） | 200 |
| `GET` | `/api/payments/{id}/history` | 查询状态历史 | 200 |
| `POST` | `/api/payments/{id}/validate` | 手动推进→VALIDATED | 200 |
| `POST` | `/api/payments/{id}/send` | 手动推进→SENT | 200 |
| `POST` | `/api/payments/{id}/complete` | 手动推进→COMPLETED | 200 |
| `POST` | `/api/payments/{id}/fail` | 手动标记失败 | 200 |

### 统一错误响应格式

```json
{
  "traceId": "abc-123-xyz",
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment with id 999 not found",
  "timestamp": "2026-07-29T10:30:00"
}
```

---

## 幻灯片 11 · 前端界面功能

```
┌──────────────────────────────────────────────────┐
│  💳 Payment System                               │
├──────────────────┬───────────────────────────────┤
│  📝 创建支付     │  📋 支付列表                  │
│  ─────────────   │  ─────────────                │
│  付款账户  [    ]│  筛选: [状态▼] [查询]         │
│  收款账户  [    ]│                               │
│  金额      [    ]│  ID | 金额 | 状态 | 操作      │
│  参考号    [    ]│  1  |1500 |CREATED| [详情]    │
│  幂等键 ✨自动生成│  2  |2500 |SENT   | [详情]    │
│           [提交] │  3  |3000 |DONE   | [详情]    │
├──────────────────┴───────────────────────────────┤
│  🔍 支付详情  #1                                 │
│  状态: [VALIDATED] → 操作按钮：[→SENT] [×FAIL]  │
│  历史时间线:                                     │
│    ● CREATED   2026-07-27 10:00 (API)           │
│    ● VALIDATED 2026-07-27 10:15 (SCHEDULER)     │
└──────────────────────────────────────────────────┘
```

**特性**：动态操作按钮 · 状态标签样式 · 历史时间线可视化 · 自动生成幂等键

---

## 幻灯片 12 · 测试策略

### 测试分层

| 层次 | 工具 | 覆盖内容 |
|------|------|----------|
| 单元测试 | JUnit 5 + Mockito | Service 业务逻辑、状态机规则 |
| 集成测试 | Spring Boot Test + H2 | Controller 端点、完整请求链路 |
| 数据库测试 | H2 内存库 | DAO 层 SQL 正确性 |

### 测试优先级

```
P0 关键路径（必须通过）
  ✅ 创建支付 - 正常流程
  ✅ 幂等键 - 重复请求返回200
  ✅ 幂等键 - 冲突请求返回409
  ✅ 状态机 - 非法转换拒绝
  ✅ 查询 - 不存在返回404

P1 重要功能
  ✅ 15分钟超时推进逻辑
  ✅ 分页列表查询
  ✅ 并发创建不重复

P2 质量增强
  ✅ traceId 链路追踪
  ✅ 错误响应格式统一
```

---

## 幻灯片 13 · 团队分工

```
┌─────────────────────────────────────────────────────┐
│                  四人团队分工                        │
├──────────────┬──────────────────────────────────────┤
│   成员 A     │  创建与幂等                           │
│              │  POST /api/payments                   │
│              │  参数校验 + 幂等键检查               │
├──────────────┼──────────────────────────────────────┤
│   成员 B     │  状态机与流程                         │
│              │  PaymentStateMachine 规则             │
│              │  手动推进接口（validate/send/...）    │
├──────────────┼──────────────────────────────────────┤
│   成员 C     │  查询与历史                           │
│              │  GET 详情 / 列表（分页）/ 历史        │
├──────────────┼──────────────────────────────────────┤
│   成员 D     │  质量 · Scheduler · 工程化            │
│              │  GlobalExceptionHandler               │
│              │  PaymentTimeoutScheduler              │
│              │  单元测试 + CI/CD                     │
└──────────────┴──────────────────────────────────────┘
```

### 分支策略

```
main
 ├─ feature/A-create-payment
 ├─ feature/B-state-machine
 ├─ feature/C-query-history
 └─ feature/D-scheduler-quality
```

---

## 幻灯片 14 · 项目亮点总结

| 亮点 | 实现方式 |
|------|----------|
| 🔒 **幂等安全** | Idempotency-Key + DB UNIQUE 双重保障 |
| 🔄 **状态可追溯** | payment_history 完整记录每次迁移 |
| ⏰ **自动化流转** | Scheduler 定时驱动，业务可配置 |
| 🛡️ **并发安全** | version 乐观锁防止重复更新 |
| 📊 **可观测** | traceId 全链路追踪 + 统一错误格式 |
| 🧪 **高质量** | P0/P1/P2 分级测试，覆盖率 ≥ 70% |
| 🖥️ **可视化** | 历史时间线 + 动态状态操作按钮 |

---

## 幻灯片 15 · 快速启动

### 3 步运行项目

**Step 1：初始化数据库**
```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

**Step 2：配置连接**
```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_db_dev
    username: root
    password: your_password
```

**Step 3：启动服务**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
# 访问: http://localhost:8080/
```

---

## 幻灯片 16 · 扩展方向

```
现状（MVP）
  └── 单币种(CNY) · 手动账户 · Scheduler驱动 · 原生前端

P1 近期可扩展
  ├── JWT / OAuth2 认证鉴权
  ├── 多币种支持（汇率转换）
  └── Swagger API 文档自动生成

P2 中期可扩展
  ├── 批量支付接口
  ├── Webhook 异步通知
  └── 撤销 / 冲正流程

P3 远期可扩展
  ├── 报表统计与审计日志
  ├── 消息队列解耦（RabbitMQ/Kafka）
  └── 微服务拆分 + 分布式事务
```

---

## 幻灯片 17 · Q&A

```
╔══════════════════════════════════════════════════════╗
║                                                      ║
║                  Q & A                               ║
║                                                      ║
║   💡 常见问题速查                                    ║
║                                                      ║
║   Q: 如何修改超时时间？                              ║
║   A: application.yml → payment.scheduler             ║
║      .timeout-minutes                                ║
║                                                      ║
║   Q: 如何关闭 Scheduler 做单元测试？                 ║
║   A: application-test.yml →                          ║
║      payment.scheduler.enabled: false                ║
║                                                      ║
║   Q: 支持并发吗？                                    ║
║   A: UNIQUE 约束 + version 乐观锁双重保障            ║
║                                                      ║
║   项目启动: 2026-07-27                               ║
║   技术栈: Spring Boot 3.2 + Java 21 + MySQL 8.0     ║
║                                                      ║
╚══════════════════════════════════════════════════════╝
```

---

*文档生成日期：2026-07-29*

