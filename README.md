# 校园活动积分平台 (EduPoint) - 团队协同开发规范与后端工程手册

> **致全体实训组员**：本手册是平台项目研发的**唯一颗粒度对齐基准（Single Source of Truth）**。涵盖平台角色权限、四大核心业务体系、三大高级拓展功能、数据库规范、前后端协同契约，以及后端微服务工程架构与启动指南。请在编写代码前仔细阅读，确保三端（后端、管理端、移动端）开发步调与业务逻辑严密一致。

---

## 📌 一、开发协同核心基准 (Single Source of Truth)

本项目严格遵循**契约先行 (Contract-First)** 原则，以下基准文件是系统的绝对规范：

1. **业务需求基准**：[《校园活动积分平台需求说明.md》](./校园活动积分平台需求说明.md)（业务流程、规则逻辑、验收标准）
2. **数据库基准**：[`src/main/resources/db/migration/V1.0.0__init.sql`](./src/main/resources/db/migration/V1.0.0__init.sql)（12 张生产级数据表 + 种子数据，Flyway 自动纳管版本）
3. **接口契约基准**：[`API.md`](./API.md)（定义所有 RESTful 接口、入参出参、业务状态码与 Token 规范）
4. **Git 分支规范**：三端日常开发统一在 `dev` 分支提交合并，上线前统一提 PR 审查合并至 `master`。

---

## 👥 二、平台角色与核心权限边界

平台名称：**校园活动积分平台（EduPoint）**

| 角色 | 权限与数据边界 | 认证体系 | 客户端 |
| :--- | :--- | :--- | :--- |
| **学生 (Student)** | 仅可查看和操作**本人**的活动报名、打卡记录、个人可用与累计积分、礼品兑换订单及个人电子证书。不可越权查看其他同学隐私数据。 | Sa-Token 学生账号体系（`student_token`） | 移动学生端 (`mobile-app` H5 / 小程序) |
| **管理员 (Admin / Counselor)** | 具备全局活动的发布与维护、礼品管理、学生档案管理、兑换订单核销与取消、数据大屏查看及报表导出权限。 | Sa-Token 后台管理体系（`admin_token`） | 管理后台 (`admin-web` Vue 3) |

> ⚠️ **认证隔离红线**：管理员与学生的访问令牌（Token）严格物理隔离，禁止混用。所有受保护接口必须强校验令牌有效性与所属角色，防止权限越界。

---

## 🧩 三、核心业务功能架构与开发任务清单 (研发进度追踪)

### 📊 团队协同分工与模块进度概览

| 负责角色 | 核心模块职责 | 研发进度 | 状态概括 |
| :--- | :--- | :---: | :--- |
| **👨‍💻 开发者 A** | **活动打卡与履历链路**（业务流转、防作弊与位置核验） | **~95%** | **全链路基本完成**：活动报名、候补排队、Redisson 防超发打卡、动态二维码核验、移动端大厅/详情/打卡页均已联调通过；仅剩管理端打卡明细页待绑接口 |
| **👨‍💻 开发者 B** | **积分商城与交易链路**（高并发防超卖与事务安全） | **100%** | **全链路已完成**：积分台账、排行榜、礼品维护、Redis+MQ+乐观锁防超卖兑换、核销与撤销退分、双端页面已全部联调通过 |
| **👨‍💻 开发者 C** | **基础架构、数据看板与高级扩展**（全局基建、大屏与难点攻坚） | **~65%** | **核心基建已全闭环**：鉴权基建、管理员/学生档案管理CRUD、七牛云OSS、EasyExcel报表导出、WS推送已实装；**全力冲刺：数据大屏、AI客服与证书验真** |

---

### 1. 账号与权限管理闭环模块

#### (1) 登录认证与安全基建

