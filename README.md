
# 🎓 校园活动积分平台 (EduPoint) - 后端服务

> 基于 Spring Boot 3 + Redis + Sa-Token 构建的高性能、高并发校园活动全生命周期与积分交易管理系统。

[](https://www.oracle.com/java/)
[](https://spring.io/projects/spring-boot)
[](https://www.mysql.com/)
[](https://redis.io/)
[](https://sa-token.cc/)
[](https://www.docker.com/)
[License](https://www.google.com/search?q=./LICENSE)

[功能特性](#-功能特性) • [技术亮点](#-核心技术亮点) • [技术选型](#-技术选型) • [快速启动](#-快速启动) • [工程架构](#-工程架构) • [关联项目](#-核心工程矩阵)

&lt;/div&gt;

---

## 📖 项目简介

**EduPoint（校园活动积分平台）** 旨在解决高校活动组织繁琐、出勤核验难、学生活动参与积极性低以及综测学时核算工作量大的痛点。平台打通**“活动发布 ➔ 名额争夺 ➔ 现场打卡 ➔ 积分发放 ➔ 积分商城秒杀 ➔ 个人能力履历”**全链路，通过引入分布式锁、消息队列削峰与冷热数据缓存等高并发设计，保障高并发抢活动名额与礼品兑换时的系统稳定性与资产安全。

本仓库为 **EduPoint 核心后端微服务/单体工程**，提供高内聚低耦合的 RESTful 接口、分布式事务与防超卖机制，并为管理后台与移动端提供严格隔离的权限鉴权。

---

## 🌟 核心工程矩阵

| 工程端 | 技术栈 | 仓库地址 / 描述 |
| :--- | :--- | :--- |
| **后端服务 (EduPoint-backend)** | Spring Boot 3 + MySQL + Redis + Sa-Token | [本仓库](https://github.com/sanomelba125-ctrl/EduPoint-backend) |
| **学生移动端 (EduPoint-mobile-app)** | UniApp / Vue3 / H5 / 小程序 | [EduPoint-mobile-app](https://www.google.com/search?q=https://github.com/sanomelba125-ctrl/EduPoint-mobile-app) |
| **运营管理后台 (EduPoint-admin-web)** | Vue 3 + Vite + Element Plus + Pinia | 校园管理员与辅导员管理看板 |

---

## 🎯 功能特性

```
EduPoint 平台全景
├── 📅 校园活动全生命周期
│   ├── 活动发布与审核机制（草稿 / 待审 / 发布 / 报名中 / 进行中 / 已结束）
│   ├── 高并发抢名额与候补队列（容量管控、防重复报名）
│   └── 现场动态核销签到（动态刷新二维码 / 时效防作弊 / 地理围栏辅助）
├── 🎁 积分商城与资产中台
│   ├── 严密双向积分台账（流水唯一索引、冻结/解冻、对账防篡改）
│   ├── 高并发礼品秒杀（Redis 预减库存 + 异步排队 + 乐观锁双重兜底）
│   └── 线下礼品核销链路（一次性核销码校验、过期/取消自动返还积分）
├── 🏅 履历画像与证书防伪
│   ├── 个人活动履历与积分资产总览
│   ├── 五维综合能力雷达图生成
│   └── 电子荣誉证书验真（防伪唯一 Hash、数字签名与真伪核验 API）
├── 📊 监控大屏与管理运营
│   ├── 实时数据看板（活动热度、签到率、实时积分流转监控）
│   ├── WebSocket 实时推送打卡动态与兑换快讯
│   └── 基于 EasyExcel 的学生档案、签到名单与积分台账报表批量导出
└── 🤖 扩展特色服务
    └── 基于大语言模型的校园活动智能问答与学时规则咨询
```

---

## 🚀 核心技术亮点

### 1. 高并发防超发与活动报名排队
* **Redisson 分布式锁**：针对火爆活动报名与打卡场景，采用 `Redisson` 分布式细粒度锁（锁粒度精确至 `activity_id:user_id`），防止重复点击或刷接口导致的重复入库。
* **Redis 计数器与原子操作**：名额扣减前置于 Redis，利用原子自减与 Lua 脚本做预校验，过滤无效并发请求，避免数据库打满。

### 2. 积分商城防超卖与事务安全保障
* **三道防线抵御超卖**：
  1. 第一道：Redis 缓存预扣减库存与用户频控，瞬间承接高并发并发请求。
  2. 第二道：消息队列解耦异步下单，实现削峰填谷，降低数据库瞬间写压力。
  3. 第三道：数据库层采用乐观锁（Version 机制 / `stock > 0` 条件更新）做绝对防御。
* **积分资产一致性**：严格的流水台账机制，所有积分增减均伴随唯一的流水记录与幂等校验，支持核销取消与异常场景下的反向事务冲正。

### 3. 多端账号多重隔离鉴权 (Sa-Token)
* 针对学生端与管理员端，基于 Sa-Token 建立完全独立的鉴权会话体系：
  * **学生端**：使用独立 `student_token`，仅具备报名、打卡、个人资产查看与兑换权限。
  * **管理端**：使用独立 `admin_token`，具备活动审核、礼品核销、数据大屏及报表导出权限。
* 物理层面防水平越权与垂直提权，杜绝 Token 混用风险。

### 4. 实时数据大屏与 WebSocket 推送
* 结合 Spring WebSocket / STOMP，将全校实时打卡、积分变动与活动报满事件秒级下发至管理员大屏，保障数据交互的低延迟。

### 5. 生产级数据库版本控制 (Flyway)
* 摒弃手工执行 SQL，项目内置 Flyway 数据库迁移管理，所有表结构与初始化种子数据纳入 Git 版本追踪，支持多环境无缝平滑升级。

---

## 🛠️ 技术选型

| 领域 | 关键组件 / 框架 | 选型说明 |
| :--- | :--- | :--- |
| **核心框架** | Spring Boot 3.x | 现代化 Java 开发底座，高性能、支持最新 Java 语法特性 |
| **语言环境** | JDK 17 / 21 LTS | 长期支持版本，兼顾生态与性能 |
| **持久层** | MyBatis-Plus 3.5+ | 简化 CRUD，内置代码生成与分页插件 |
| **安全认证** | Sa-Token 1.37+ | 轻量级、易上手、支持多账号体系物理隔离的权限框架 |
| **关系型数据库** | MySQL 8.0 | 支持事务与 JSON 字段，高可靠数据存储 |
| **缓存与锁** | Redis 7.x + Redisson | 缓存热点数据、分布式锁与原子计数器 |
| **数据库迁移** | Flyway | 数据库版本纳管，杜绝多环境表结构差异 |
| **报表引擎** | EasyExcel 3.x | 阿里开源高性能 Excel 解析引擎，防止海量导出 OOM |
| **对象存储** | 七牛云 OSS / 阿里云 OSS | 证书图片、活动封面图与资源持久化 |
| **容器化编排** | Docker & Docker Compose | 一键编排依赖中间件，开箱即用 |

---

## ⚙️ 快速启动

### 1. 前置依赖
* JDK 17 或 21
* Maven 3.8+
* Docker & Docker Compose（推荐，用于一键拉起 MySQL、Redis）

### 2. 克隆代码
```bash
git clone https://github.com/sanomelba125-ctrl/EduPoint-backend.git
cd EduPoint-backend
```

### 3. 一键启动基础设施
项目已提供完整的 `docker-compose.yml`，执行以下命令即可在本地一键拉起 MySQL 与 Redis：
```bash
docker compose up -d
```
> **提示**：MySQL 启动后，Flyway 会在应用启动时自动检测并迁移 `src/main/resources/db/migration` 下的全部建表脚本，无需手动导入 SQL。

### 4. 配置环境变量
复制根目录下的 `.env.example` 为 `.env` 或 `application-local.yml`，按需调整连接凭证：
```bash
cp .env.example .env
```
核心配置项参考：
```properties
# 数据库配置
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/edupoint?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your_password

# Redis 配置
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=

# Sa-Token 配置
SA_TOKEN_TIMEOUT=2592000
```

### 5. 编译与启动
```bash
# 采用 Maven Wrapper 编译
./mvnw clean package -DskipTests

# 启动 Spring Boot 服务
./mvnw spring-boot:run
```
服务默认启动在 `http://localhost:8080`。

---

## 📂 工程目录结构

```
EduPoint-backend
├── .github/                # GitHub Actions CI/CD 工作流
├── deploy/                 # 部署相关脚本与配置
├── src/main/
│   ├── java/com/edupoint/
│   │   ├── common/         # 全局通用模块 (统一响应 Result、全局异常处理、常量、注解)
│   │   ├── config/         # 基础设施配置 (Sa-Token、Redis/Redisson、WebSocket、MyBatis-Plus)
│   │   ├── controller/     # 接口层 (按业务划分 admin / student / open)
│   │   ├── service/        # 核心业务逻辑层 (活动管理、积分交易、用户鉴权)
│   │   ├── mapper/         # 数据持久层 (MyBatis-Plus Mapper)
│   │   ├── model/          # 数据实体 (entity / dto / vo)
│   │   └── util/           # 工具类 (加密、二维码生成、校验)
│   └── resources/
│       ├── db/migration/   # Flyway 生产版本迁移脚本 (V1.0.0__init.sql 等)
│       └── application.yml # 核心工程配置
├── API.md                  # RESTful API 接口规范与入参定义
├── Dockerfile              # 后端容器构建定义
├── docker-compose.yml      # 本地一体化中间件编排
└── pom.xml                 # Maven 依赖与插件纳管
```

---

## 📑 接口文档与开发规范

* 完整 RESTful 接口出入参请参阅：[API.md](https://www.google.com/search?q=./API.md)
* 业务详细需求与状态机流转请参阅：[校园活动积分平台需求说明.md](https://www.google.com/search?q=./%E6%A0%A1%E5%9B%AD%E6%B4%BB%E5%8A%A8%E7%A7%AF%E5%88%86%E5%B9%B3%E5%8F%B0%E9%9C%80%E6%B1%82%E8%AF%B4%E6%98%8E.md)

---

## 🤝 贡献与开源许可

本项目遵循 [MIT License](https://www.google.com/search?q=./LICENSE) 开源协议。欢迎提交 PR 或创建 Issue 交流！
