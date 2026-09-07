# 学生活动打卡与积分兑换管理系统 - 后端服务 (Backend)

基于 **Spring Boot 3.3.2**、**Java 17/23**、**MyBatis-Plus** 与 **Sa-Token** 开发的高性能微服务后端。

---

## 🛠️ 技术栈与依赖架构

- **核心框架**：Spring Boot 3.3.2
- **持久层框架**：MyBatis-Plus 3.5.7 + MySQL Connector/J
- **权限安全认证**：Sa-Token 3.x (Spring Boot 3 规范适配)
- **API 交互式文档**：Knife4j OpenAPI 3 (v4.5.0)
- **参数校验**：Spring Boot Validation / Jakarta Validation
- **数据序列化**：FastJSON2 (2.0.51)

---

## 📁 目录架构

```text
backend/
├── pom.xml                                  # Maven 核心配置
├── sql/
│   └── init.sql                             # 数据库建表与初始数据 DDL
├── src/
│   └── main/
│       ├── java/com/campus/
│       │   ├── Application.java             # Spring Boot 启动主类
│       │   ├── common/                      # 全局通用 Result、业务异常与统一拦截器
│       │   ├── config/                      # 跨域 CORS、Knife4j、MyBatis-Plus 分页插件配置
│       │   ├── controller/                  # RESTful 接口控制器
│       │   ├── entity/                      # 数据库 PO 持久化实体
│       │   ├── mapper/                      # MyBatis-Plus 数据访问接口
│       │   └── service/                     # 业务接口与逻辑实现层
│       └── resources/
│           ├── application.yml              # 全局通用配置 (Sa-Token, Knife4j)
│           └── application-dev.yml          # 本地开发数据源与日志级别配置
```

---

## 🚀 快速启动指南

### 1. 准备工作
- 安装 JDK 17 或更高版本（支持 JDK 23）。
- 安装并启动 MySQL 8.0+，执行 `sql/init.sql` 完成数据库及表的创建。
- 检查 `src/main/resources/application-dev.yml` 中的数据库账号与密码配置。

### 2. 编译与打包
```bash
# 跳过测试打包出独立 Fat-JAR
mvn clean package -DskipTests
```

### 3. 运行服务
```bash
# 方式一：直接使用 Maven 插件运行
mvn spring-boot:run

# 方式二：运行构建好的可执行 JAR
java -jar target/campus-activity-platform-1.0.0-SNAPSHOT.jar
```

---

## 🔗 服务端口与核心端点

- **服务端口**：`8080` (Context-Path: `/api`)
- **交互式 API 文档 (Knife4j)**：[http://localhost:8080/api/doc.html](http://localhost:8080/api/doc.html)
- **服务健康探针**：[http://localhost:8080/api/health](http://localhost:8080/api/health)