- [X] **[开发者 C] 后端管理员登录与登出**：`POST /user/login`、`POST /user/logout`（Sa-Token 独立鉴权体系）
- [X] **[开发者 C] 后端管理员个人信息获取**：`GET /user/info`（兼容 `/auth/me`）
- [X] **[开发者 C] 管理端登录界面实装**：`admin-web/src/views/login/index.vue`（含密码错误安全模糊提示）
- [X] **[开发者 C] 后端移动学生端登录与登出**：`POST /student/login`、`POST /student/logout`（学号 + 密码）
- [X] **[开发者 C] 后端学生个人中心资料与修改**：`GET /student/info`、`PUT /student/profile`
- [X] **[开发者 C] 移动端学生登录与个人中心实装**：`mobile-app/src/pages/my/index.vue`（含资料编辑、头像上传、未提货角标）
- [X] **[开发者 C] 公共文件上传基建**：`POST /common/upload`（七牛云 OSS 私有空间预签名时效 URL 安全上传）

#### (2) 系统管理员与后台用户管理

- [X] **[开发者 C] 后端管理员列表分页多条件查询**：`GET /user/list`
- [X] **[开发者 C] 后端新增管理员账号**：`POST /user/create`（BCrypt 密码加密、角色分配）
- [X] **[开发者 C] 后端修改管理员信息**：`PUT /user/update`
- [X] **[开发者 C] 后端启用/停用管理员账号**：`PUT /user/{id}/status`
- [X] **[开发者 C] 后端删除/注销管理员账号**：`DELETE /user/{id}`（逻辑删除）
- [X] **[开发者 C] 管理端管理员管理界面实装与 API 对接**：`admin-web/src/views/user/index.vue`（实装真实增删改查、状态切换、下拉自动筛选、七牛云头像上传与重置密码）

#### (3) 学生档案与主账号管理

- [X] **[开发者 C] 后端学生档案分页查询与班级筛选**：`GET /student/list`
- [X] **[开发者 C] 后端录入新学生档案**：`POST /student/create`（BCrypt 密码加密、默认密码 `123456`、学号唯一校验）
- [X] **[开发者 C] 后端修改学生基本档案**：`PUT /student/update`（姓名、班级、手机号、学院、专业等）
- [X] **[开发者 C] 后端冻结/解冻学生账号**：`PUT /student/{id}/status`（联动踢下线机制）
- [X] **[开发者 C] 后端重置学生密码**：`PUT /student/{id}/reset-password`（统一重置为初始密码 `123456`，联动踢下线）
- [X] **[开发者 C] 管理端学生档案管理界面实装与 API 对接**：`admin-web/src/views/student/index.vue`（实装真实增删改查、动态班级过滤、下拉自动筛选、七牛云头像上传、冻结切换与密码重置）

---

### 2. 活动全生命周期与打卡链路

#### (1) 活动发布与维护

- [X] **[开发者 A] 后端活动分页列表查询**：`GET /activity/page`（支持学生端/管理端基础查询，含分类与状态过滤）
- [X] **[开发者 A] 后端查询活动详情**：`GET /activity/{id}`
- [X] **[开发者 A] 后端管理端创建活动**：`POST /activity/create`（支持标题、时间、描述、奖励积分）
- [X] **[开发者 A] 后端管理端修改活动**：`PUT /activity/update`
- [X] **[开发者 A] 后端管理端删除/归档活动**：`DELETE /activity/{id}`（已有报名的活动严禁物理删除，仅支持状态变更）
- [X] **[开发者 A] 数据库活动分类支持**：`activity.category` 字段（`ACADEMIC`, `VOLUNTEER`, `SPORTS`, `PRACTICE`, `OTHER`）
- [X] **[开发者 A] 管理端活动管理与编辑页面实装**：`admin-web/src/views/activity/index.vue` 与 `admin-web/src/views/activity/edit.vue`（已接入真实分页检索、分类过滤、状态自动查询、新增/修改表单及动态二维码弹窗）

#### (2) 报名机制与候补排队 (Waitlist)

