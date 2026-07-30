# 支付处理系统 - 启动指南

## ✅ 问题已解决

**问题原因**：`pom.xml` 中 Maven 编译器配置为 Java 22，但 IDE 运行时使用 Java 21，导致 `UnsupportedClassVersionError`。

**解决方案**：
- ✅ 将 `pom.xml` 编译器配置从 Java 22 改为 Java 21
- ✅ 删除 `--enable-preview` 编译参数
- ✅ 清理并重新编译项目

---

## 🚀 启动应用步骤

### 1️⃣ 确保 MySQL 正在运行

```bash
# Windows - 如果 MySQL 安装为服务，确保服务启动
# 或者直接使用 MySQL 8.0+ 客户端工具验证连接
mysql -u root -p -e "SELECT VERSION();"
```

### 2️⃣ 创建数据库和表

在 MySQL 中执行初始化脚本：

```bash
mysql -u root -p password < src/main/resources/db/schema.sql
```

或者在 MySQL 工作台/命令行中复制执行：
- 打开 `src/main/resources/db/schema.sql`
- 在 MySQL 中执行全部内容

### 3️⃣ 启动 Spring Boot 应用

#### **方法A：在 IDE 中启动** (推荐)

1. 打开 `PaymentApplication.java`
2. 点击类名旁的 ▶️ 绿色运行按钮
3. 选择 **Run 'PaymentApplication.main()'**
4. 等待启动完成（看到 `Tomcat started on port(s): 8080` 消息）

#### **方法B：使用 Maven 启动**

```powershell
# PowerShell 中设置 JAVA_HOME 为 Java 21
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-21.0.12"

# 先编译和打包
mvn clean package -q

# 运行 Spring Boot 应用
mvn spring-boot:run -q
```

#### **方法C：直接使用 Java 运行 JAR**

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-21.0.12"
java -jar target/payment-system-1.0.0.jar
```

### 4️⃣ 打开浏览器访问

```
http://localhost:8080
```

你应该看到支付处理系统的首页，包含：
- 创建支付表单
- 支付列表
- 支付详情查询
- 状态历史查询

---

## 🔧 应用配置

### 数据库配置 (`application.yml`)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_db
    username: root
    password: n3u3da!
```

### 支付系统配置 (`application.yml`)
```yaml
payment:
  scheduler:
    enabled: true              # 启用定时任务
    timeout-minutes: 15        # 15分钟超时
    execution-interval-seconds: 60  # 每分钟检测一次
    failure-rate: 0.1          # SENT超时时10%失败率
```

---

## ✨ 功能演示流程

### 1. 创建支付
- 填写付款账户、收款账户、金额
- 系统自动生成幂等键
- 点击"提交"创建支付记录
- 支付状态为 `CREATED`

### 2. 查看列表
- 切换到"支付列表"标签
- 可按状态筛选（CREATED / VALIDATED / SENT / COMPLETED / FAILED）
- 点击"详情"按钮查看单笔支付详情

### 3. 观察状态推进（15分钟超时演示）
- CREATED 状态满 15 分钟 → VALIDATED
- VALIDATED 状态满 15 分钟 → SENT
- SENT 状态满 15 分钟 → COMPLETED 或 FAILED（10% 失败率）

### 4. 手动测试（支付详情页）
- 点击"详情"进入支付详情页
- 根据当前状态显示可用的操作按钮
- 手动推进状态（用于演示）

---

## 🐛 故障排查

### 问题：页面不显示或按钮无反应

**原因**：浏览器缓存了旧 JS 文件

**解决**：
1. 按 `Ctrl+F5` 硬刷新浏览器
2. 或打开开发者工具 → Application → 清除缓存
3. 重新访问 `http://localhost:8080`

### 问题：数据库连接失败

**排查**：
```bash
# 检查 MySQL 是否运行
mysql -u root -p -e "SELECT 1;"

# 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES LIKE 'payment_db';"

# 检查表是否存在
mysql -u root -p payment_db -e "SHOW TABLES;"
```

### 问题：应用启动时仍然出现 Java 版本错误

**排查**：
```powershell
# 确保使用 Java 21
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-21.0.12"
& "$env:JAVA_HOME\bin\java" -version

# 清理并重新编译
mvn clean compile
```

---

## 📝 API 端点总结

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/payments` | 创建支付 |
| GET | `/api/payments` | 查询支付列表（分页） |
| GET | `/api/payments/{id}` | 查询支付详情 |
| GET | `/api/payments/{id}/history` | 查询支付历史 |
| POST | `/api/payments/{id}/validate` | 推进为 VALIDATED |
| POST | `/api/payments/{id}/send` | 推进为 SENT |
| POST | `/api/payments/{id}/complete` | 推进为 COMPLETED |
| POST | `/api/payments/{id}/fail` | 推进为 FAILED |

---

## ✅ 完成检查清单

- [x] Java 版本冲突已修复（Java 21）
- [x] `pom.xml` 编译器配置已更新
- [x] 项目已成功编译和打包
- [x] 数据库初始化脚本已准备
- [x] 前端资源路径已修复
- [x] 错误处理已增强

**现在可以启动应用了！** 🎉

