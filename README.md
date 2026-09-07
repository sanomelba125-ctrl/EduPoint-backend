# 校园活动积分平台 (EduPoint) - 后端服务 (Backend)

基于 **Spring Boot 3.3.2**、**Java 17/23**、**MyBatis-Plus** 与 **Sa-Token** 开发的生产级后端微服务。

---

## 🛠️ 技术栈与依赖架构

- **核心框架**：Spring Boot 3.3.2
- **持久层框架**：MyBatis-Plus 3.5.7 + MySQL Connector/J 8.0+
- **权限安全认证**：Sa-Token 1.38.0 (支持多账号体系、登录校验与无状态 Token)
- **参数校验**：Spring Boot Validation / Jakarta Validation
- **数据序列化**：FastJSON2 (2.0.51)
- **对象存储**：七牛云 Kodo OSS SDK (7.15.0)

---

## 📁 目录架构

```text
backend/
├── pom.xml                                  # Maven 核心构建配置
├── API.md                                   # 团队前后端 RESTful API 契约文档
├── sql/
│   └── init.sql                             # 12 张核心业务表与种子数据 DDL
├── src/
│   └── main/
│       ├── java/com/campus/
│       │   ├── Application.java             # Spring Boot 启动主类
│       │   ├── common/                      # 全局通用 Result、业务异常与统一拦截器、WebSocket Server
│       │   ├── config/                      # 跨域、Sa-Token、MyBatis-Plus、WebSocket、七牛云等配置
│       │   └── modules/                     # 🔥 Package-by-Feature 按业务领域划分的模块
│       │       ├── admin/                   # 管理员领域 (SysUser)
│       │       ├── student/                 # 学生中心领域 (Student)
│       │       ├── activity/                # 活动核心领域 (Activity 相关)
│       │       ├── mall/                    # 积分商城领域 (Gift, 兑换记录)
│       │       ├── certificate/             # 电子证书领域 (Certificate)
│       │       └── system/                  # 系统通用领域 (通知, 文件上传, Q&A)
│       └── resources/
│           ├── application.yml              # 全局通用配置 (Sa-Token, 端口, 路径)
│           ├── application-dev.yml          # 本地开发环境数据源配置
│           └── application-local.yml        # 本地私密凭证 (AK/SK，已被 Git 忽略)
```

---

## 🚀 快速启动指南

### 1. 启动中间件
在项目根目录双击运行 [`start-dev.bat`](../start-dev.bat) 启动 Docker 中的 MySQL、Redis 与 RabbitMQ。

### 2. 数据库初始化
将 [`sql/init.sql`](./sql/init.sql) 导入本地 MySQL `campus_activity` 数据库中。已内置管理员及测试学生账号（密码均已 BCrypt 加盐加密）。

### 3. 本地运行
- **IDE 运行**：使用 IntelliJ IDEA 打开项目，找到 `com.campus.Application` 直接右键 Run。
- **命令行运行**：
  ```bash
  mvn spring-boot:run
  ```

---

## 🔗 核心规范与文档链接

- **Markdown 接口规范文档**：[API.md](./API.md)（所有前后端接口契约以此为准）
- **数据库建表脚本**：[sql/init.sql](./sql/init.sql)（12 张业务数据表）
- **服务端口与上下文**：`http://localhost:8080/api`
- **实时通信 WebSocket**：`ws://localhost:8080/api/ws/{userType}/{userId}`

---

## 🔒 研发红线与安全须知

1. **密码安全**：严禁明文比对或明文入库，统一调用 `PasswordUtil.encode()` 与 `PasswordUtil.matches()`。
2. **防超卖事务**：礼品兑换、积分扣减与订单写入必须处于同一数据库事务中，且礼品表需使用 `@Version` 乐观锁扣减库存。
3. **敏感信息脱敏**：用户/学生实体的密码字段已添加 `@TableField(select = false)` 与 `@JsonProperty(access = WRITE_ONLY)`，严禁对外接口返回密码。
4. **防刷保护**：文件上传接口 `/common/upload` 已加 `@SaCheckLogin` 鉴权，仅限登录用户上传。