- [X] **[开发者 A] 后端学生报名活动接口**：`POST /student/activity/signup`
  - 校验活动时间，限本人报名且同一活动限报一次；
  - 判断是否达到名额上限 `registration_limit`：未满员写入 `REGISTERED`，满员自动进入候补排队并写入 `WAITING`（分配排队序号 `queue_number`）。
- [X] **[开发者 A] 后端学生取消报名与自动递补**：`POST /student/activity/cancel-signup`
  - 取消已报名记录，系统自动按顺序检索该活动候补队伍中排第 1 名的学生递补转正，并触发通知。

#### (3) 动态打卡与防作弊核验 (Checkin)

- [X] **[开发者 A] 后端生成/刷新动态防伪签到二维码**：`GET /activity/{id}/qrcode`
  - 服务端生成含活动ID、随机 `nonce`、有效时间戳和 HMAC-SHA256 防伪签名的动态 Token，时效刷新自动作废旧码（防止截图代打卡）。
- [X] **[开发者 A] 后端扫码打卡签到核心核验**：`POST /student/activity/checkin`
  - 校验当前是否处于有效打卡时段；
  - 单次核销：校验动态二维码 `nonce` 未被使用；
  - 位置校验：开启定位核验时，计算打卡 GPS 与活动目标点的球面距离（Haversine），判断 $\le$ `location_radius`；
  - 强事务写入：打卡记录标记已完成、增加学生对应奖励积分并记录流水，同一事务内执行。
- [X] **[开发者 A] 后端学生查询我的活动参与记录**：`GET /student/activity/my-list`
- [X] **[开发者 A] 后端管理端全量查询学生活动打卡明细**：`GET /activity/record/list`
- [ ] **[开发者 A] 管理端学生活动记录与审核界面实装**：`admin-web/src/views/student-activity/index.vue`（待替换静态假数据）

#### (4) 移动端活动相关页面实装

- [X] **[开发者 A] 移动端活动大厅实装**：`mobile-app/src/pages/activity/index.vue`（实现分类 Tab、活动列表滚动加载、状态展示）
- [X] **[开发者 A] 移动端活动详情页**：`mobile-app/src/pages/activity/detail.vue`（支持报名、候补状态提示、导航路线指引）
- [X] **[开发者 A] 移动端扫码打卡页**：`mobile-app/src/pages/activity/checkin.vue`（唤起相机扫码、获取当前 GPS 经纬度并提交打卡）
- [X] **[开发者 A] 移动端参与活动记录页**：`mobile-app/src/pages/activity/records.vue`（展示学生全部历史打卡记录）

---

### 3. 积分流转与商城交易链路 (✅ 开发者 B 全链路 100% 已完成)

#### (1) 积分账户与流水台账

- [X] **[开发者 B] 双积分账户底层架构**：`current_points`（可用积分余额，可增可减）与 `total_points`（历史累计总积分，只增不减，专供排行榜）
- [X] **[开发者 B] 后端学生查看可用积分与累计汇总**：`GET /student/score/balance`
- [X] **[开发者 B] 后端学生分页查询个人积分流水**：`GET /student/score/records`（财务级流水台账）
- [X] **[开发者 B] 移动端积分明细页实装**：`mobile-app/src/pages/my/points.vue`
- [X] **[开发者 B] 后端积分排行榜接口**：`GET /student/score/rank`（基于 `total_points` 降序，兼顾同分稳定排序）
- [X] **[开发者 B] 移动端积分排行榜实装**：`mobile-app/src/pages/rank/index.vue`（高颜值前三名与榜单）
- [X] **[开发者 B] 后端管理端人工调分**：`POST /student/score/adjust`（防负数校验、写入变动流水台账）
- [X] **[开发者 B] 管理端积分管理与调分界面实装**：`admin-web/src/views/student-point/index.vue`（支持列表检索与弹窗实时调分）

#### (2) 礼物库全生命周期维护

- [X] **[开发者 B] 后端礼品列表查询**：`GET /gift/list`（支持分类与上下架过滤）
- [X] **[开发者 B] 后端单个礼品详情查询**：`GET /gift/{id}`
- [X] **[开发者 B] 后端管理端新增礼品**：`POST /gift/create`
- [X] **[开发者 B] 后端管理端修改礼品**：`PUT /gift/update`
- [X] **[开发者 B] 后端管理端上架/下架切换状态**：`PUT /gift/{id}/status`
- [X] **[开发者 B] 后端管理端删除礼品**：`DELETE /gift/{id}`（底层拦截已存在兑换记录的礼品，防止业务数据孤岛）
- [X] **[开发者 B] 管理端礼品库维护界面实装**：`admin-web/src/views/gift/index.vue`（新增、编辑、上下架开关、封面上传与删除）
- [X] **[开发者 B] 移动端礼品商城与详情实装**：`mobile-app/src/pages/gift/index.vue` 与 `mobile-app/src/pages/gift/detail.vue`（支持步进器多件兑换）

#### (3) 订单兑换、高并发防超卖与核销闭环

- [X] **[开发者 B] 后端学生兑换礼品接口**：`POST /student/gift/exchange`
  - **高并发三层防超卖**：Redis Lua 预扣减库存 + 数据库乐观锁（`version`）+ 条件更新（`stock >= quantity`）；
  - **RabbitMQ 削峰异步落库**：生成防重复唯一订单号与提货核销码（Pickup Code）；
  - **积分与库存强事务**：扣减积分与落库在事务内保证强一致性。
- [X] **[开发者 B] 后端学生端查询我的兑换订单**：`GET /student/gift/my-orders`
- [X] **[开发者 B] 移动端我的兑换订单实装**：`mobile-app/src/pages/my/orders.vue`（支持按状态筛选与提货核销码展示）
- [X] **[开发者 B] 后端管理端查询兑换订单列表**：`GET /gift/order/list`
- [X] **[开发者 B] 后端管理端确认送达/一键核销**：`POST /gift/order/confirm-delivery`（更新状态为已送达，触发 WebSocket 提醒学生）
- [X] **[开发者 B] 后端管理端取消兑换订单**：`POST /gift/order/cancel`（回退库存，并原路退还学生积分及记录流水）
- [X] **[开发者 B] 管理端兑换订单管理界面实装**：`admin-web/src/views/student-gift/index.vue`

---

### 4. 实时通知与数据看板大屏

#### (1) 通知与 WebSocket 基建

- [X] **[开发者 C] WebSocket 服务端长连接搭建**：`ws://localhost:8080/api/ws/{userType}/{userId}`（基于 `@ServerEndpoint` 实现用户与客户端 Session 管理）
- [X] **[开发者 C] 交易与调分消息定向/广播推送**：已在订单核销送达、学生下单及调分流程中接入 WebSocket 自动通知
- [ ] **[开发者 C] 后端用户查阅个人系统通知列表**：`GET /notice/my-list`
- [ ] **[开发者 C] 后端标记系统通知为已读**：`PUT /notice/{id}/read`
- [ ] **[开发者 C] 双端全局消息监听与红点弹窗提示**：前端建立 WS 心跳并在接收到推送时弹出 Element Plus / Uni-app 通知气泡

#### (2) 数据看板与海量数据导出 (✅ 开发者 C 全链路 100% 已完成)

- [X] **[开发者 C] 后端数据看板核心指标统计**：`GET /dashboard/overview`（学生总人数、签到人次、平均签到率、流通积分总量）
- [X] **[开发者 C] 后端数据看板排行榜与最新打卡流水**：`GET /dashboard/top-students`（Top5 积分榜与近期实时打卡列表）
- [X] **[开发者 C] 后端基于 EasyExcel 导出活动参与明细报表**：`GET /admin/export/activities`（接入数据库真实数据导出）
- [X] **[开发者 C] 后端基于 EasyExcel 导出学生积分明细报表**：`GET /admin/export/points`（接入数据库真实流水导出）
- [X] **[开发者 C] 管理端数据大屏实装与 API 对接**：`admin-web/src/views/dashboard/index.vue`（替换当前静态 Mock 数据，联调真实指标卡与趋势图表）

---

## 🌟 四、高级拓展功能规划与开发清单

### 1. 地理位置与地图路线导航

- [X] **[开发者 A] 数据库与实体层地理信息扩展**：活动表经度（`longitude`）、纬度（`latitude`）、打卡半径（`location_radius`）及核验开关（`location_check_enabled`）
- [ ] **[开发者 A] 管理端地图拖拽与搜索选点交互**：在活动创建/编辑时集成高德或腾讯地图 API，拖拽选点并自动回填经纬度
- [ ] **[开发者 A] 移动端 GPS 打卡与本地地图 App 导航唤起**：详情页点击一键拉起本地地图 App 规划路线，打卡页精准捕获设备定位

### 2. AI 智能业务客服助手

- [X] **[开发者 C] 数据库知识库表与种子数据设计**：`qa_knowledge` 表及官方初始问答条目纳管
- [ ] **[开发者 C] 后端管理端 FAQ 知识库 CRUD 接口**：`GET /ai/faq/page`、`POST /ai/faq/create`、`PUT /ai/faq/update`、`DELETE /ai/faq/{id}`
- [ ] **[开发者 C] 后端双端查询 FAQ 常见问题与欢迎语**：`GET /ai/faq/list`
- [ ] **[开发者 C] 后端 AI 客服流式智能对话接口**：`POST /ai/chat/stream`（基于 SSE 流式响应，支持数据安全鉴权与本地知识库关键词兜底）
- [ ] **[开发者 C] 移动端 AI 悬浮入口与打字机对话框**：参考原型接入流式对话

### 3. 电子荣誉证书生成与公开验真

- [X] **[开发者 C] 数据库电子荣誉证书表与种子数据设计**：`certificate` 表纳管防伪编码 `certificate_no`
- [ ] **[开发者 C] 后端打卡完成自动生成电子荣誉证书**：学生打卡成功后按活动规则自动触发签发并入库
- [ ] **[开发者 C] 后端学生端查询我的电子证书列表**：`GET /certificate/my-list`
- [ ] **[开发者 C] 后端管理端全量查询电子证书**：`GET /certificate/list`
- [ ] **[开发者 C] 后端对外公开扫码验真接口**：`GET /certificate/verify/{certificateNo}`（敏感数据脱敏、公开扫码可查）
- [ ] **[开发者 C] 后端管理端撤销电子荣誉证书**：`PUT /certificate/{id}/revoke`
- [ ] **[开发者 C] 移动端与管理端证书页面实装**：移动端实现证书卡片预览与保存图片，管理端实现证书吊销与查询

---

## 🔒 五、底层数据模型与安全规约

### 1. 核心业务表清单 (全 12 张表)

| 序号 | 表名 | 中文名称 | 核心特征与约束 |
| :--- | :--- | :--- | :--- |
| 1 | `sys_user` | 管理员/后台用户表 | BCrypt 加密、`uk_username` |
| 2 | `student` | 学生档案与主账户表 | 双积分体系（`current_points` / `total_points`）、`uk_student_no` |
| 3 | `activity` | 校园活动表 | **`category` 分类**、经纬度、签到半径、候补与证书开关 |
| 4 | `student_activity` | 学生活动参与打卡表 | `uk_stu_act (student_id, activity_id)` 防重复打卡 |
| 5 | `activity_waitlist` | 报名排队候补队列表 | 候补序号 `queue_number`、`uk_act_stu_wait` 防重复排队 |
| 6 | `activity_checkin_code` | 动态打卡二维码防伪表 | `uk_nonce` 一次性单次核销、过期时间 |
| 7 | `student_point_record` | 学生积分明细台账表 | 财务级对账（记录变动前、变动值、变动后余额） |
| 8 | `gift` | 积分商城礼品库表 | `version` 乐观锁防超卖、`total_stock`、`stock` |
| 9 | `student_gift` | 礼品兑换订单表 | **`quantity` 件数**、`uk_order_no`、`uk_pickup_code` 提货码 |
| 10 | `sys_notice` | 业务通知与离线消息表 | 支持 WebSocket 消息持久化与已读未读管理 |
| 11 | `certificate` | 电子荣誉证书及验真表 | `uk_cert_no` 防伪编号、扫码验真核验计数 |
| 12 | `qa_knowledge` | AI 客服平台知识库表 | 官方问答知识库、分类检索权重 |

### 2. 数据库设计红线

- **软删除与审计字段**：全部 12 张表统一包含 `create_time`、`update_time`、`deleted`（逻辑删除，MyBatis-Plus 自动拦截过滤）；
- **主外键业务解耦**：采用逻辑外键关联（业务 ID 关联），生产环境严禁建立物理外键约束以提高吞吐性能。

---

## 🛠️ 六、后端技术架构与工程指引

### 1. 核心技术栈与依赖
- **核心框架**：Spring Boot 3.3.2
- **持久层框架**：MyBatis-Plus 3.5.7 + MySQL Connector/J 8.0+
- **权限安全认证**：Sa-Token 1.38.0 (多账号体系、登录校验与无状态 Token)
- **缓存与消息队列**：Redis 7.0 + RabbitMQ 3.13
- **参数校验**：Spring Boot Validation / Jakarta Validation
- **数据序列化**：FastJSON2 (2.0.51)
- **对象存储**：七牛云 Kodo OSS SDK (7.15.0)

### 2. 后端工程目录架构
```text
backend/
├── pom.xml                                  # Maven 核心构建配置
├── docker-compose.yml                       # Docker 一键启动基础设施 (MySQL 8.0, Redis 7.0, RabbitMQ 3.13)
├── API.md                                   # 团队前后端 RESTful API 契约文档
├── 校园活动积分平台需求说明.md              # 业务流程、规则逻辑与需求说明文档
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
│           ├── db/migration/
│           │   ├── V1.0.0__init.sql         # Flyway 数据库迁移初始化脚本
│           │   └── V1.0.1__add_demo_student.sql # 演示学生账号与种子数据
│           ├── application.yml              # 全局通用配置 (Sa-Token, 端口, 路径)
│           ├── application-dev.yml          # 本地开发环境数据源配置
│           └── application-local.yml        # 本地私密凭证 (AK/SK，已被 Git 忽略)
```

### 3. 本地运行与调试
- **基础设施一键启动 (Docker Compose)**：
  在 `backend` 目录下执行如下命令，即可一键启动 MySQL 8.0、Redis 7.0 与 RabbitMQ 3.13（会自动导入 `V1.0.0__init.sql` 建表并初始化测试账号）：
  ```bash
  docker compose up -d
  ```
  > 如需进入 RabbitMQ 网页端管理后台：访问 `http://localhost:15672`（账号：`guest`，密码：`guest`）。
- **IDE 运行**：使用 IntelliJ IDEA 打开 `backend`，运行 `com.campus.Application` 主类即可（支持断点与 DevTools 热更）。
- **命令行运行**：
  ```bash
  mvn spring-boot:run
  ```
- **后端服务端口与 API 前缀**：`http://localhost:8080/api`
- **实时通信 WebSocket**：`ws://localhost:8080/api/ws/{userType}/{userId}`

---

## 🔑 七、预置开箱即用测试账号

| 角色 | 登录账号 | 初始密码 | 姓名 / 特征 | 适用端 |
| :--- | :--- | :--- | :--- | :--- |
| **超级管理员** | `admin` | `123` | 超级管理员 (全模块权限) | 管理后台 (`admin-web`) |
| **辅导员** | `counselor` | `123` | 李辅导员 (活动与订单管理) | 管理后台 (`admin-web`) |
| **核心演示学生** | `2023010401` | `123456` | 张晓明 (可用 350 / 累计 420，光荣榜第1名) | 学生端 (`mobile-app`) |
| **测试学生 1** | `20220101` | `123456` | 张伟 (可用积分 180 / 累计 320，晨跑打卡记录) | 学生端 (`mobile-app`) |
| **测试学生 2** | `20220102` | `123456` | 王芳 (可用积分 280 / 累计 280，有待发放订单) | 学生端 (`mobile-app`) |
| **测试学生 3** | `20220103` | `123456` | 陈明 (可用积分 110 / 累计 210) | 学生端 (`mobile-app`) |
| **测试学生 4** | `20220104` | `123456` | 赵丽 (可用积分 150 / 累计 150) | 学生端 (`mobile-app`) |
| **测试学生 5** | `20220105` | `123456` | 孙浩 (可用积分 40 / 候补排队中) | 学生端 (`mobile-app`) |

---

## 💻 八、三端工程快速指引

```text
├── backend/            # 后端微服务 (Spring Boot 3.3.2 + Java 17/23 + MyBatis-Plus + Sa-Token)
├── admin-web/          # 管理后台前端 (Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts)
├── mobile-app/         # 学生移动端 (Uni-app Vue 3 跨端规范 + Vite + @dcloudio/uni-ui + Pinia)
```

1. **后端服务 (`backend/`)**：
   - 端口 `8080`，API 根路径 `http://localhost:8080/api`。
2. **管理前端 (`admin-web/`)**：
   - 执行 `npm run dev`，默认访问 `http://localhost:3000`（若遇到 Windows NAT 系统保留 3000 端口限制，服务将智能自动切换至 `http://localhost:3200` 备用端口）。
3. **移动前端 (`mobile-app/`)**：
   - 执行 `npm run dev:h5`，访问 `http://localhost:5173`；已集成 `@dcloudio/uni-ui` 自动导入与 Pinia 跨页面积分实时响应。

---

## 📡 九、前后端通信协议规范

### 1. 统一响应格式
```json
{
  "code": 200,          // 200-成功, 401-未登录/Token失效, 500-业务异常
  "message": "操作成功",  // 用户友好提示信息
  "data": { ... },      // 数据载体
  "timestamp": 1772874000000
}
```

### 2. 鉴权请求头 (Header)
```http
satoken: 2b8f7c6e-1234-5678-90ab-cdef12345678
```

### 3. 常见业务错误码约定
- `401`：认证失效，拦截器自动清空缓存并跳往登录页；
- `504`：已打卡拦截（“您已参与打卡，请勿重复操作”）；
- `505`：积分余额不足（“可用积分不足，兑换失败”）；
- `506`：库存超卖拦截（“礼品已被抢光”）。

---

## 🔒 十、研发红线与安全须知

1. **密码安全**：严禁明文比对或明文入库，统一调用 `PasswordUtil.encode()` 与 `PasswordUtil.matches()`。
2. **防超卖事务**：礼品兑换、积分扣减与订单写入必须处于同一数据库事务中，且礼品表需使用 `@Version` 乐观锁扣减库存。
3. **敏感信息脱敏**：用户/学生实体的密码字段已添加 `@TableField(select = false)` 与 `@JsonProperty(access = WRITE_ONLY)`，严禁对外接口返回密码。
4. **防刷保护**：文件上传接口 `/common/upload` 已加 `@SaCheckLogin` 鉴权，仅限登录用户上传。
5. **统一简体中文提交说明**：严格遵循 Conventional Commits 规范，如 `feat(activity): ...`、`fix(auth): ...`。
6. **敏感信息严禁提交**：数据库密码、七牛云 AK/SK 请统一配置在 `application-local.yml`（已加入 `.gitignore`）。
