# 校园活动积分平台 (EduPoint) - 后端 RESTful API 接口规范文档

> **【开发规范红线】**：**严格禁止私自增加业务接口！系统前后端只允许对接与实现本文档已定义的接口，严禁擅自新增非 API 文档规定的接口！**  
> **版本**：v1.0.0  
> **基础路径 (Base URL)**：`http://localhost:8080/api` (Server Context-Path 为 `/api`)  
> **数据格式**：Request / Response 均使用 `application/json;charset=UTF-8`（文件上传接口除外）  
> **时间规范**：统一采用 `YYYY-MM-DD HH:mm:ss` 格式字符串或毫秒级时间戳  
> **文档适用**：Web 管理后台 (`campus-admin-web`)、学生移动端 H5 (`campus-mobile-app`) 及后端研发人员

---

## 目录
- [一、全局协议与交互规范](#一全局协议与交互规范)
  - [1.1 统一响应结构 (Result)](#11-统一响应结构-result)
  - [1.2 业务状态码清单 (ResultCode)](#12-业务状态码清单-resultcode)
  - [1.3 身份认证与 Token 传递 (Sa-Token)](#13-身份认证与-token-传递-sa-token)
  - [1.4 分页参数与结构](#14-分页参数与结构)
- [二、模块接口定义](#二模块接口定义)
  - [1. 身份认证与用户管理模块](#1-身份认证与用户管理模块-user--student-auth)
  - [2. 校园活动管理与查询模块](#2-校园活动管理与查询模块-activity)
  - [3. 活动报名、候补与动态打卡模块](#3-活动报名候补与动态打卡模块-student-activity)
  - [4. 积分台账与排行榜模块](#4-积分台账与排行榜模块-points--leaderboard)
  - [5. 积分商城与礼品兑换模块](#5-积分商城与礼品兑换模块-gift--order)
  - [6. 管理后台数据看板模块](#6-管理后台数据看板模块-dashboard)
  - [7. 系统业务通知与 WebSocket 模块](#7-系统业务通知与-websocket-模块-notice--websocket)
  - [8. 电子荣誉证书与验真模块](#8-电子荣誉证书与验真模块-certificate)
  - [9. AI 客服与智能问答模块](#9-ai-客服与智能问答模块-ai-qa)
  - [10. 通用文件上传模块](#10-通用文件上传模块-common-upload)
- [三、测试种子数据与账号](#三测试种子数据与账号)

---

## 一、全局协议与交互规范

### 1.1 统一响应结构 (Result)
所有接口无论成功或失败，HTTP 状态码均统一返回 `200 OK`，实际业务结果通过响应体中的 JSON 对象表示：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1772874000000
}
```

- `code` [Integer]：业务状态码（200 为成功，其余为业务异常）
- `message` [String]：结果反馈提示信息（面向用户或前端交互提示）
- `data` [Object/Array/null]：实际承载的业务数据，无数据返回时为 `null`
- `timestamp` [Long]：服务端响应时刻的毫秒级时间戳

---

### 1.2 业务状态码清单 (ResultCode)

| 状态码 | 含义 | 对应场景与前端处理策略 |
|:---|:---|:---|
| **200** | 操作成功 | 正常处理 `data` |
| **400** | 请求参数有误 | 校验失败，Toast 弹出 `message` 提示 |
| **401** | 账号未登录或登录已失效 | Token 无效/过期，清空本地存储并跳转至登录页 |
| **403** | 没有访问权限 | 越权访问拦截（如学生调用管理员维护接口） |
| **404** | 资源不存在 | 查无此记录 |
| **500** | 业务逻辑处理失败 / 系统异常 | 事务回滚或通用错误，展示友好错误提示 |
| **501** | 用户不存在 | 账号输入错误 |
| **502** | 登录密码错误 | 密码错误 |
| **503** | 活动未开始或已截止 | 活动时间校验拦截 |
| **504** | 已参与过该活动打卡 | 防重放校验拦截，严禁重复打卡 |
| **505** | 可用积分余额不足 | 积分兑换校验拦截 |
| **506** | 礼品库存不足 | 兑换时库存售罄/并发超卖拦截 |

---

### 1.3 身份认证与 Token 传递 (Sa-Token)

- 系统采用 **Sa-Token** 体系。管理端（管理员/教师）与移动端（学生）账号体系独立鉴权，令牌隔离。
- 会话持久化用的是 `sa-token-redis-jackson`，令牌与 `SaSession` 都落在 Redis，所以「踢下线」在多实例部署下同样生效。
  会话策略（`application.yml`）：`timeout: 86400`（绝对有效期 24 小时）、`active-timeout: 1800`（30 分钟无操作失效）、`auto-renew: true`（有操作自动续签）。
- 登录标识带身份前缀：管理员是 `admin:{id}`，学生是 `student:{id}`。服务层统一通过 `AuthUtil.requireAdminId()` / `requireStudentId()` 取当前用户，前缀不匹配直接 403——拿学生令牌调管理端接口会在这一层被拦掉。
- 客户端在调用登录接口成功后，服务端返回会话令牌 `token`。
- 后续所有受保护接口，前端必须在 HTTP 请求 Header 中携带该令牌：
  ```http
  satoken: d4e1a0b3-9876-4f32-bc10-1234567890ab
  ```
  *(注：Cookie 中也会自动下发名为 `satoken` 的值)*
- 角色解析由 `StpInterfaceImpl` 按前缀分流：学生统一 `STUDENT`；管理员优先读 `SaSession` 里缓存的 `role`，未命中再回源 `sys_user` 表并回写缓存。
- 常见会话失效响应：账号被强制下线/禁用 → 「账号已被管理员强制下线或已被禁用，请重新登录」；登录态过期或异地登录 → 「登录态已失效或已在其他设备登录，请重新登录」；角色不足 → 「无权访问此功能模块：角色权限不足」。

---

### 1.4 分页参数与结构

#### 通用分页请求 Query 参数：
- `page`：当前页码，Integer，从 1 开始，默认 1
- `size`：每页条数，Integer，默认 10，最大 100

#### 通用分页响应数据结构：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [ ... ],
    "total": 52,
    "size": 10,
    "current": 1,
    "pages": 6
  },
  "timestamp": 1772874000000
}
```

---

## 二、模块接口定义

### 1. 身份认证与用户管理模块 (/user & /student auth)

#### 1.1 管理员登录
- **请求方式**：`POST`
- **请求路径**：`/user/login`
- **权限要求**：无需 Token
- **请求参数 (JSON Body)**：
  ```json
  {
    "username": "admin",
    "password": "123"
  }
  ```
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "user": {
        "id": 1,
        "username": "admin",
        "nickname": "超级管理员",
        "avatar": "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png",
        "role": "SUPER_ADMIN",
        "phone": "13800000001"
      }
    }
  }
  ```

#### 1.2 管理员退出登录
- **请求方式**：`POST`
- **请求路径**：`/user/logout`
- **权限要求**：需登录，且角色为 `SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **成功响应**：`{ "code": 200, "message": "退出成功", "data": null }`

#### 1.3 获取当前登录管理员信息
- **请求方式**：`GET`
- **请求路径**：`/user/info`（兼容别名 `/user/me`、`/auth/info`、`/auth/me`）
- **权限要求**：需登录，且角色为 `SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一

#### 1.4 管理员列表分页多条件查询
- **请求方式**：`GET`
- **请求路径**：`/user/list`
- **权限要求**：**仅 `SUPER_ADMIN`**（系统用户管理是超管独占功能）
- **请求 Query 参数**：
  - `page`: 当前页码（默认 1）
  - `size`: 每页大小（默认 10）
  - `keyword`: 模糊检索关键字（用户名或姓名昵称）
  - `role`: 角色过滤 (`SUPER_ADMIN` / `ADMIN` / `COUNSELOR`)
  - `status`: 状态过滤 (`1`-正常启用, `0`-已被禁用)
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "records": [
        {
          "id": 1,
          "username": "admin",
          "nickname": "超级管理员",
          "avatar": "https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png",
          "role": "SUPER_ADMIN",
          "phone": "13800000001",
          "status": 1,
          "createTime": "2024-09-01 10:00:00",
          "updateTime": "2024-09-01 10:00:00"
        }
      ],
      "total": 1,
      "size": 10,
      "current": 1,
      "pages": 1
    }
  }
  ```

#### 1.5 新增管理员账号
- **请求方式**：`POST`
- **请求路径**：`/user/create`
- **权限要求**：**仅 `SUPER_ADMIN`**
- **请求参数 (JSON Body)**：
  ```json
  {
    "username": "teacher_wang",
    "password": "123",
    "nickname": "学工处王老师",
    "role": "ADMIN",
    "phone": "13812345678",
    "avatar": ""
  }
  ```
- **成功响应**：`{ "code": 200, "message": "管理员账号创建成功", "data": { ... } }`

#### 1.6 修改管理员信息
- **请求方式**：`PUT`
- **请求路径**：`/user/update`
- **权限要求**：**仅 `SUPER_ADMIN`**
- **请求参数 (JSON Body)**：
  ```json
  {
    "id": 2,
    "nickname": "学工处王主任",
    "role": "ADMIN",
    "phone": "13812345679",
    "status": 1,
    "password": ""
  }
  ```
- **成功响应**：`{ "code": 200, "message": "管理员信息更新成功", "data": { ... } }`
- **安全防线**：禁止把内建超管（`id=1` 或用户名 `admin`）降权为其他角色；改角色 / 改状态 / 改密码都会调 `StpUtil.kickout()` 立刻作废该账号的在线会话。

#### 1.7 启用/停用管理员账号
- **请求方式**：`PUT`
- **请求路径**：`/user/{id}/status`
- **权限要求**：**仅 `SUPER_ADMIN`**
- **请求参数**：URL Path 参数 `id`；Query 或 Body 参数 `status` (`1`-启用, `0`-停用)
- **成功响应**：`{ "code": 200, "message": "账号已启用", "data": null }`
- **安全防线**：禁止停用内建超管（`id=1` 或用户名 `admin`）；停用后立即踢下线。

#### 1.8 删除/注销管理员账号 (逻辑删除)
- **请求方式**：`DELETE`
- **请求路径**：`/user/{id}`
- **权限要求**：**仅 `SUPER_ADMIN`**（保护限制：禁止删除初始超管或当前登录者自身）
- **成功响应**：`{ "code": 200, "message": "管理员账号注销成功", "data": null }`
- **安全防线**：删除后立即踢下线。

#### 1.9 重置管理员密码
- **请求方式**：`PUT`
- **请求路径**：`/user/{id}/reset-password`
- **权限要求**：**仅 `SUPER_ADMIN`**
- **请求参数 (JSON Body)**：
  ```json
  {
    "newPassword": "123"
  }
  ```
- **成功响应**：`{ "code": 200, "message": "管理员密码重置成功", "data": null }`
- **安全防线**：新密码以 BCrypt 密文入库，重置后立即踢下线，强制用新密码重新登录。

---

> **编号说明**：本模块下「学生端接口」自成一组，编号从 `1.4` 重新起算，因此与上文管理端的 `1.4` ~ `1.9` 存在重号。请按标题里的「管理端 / 学生端」区分，不要只按数字定位。

#### 1.4 学生端登录
- **请求方式**：`POST`
- **请求路径**：`/student/login`
- **权限要求**：无需 Token
- **请求参数 (JSON Body)**：
  ```json
  {
    "studentNo": "20220101",
    "password": "123456"
  }
  ```
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "stu-b2c3d4e5-f678-9012-3456-7890abcdef12",
      "student": {
        "id": 1,
        "studentNo": "20220101",
        "name": "张伟",
        "gender": 1,
        "avatar": "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
        "college": "信息工程学院",
        "major": "计算机科学与技术",
        "className": "计科2201班",
        "phone": "13911110001",
        "totalPoints": 320,
        "currentPoints": 180
      }
    }
  }
  ```

#### 1.5 学生端退出登录
- **请求方式**：`POST`
- **请求路径**：`/student/logout`
- **权限要求**：需学生登录
- **成功响应**：`{ "code": 200, "message": "退出成功", "data": null }`

#### 1.6 学生获取个人中心资料
- **请求方式**：`GET`
- **请求路径**：`/student/info`
- **权限要求**：需学生登录（`@SaCheckLogin` + 服务层 `AuthUtil.requireStudentId()` 前缀校验，管理端令牌会被拒绝）

#### 1.7 学生修改个人资料（姓名/昵称、手机、头像、性别）
- **请求方式**：`PUT`
- **请求路径**：`/student/profile`
- **权限要求**：需学生登录
- **请求参数 (JSON Body)**：
  ```json
  {
    "name": "张三三",
    "phone": "13911110088"
  }
  ```
- **说明**：字段逐项判空，只有传了值的字段才会写回 `student` 表；空字符串不更新（因此本接口不能用来把手机号清空）。

#### 1.7.1 学生自主修改登录密码
- **请求方式**：`PUT`
- **请求路径**：`/student/change-password`
- **权限要求**：需学生登录
- **请求参数 (JSON Body)**：
  ```json
  {
    "oldPassword": "123456",
    "newPassword": "654321",
    "confirmPassword": "654321"
  }
  ```
- **成功响应**：`{ "code": 200, "message": "密码修改成功，请使用新密码重新登录", "data": null }`
- **校验规则**：新密码与确认密码必须一致；新密码不能与原密码相同；原密码只做 BCrypt 密文比对（不保留明文兜底）。
- **说明**：校验通过后以 BCrypt 重新加密入库，并立即 `StpUtil.kickout()` 踢掉该学生的在线会话，强制用新密码重新登录。
- **失败响应**：原密码错误时统一返回「原密码不正确，请重新输入」，不回吐学号姓名，也不区分账号是否存在。
- **安全要求**：日志只记录 `studentId`，**绝不记录用户输入的原密码或库中密文**。

#### 1.8 管理端分页查询学生档案列表
- **请求方式**：`GET`
- **请求路径**：`/student/list`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **Query 参数**：
  - `page`: 当前页码（默认 1）
  - `size`: 每页条数（默认 10）
  - `keyword`: 模糊检索关键字（学号或姓名）
  - `className`: 班级名称筛选
  - `college`: 学院模糊过滤
  - `major`: 专业模糊过滤
  - `status`: 账号状态（1-正常, 0-冻结）
- **成功响应**：返回 `records` 学生列表（含 `currentPoints`, `totalPoints` 等）及分页信息。

#### 1.9 管理端录入新学生档案
- **请求方式**：`POST`
- **请求路径**：`/student/create`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **唯一性**：学号做双层校验——先按学号查一次（**忽略逻辑删除**，被注销过的学号会被判为「历史占用」并给出明确提示），再由 `uk_student_no` 唯一索引兜底
- **请求参数 (JSON Body)**：
  ```json
  {
    "studentNo": "20240105",
    "name": "赵敏",
    "gender": 0,
    "className": "软件2201",
    "college": "计算机科学学院",
    "major": "软件工程",
    "phone": "13912345678",
    "password": "123456"
  }
  ```
- **成功响应**：`{ "code": 200, "message": "学生档案录入成功", "data": { ... } }`

#### 1.10 管理端修改学生档案
- **请求方式**：`PUT`
- **请求路径**：`/student/update`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **说明**：把 `status` 改成 `0` 或修改密码都会调 `StpUtil.kickout()` 踢掉该学生的在线会话。
- **请求参数 (JSON Body)**：
  ```json
  {
    "id": 5,
    "name": "赵敏",
    "gender": 0,
    "className": "软件2201",
    "college": "计算机科学学院",
    "major": "软件工程",
    "phone": "13912345679",
    "status": 1
  }
  ```
- **成功响应**：`{ "code": 200, "message": "学生档案修改成功", "data": { ... } }`

#### 1.11 管理端冻结/解冻学生账号
- **请求方式**：`PUT`
- **请求路径**：`/student/{id}/status`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **请求参数**：URL Path `id`；Query 或 Body 参数 `status` (1-正常, 0-冻结；冻结时强制踢掉对应在线会话)
- **成功响应**：`{ "code": 200, "message": "学生账号已恢复正常", "data": null }`

#### 1.12 管理端重置学生密码
- **请求方式**：`PUT`
- **请求路径**：`/student/{id}/reset-password`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **说明**：默认重置为 `123456`，BCrypt 入库后立即踢下线。
- **请求参数 (JSON Body)**：
  ```json
  {
    "newPassword": "123456"
  }
  ```
- **成功响应**：`{ "code": 200, "message": "学生密码已成功重置", "data": null }`

#### 1.13 管理端删除/注销学生档案
- **请求方式**：`DELETE`
- **请求路径**：`/student/{id}`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **说明**：逻辑删除（`deleted` 置 1），同时销毁学生在线会话。
- **成功响应**：`{ "code": 200, "message": "学生档案注销成功", "data": null }`

#### 1.14 获取系统中所有有效班级列表
- **请求方式**：`GET`
- **请求路径**：`/student/classes`
- **权限要求**：`SUPER_ADMIN` / `ADMIN` / `COUNSELOR` 之一
- **成功响应**：`{ "code": 200, "message": "操作成功", "data": ["软件2201", "软件2202", "信管2202"] }`

---

#### 1.13 管理端分页查询管理员列表 ｜ 1.14 新增后台管理员 ｜ 1.15 修改管理员信息 ｜ 1.16 启用/停用/删除后台用户

> ⚠️ **本节为历史遗留的重复章节，内容与上文 1.4 ~ 1.8 完全重复，且缺少权限要求等说明。请以上文 1.4 ~ 1.8 为准**（`/user/list` / `/user/create` / `/user/update` / `/user/{id}/status` / `DELETE /user/{id}` 全部要求 `SUPER_ADMIN`）。
> 保留原始条目仅为兼容可能存在的历史跳转，后续整理时可直接删除本节。

- 请求方式 / 路径：`GET /user/list`、`POST /user/create`、`PUT /user/update`、`PUT /user/{id}/status`、`DELETE /user/{id}`
- 权限要求：均为 **`SUPER_ADMIN`**
- 安全防线：禁止删除或停用内建超管（`id=1` 或用户名 `admin`）；禁止删除当前登录者自身；删除/停用/改角色/重置密码都会立即踢下线。

---

### 2. 校园活动管理与查询模块 (/activity)

#### 2.1 分页查询活动列表 (学生端/管理端通用)
- **请求方式**：`GET`
- **请求路径**：`/activity/page`
- **Query 参数**：
  - `page`, `size`
  - `keyword`：活动名称关键字
  - `status`：状态（0-未开始, 1-进行中, 2-已截止/结束, 3-已关闭）
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "records": [
        {
          "id": 1,
          "title": "2026春季校园晨跑健康打卡",
          "coverImage": "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500",
          "points": 15,
          "startTime": "2026-03-01 06:30:00",
          "endTime": "2026-03-15 08:30:00",
          "registrationLimit": 100,
          "participantCount": 24,
          "waitlistEnabled": 1,
          "locationName": "西区田径操场",
          "locationAddress": "湖北省武汉市洪山区大学园路1号西区体育场",
          "longitude": 114.362145,
          "latitude": 30.523412,
          "locationRadius": 500,
          "locationCheckEnabled": 1,
          "certificateEnabled": 0,
          "status": 1
        }
      ],
      "total": 1,
      "size": 10,
      "current": 1
    }
  }
  ```

#### 2.2 查询活动详情
- **请求方式**：`GET`
- **请求路径**：`/activity/{id}`
- **成功响应**：返回活动全部字段（包括 `ruleDescription` 规则说明、`description` 富文本详情）。

#### 2.3 管理端创建活动
- **请求方式**：`POST`
- **请求路径**：`/activity/create`
- **权限要求**：管理员
- **请求参数 (JSON Body)**：
  ```json
  {
    "title": "“青年AI与新质生产力”前沿讲座",
    "description": "讲座详情说明...",
    "ruleDescription": "入场扫码打卡，全程参与颁发荣誉证书",
    "coverImage": "https://your-oss-domain/cover.png",
    "points": 30,
    "startTime": "2026-03-10 14:30:00",
    "endTime": "2026-03-10 17:30:00",
    "registrationLimit": 50,
    "waitlistEnabled": 1,
    "locationName": "图书馆一楼大报告厅",
    "locationAddress": "图书馆主楼A区一层东侧报告厅",
    "longitude": 114.364200,
    "latitude": 30.524100,
    "locationRadius": 200,
    "locationCheckEnabled": 1,
    "certificateEnabled": 1,
    "certificateTitle": "AI前沿学术讲座结业证书",
    "certificateIssuer": "校科技创新协会"
  }
  ```

#### 2.4 管理端修改活动
- **请求方式**：`PUT`
- **请求路径**：`/activity/update`
- **请求参数**：包含 `id` 及需更新的活动字段。

#### 2.5 管理端删除/归档活动
- **请求方式**：`DELETE`
- **请求路径**：`/activity/{id}`
- **说明**：已有报名打卡记录的活动禁止物理删除，系统自动转换为归档/下架状态。

#### 2.6 管理端生成/刷新活动动态签到二维码
- **请求方式**：`GET`
- **请求路径**：`/activity/{id}/qrcode`
- **权限要求**：管理员
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "二维码生成成功",
    "data": {
      "activityId": 1,
      "nonce": "NONCE_7a9f8b2c4d1e",
      "sign": "SIGN_9a8b7c6d5e4f3a2b1c",
      "expireTime": "2026-03-07 18:30:00",
      "validSeconds": 300,
      "qrContent": "http://localhost:5173/#/checkin?actId=1&nonce=NONCE_7a9f8b2c4d1e&sign=SIGN_9a8b7c6d5e4f3a2b1c"
    }
  }
  ```

---

### 3. 活动报名、候补与动态打卡模块 (/student/activity)

#### 3.1 学生报名活动
- **请求方式**：`POST`
- **请求路径**：`/student/activity/signup`
- **权限要求**：学生登录
- **请求参数 (JSON Body)**：
  ```json
  {
    "activityId": 1
  }
  ```
- **业务规则与响应**：
  - 名额未满：直接报名成功，返回状态 `1`（已报名）。
  - 名额已满且开启候补：自动进入候补排队，返回提示与排队序号 `queueNumber`。
  - 重复报名：拦截返回 400 提示“您已报名该活动”。
  - 成功响应示例：
    ```json
    {
      "code": 200,
      "message": "报名成功",
      "data": {
        "status": "ENROLLED", 
        "waitlist": false,
        "message": "您已成功正式报名！"
      }
    }
    ```

#### 3.2 学生取消报名 (触发自动递补)
- **请求方式**：`POST`
- **请求路径**：`/student/activity/cancel-signup`
- **请求参数 (JSON Body)**：`{ "activityId": 1 }`
- **说明**：正式报名学生取消后，系统自动查找该活动 `activity_waitlist` 中排名第 1 的候补学生自动递补为正式名额，并通过 WebSocket / 消息中心发送通知。

#### 3.3 扫码动态打卡签到
- **请求方式**：`POST`
- **请求路径**：`/student/activity/checkin`
- **权限要求**：学生登录
- **请求参数 (JSON Body)**：
  ```json
  {
    "activityId": 1,
    "nonce": "NONCE_7a9f8b2c4d1e",
    "sign": "SIGN_9a8b7c6d5e4f3a2b1c",
    "checkinLatitude": 30.523418,
    "checkinLongitude": 114.362140
  }
  ```
- **核心校验流程**：
  1. 校验学生是否已正式报名；
  2. 校验防篡改签名 `sign` 与 `nonce` 是否在有效期内且未被核销使用；
  3. 若活动开启了定位核验，计算与活动设定坐标距离，超过 `locationRadius` 拒绝打卡并提示；
  4. 同一事务内完成：核销二维码、更新参与记录为已完成 (`status=2`)、发放活动奖励积分并记入流水台账、触发电子证书颁发判定。
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "打卡签到成功",
    "data": {
      "pointsAwarded": 15,
      "currentPoints": 195,
      "certificateIssued": true,
      "checkinTime": "2026-03-07 17:35:12"
    }
  }
  ```

#### 3.4 学生查询我的活动参与记录
- **请求方式**：`GET`
- **请求路径**：`/student/activity/my-list`
- **Query 参数**：`page`, `size`, `status` (1-已报名, 2-已完成, 3-已失效)

#### 3.5 管理端全量查询学生活动记录
- **请求方式**：`GET`
- **请求路径**：`/activity/record/list`
- **Query 参数**：`page`, `size`, `studentName`, `studentNo`, `activityTitle`, `status`

---

### 4. 积分台账与排行榜模块 (/student/score)

#### 4.1 学生查看当前积分与汇总
- **请求方式**：`GET`
- **请求路径**：`/student/score/balance`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "currentPoints": 180,
      "totalPoints": 320
    }
  }
  ```

#### 4.2 学生分页查询个人积分变动流水
- **请求方式**：`GET`
- **请求路径**：`/student/score/records`
- **Query 参数**：`page`, `size`, `type`
- **成功响应**：
  ```json
  {
    "code": 200,
    "data": {
      "records": [
        {
          "id": 1,
          "type": 1,
          "changePoints": 15,
          "beforePoints": 165,
          "afterPoints": 180,
          "description": "参与活动打卡奖励【2026春季校园晨跑健康打卡】",
          "createTime": "2026-03-07 07:15:00"
        }
      ],
      "total": 1
    }
  }
  ```

#### 4.3 积分排行榜 (学生端)
- **请求方式**：`GET`
- **请求路径**：`/student/score/rank`
- **Query 参数**：`page`, `size` (默认每页 20 条)
- **排序规则**：按 `total_points` 累计总积分从高到低排序，同分按学号升序稳定排序。
- **成功响应**：
  ```json
  {
    "code": 200,
    "data": {
      "records": [
        { "rank": 1, "studentNo": "20220101", "name": "张伟", "avatar": "...", "totalPoints": 320 },
        { "rank": 2, "studentNo": "20220102", "name": "王芳", "avatar": "...", "totalPoints": 280 }
      ],
      "myRank": {
        "rank": 1,
        "totalPoints": 320
      }
    }
  }
  ```

#### 4.4 管理端人工调整学生积分
- **请求方式**：`POST`
- **请求路径**：`/student/score/adjust`
- **权限要求**：管理员
- **请求参数 (JSON Body)**：
  ```json
  {
    "studentId": 1,
    "points": 20, 
    "reason": "优秀志愿者专项加分"
  }
  ```
  *(注：`points` 为负数代表扣减积分，系统必须强校验扣减后余额不得小于 0)*

---

### 5. 积分商城与礼品兑换模块 (/gift & /student/gift)

#### 5.1 查询礼品列表 (商城浏览)
- **请求方式**：`GET`
- **请求路径**：`/gift/list`（兼容别名 `/gift/page`）
- **权限要求**：当前未加鉴权注解（管理端与移动端共用；学生端默认只查上架中的礼品）
- **Query 参数**：`page`, `size`, `status` (学生端默认只查上架中的礼品)
- **成功响应**：
  ```json
  {
    "code": 200,
    "data": {
      "records": [
        {
          "id": 1,
          "name": "校园定制环保帆布袋",
          "description": "加厚纯棉帆布，印有校徽刺绣",
          "image": "https://images.unsplash.com/photo-1544816155-12df9643f363?w=500",
          "pointsRequired": 50,
          "pointsCost": 50,
          "totalStock": 108,
          "stock": 100,
          "version": 10,
          "status": 1
        }
      ]
    }
  }
  ```
  > `pointsCost` 是 `pointsRequired` 的兼容别名（VO 里由 getter 直接派生）。
  > `totalStock` 是历史累计入库总量（只增），`stock` 是当前可用库存（兑换实时扣减、退单回补、补货自增）。

#### 5.2 管理端新增礼品
- **请求方式**：`POST`
- **请求路径**：`/gift/create`（兼容别名 `/gift/save`）
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`
- **请求参数 (JSON Body)**：
  ```json
  {
    "name": "运动智能手环 8 代",
    "description": "心率检测、50米防水",
    "image": "https://your-oss-url/band.jpg",
    "pointsRequired": 300,
    "totalStock": 20,
    "stock": 20,
    "status": 1
  }
  ```
  > 入库总量以 `totalStock` 为准，服务端把初始 `stock` 直接置成 `totalStock`；`status` 不传时默认 `1`（上架），`version` 初始化为 `0`。
  > 建库之后就**不能再通过新增/编辑改库存**了，追加入库请用 5.12 的补货接口。

#### 5.3 学生使用积分兑换礼品
- **请求方式**：`POST`
- **请求路径**：`/student/gift/exchange`
- **权限要求**：学生登录
- **请求参数 (JSON Body)**：
  ```json
  {
    "requestId": "3f2b9c1e-8a4d-4b7f-a0d5-9e6c1b2a7f30",
    "giftId": 1,
    "quantity": 1,
    "receiverName": "张伟",
    "receiverPhone": "13911110001",
    "receiverAddress": "南区学生公寓12栋302室"
  }
  ```
  > `requestId` 为客户端生成的 UUID 幂等标识（推荐必传，≤64 位）：同一次兑换意图内必须保持不变
  > （弱网重试、用户手动重试复用同一标识），兑换成功后由客户端重置。服务端按该标识去重，
  > 重复提交直接返回首次成功的订单结果，不会重复扣减积分/库存，也不会重复发送通知。
  > 若客户端未传，服务端将自动生成全局唯一降级标识兜底落库，平滑兼容旧版客户端。
- **核心逻辑**：
  1. 校验可用积分是否充足；
  2. 校验礼品可用库存并基于 `@Version` 乐观锁原子扣减；
  3. 扣减学生可用积分，记录 `student_point_record` 流水；
  4. 生成兑换订单及随机唯一的提货核销码（Pickup Code）；
  5. 触发向管理端的 WebSocket 实时新订单通知。
- **幂等保障（双保险）**：
  1. Redis `SETNX gift:exchange:idem:{studentId}:{requestId}` 占位，挡住同一标识的并发重复提交；
     占位成功但业务失败时释放（回滚），保证可重试；
  2. `student_gift.request_id` 唯一索引 `uk_request_id` 作为最后防线，Redis 不可用时由数据库拒绝重复落单；
  3. 命中已成功的 `requestId` 时直接返回原订单（`orderNo`/`pickupCode` 与首次一致）。
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "礼品兑换成功",
    "data": {
      "orderNo": "ORD202609152030451234567",
      "pickupCode": "PK09168821",
      "pointsCost": 50,
      "remainPoints": 130
    }
  }
  ```
  > 订单号格式为 `ORD` + `yyyyMMddHHmmssSSS`（毫秒级时间戳）+ 4 位随机数（共 24 位）；提货码格式为 `PK` + `MMdd`（4 位月日）+ 4 位随机数（共 10 位，例如 `PK09168821` 表示 9 月 16 日的单）。
  > 两者都由 `java.security.SecureRandom` 生成（不是 `Math.random()`，避免被反推预测）。提货码在生成前会先按 `pickup_code` 查一次库，命中则重新生成，最多重试 5 次；极端连续碰撞时追加纳秒尾数兜底（13 位）。
  > 唯一性最终仍由 `uk_order_no` / `uk_pickup_code` 唯一索引兜底；核销时忽略输入的空格与横杠后比对。

#### 5.4 学生端查询我的兑换订单
- **请求方式**：`GET`
- **请求路径**：`/student/gift/my-orders`（兼容别名 `/gift/my`、`/student/gift/my`）
- **权限要求**：学生登录（数据归属按登录态里的 `studentId` 取本人订单）
- **Query 参数**：`page`, `size`, `status`

#### 5.4.1 学生端自主取消兑换订单
- **请求方式**：`POST`
- **请求路径**：`/student/gift/orders/{id}/cancel`（兼容别名 `/gift/orders/{id}/cancel`）
- **权限要求**：学生登录
- **说明**：只能取消本人的待提货订单（`status=1`），走与管理员取消相同的状态守卫路径，默认原路退还积分并回补库存。

#### 5.5 管理端查询兑换订单列表
- **请求方式**：`GET`
- **请求路径**：`/gift/order/list`（兼容别名 `/gift/orders`、`/admin/gift/orders`）
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`（**辅导员不可访问**）
- **Query 参数**：`page`, `size`, `orderNo`, `pickupCode`, `studentName`, `status`

> 同组的 `/gift/order/metrics`（订单指标统计）权限与参数口径一致，权限同为 `SUPER_ADMIN` 或 `ADMIN`。

#### 5.6 管理端确认送达/核销发放
- **请求方式**：`POST`
- **请求路径**：`/gift/deliver`（兼容别名 `/gift/order/confirm-delivery`、`/gift/order/deliver`）
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`（**辅导员不可访问**）
- **请求参数 (JSON Body)**：
  ```json
  {
    "orderId": 1,
    "pickupCode": "PK09168821",
    "remark": "已在一站式服务大厅窗口领取核销"
  }
  ```
- **说明**：提货码比对会忽略空格与横杠（大小写不敏感）；状态更新为 `2`（已送达），并自动推送通知给对应学生。
- **并发防线**：更新语句带上 `status = 1` 条件（状态守卫），并发重复核销时只有一方能生效，另一方返回「该订单状态不是待核销状态，不可重复操作」。

#### 5.7 管理端取消兑换订单
- **请求方式**：`POST`
- **请求路径**：`/gift/order/cancel`
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`（**辅导员不可访问**）
- **请求参数 (JSON Body)**：
  ```json
  {
    "orderId": 2,
    "refundPoints": true,
    "cancelReason": "学生申请取消退换"
  }
  ```
- **说明**：状态变更为 `3`（已取消），若 `refundPoints=true` 则同一事务内自动全额退还扣除的积分、回补礼品库存并补记流水。`refundPoints` 不传时默认 `true`。
- **并发防线**：先做状态守卫条件更新，受影响行数为 0 时直接放弃（不回补库存、不退分、不记流水）。学生自主取消、管理员驳回、系统超时关单三个入口共用这一条路径。

#### 5.8 查询单个礼品详情
- **请求方式**：`GET`
- **请求路径**：`/gift/{id}`
- **权限要求**：当前未加鉴权注解（与 5.1 同口径，管理端与移动端共用）

#### 5.9 管理端修改礼品基础信息
- **请求方式**：`PUT`
- **请求路径**：`/gift/update`（兼容别名 `/gift/edit`）
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`
- **请求参数 (JSON Body)**：
  ```json
  {
    "id": 1,
    "name": "校园定制双肩包",
    "description": "轻便防泼水",
    "image": "https://your-oss-url/bag.jpg",
    "pointsRequired": 50,
    "status": 1
  }
  ```
- **说明**：本接口只维护基础信息（名称 / 描述 / 图片 / 所需积分 / 上下架状态），**不接受也不变更库存**。
  `stock` 与 `totalStock` 两个字段已从入参中移除 —— 此前它们虽被声明却不会被服务层采纳（静默丢弃），现已删掉以免误导调用方。
- **库存保护机制**：礼品实体的 `stock` / `totalStock` 标注了 MyBatis-Plus 的 `@TableField(updateStrategy = FieldStrategy.NEVER)`，实体更新生成的 SQL 里不包含这两列，因此管理员改名称/图片不会覆盖并发兑换已扣减的实时库存。库存的增减只走原子 SQL（兑换扣减 / 退单回补 / 补货自增）。
- **乐观锁**：更新语句带 `AND version = ?`；版本被并发兑换改过时命中 0 行，服务端会自动重读最新行重试一次。

#### 5.10 管理端上架/下架切换礼品货架状态
- **请求方式**：`PUT`（同时兼容 `POST`）
- **请求路径**：`/gift/{id}/status/{status}` 或 `/gift/{id}/status?status=1`
- **Query / Path 参数**：`status` (1-上架, 0-下架)
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`

#### 5.11 管理端删除礼品
- **请求方式**：`DELETE`
- **请求路径**：`/gift/{id}`
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`
- **安全防线**：若该礼品已有学生兑换订单记录，系统严格禁止物理删除，改为自动下架并提示「该礼品已有学生兑换记录，为保证订单可溯不可删除，已自动转为下架状态！」。

#### 5.12 管理端礼品补货（追加入库）
- **请求方式**：`POST`
- **请求路径**：`/gift/{id}/restock`
- **权限要求**：`SUPER_ADMIN` 或 `ADMIN`
- **Query 参数**：`quantity`（本次入库件数，必填，取值范围 `1 ~ 100000`）
- **请求示例**：`POST /api/gift/1/restock?quantity=50`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "补货成功",
    "data": {
      "id": 1,
      "name": "校园定制环保帆布袋",
      "pointsRequired": 50,
      "totalStock": 150,
      "stock": 142,
      "version": 10,
      "status": 1
    }
  }
  ```
- **说明**：
  1. 在数据库当前值上做同步原子自增：`UPDATE gift SET stock = stock + ?, total_stock = total_stock + ?, updated_by = ? WHERE id = ? AND deleted = 0`。入库总量与可用库存同时增加，不做「读快照 → 写回」，因此不会与并发的兑换扣减互相覆盖。
  2. 该语句**不修改 `version`**：库存的并发正确性由 SQL 本身保证，动版本号只会让并发的兑换白白撞乐观锁。
  3. 这是库存增加的唯一入口，且只增不减；管理员编辑礼品（5.9）不涉及库存。
- **失败响应**：
  - `quantity` 为空或 ≤ 0 → 「补货数量必须为正整数」
  - `quantity` > 100000 → 「单次补货数量不能超过 100000 件，请分批入库」
  - 礼品不存在或已被逻辑删除 → 「要补货的礼品不存在或已被删除」

---

### 6. 管理后台数据看板模块 (/dashboard)

#### 6.1 获取看板统计总览核心指标
- **请求方式**：`GET`
- **请求路径**：`/dashboard/overview`
- **权限要求**：管理员
- **成功响应**：
  ```json
  {
    "code": 200,
    "data": {
      "activityTotal": 12,
      "signupTotal": 286,
      "checkinTotal": 240,
      "checkinRate": 0.839,
      "pointsIssuedTotal": 5680
    }
  }
  ```

#### 6.2 首页积分榜前五名
- **请求方式**：`GET`
- **请求路径**：`/dashboard/top-students`
- **成功响应**：返回累计积分前 5 名的学生学号、姓名、班级及积分值。

#### 6.3 导出活动参与记录 Excel
- **请求方式**：`GET`
- **请求路径**：`/dashboard/export/activities`
- **响应**：二进制文件流 (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`)

---

### 7. 系统业务通知与 WebSocket 模块 (/notice & /ws)

#### 7.1 分页获取当前用户系统通知列表
- **请求方式**：`GET`
- **请求路径**：`/notice/my-list`
- **Query 参数**：`page`, `size`, `isRead` (0-未读, 1-已读)

#### 7.2 标记通知为已读
- **请求方式**：`PUT`
- **请求路径**：`/notice/{id}/read`

#### 7.3 WebSocket 实时长连接端点
- **连接 URL**：`ws://localhost:8080/api/ws`
- **连接鉴权协议**：连接建立后，客户端通过文本消息发送认证包：
  ```json
  {
    "action": "AUTH",
    "token": "d4e1a0b3-9876-4f32-bc10-1234567890ab",
    "userType": "STUDENT" 
  }
  ```
- **服务端推送事件类型**：
  - `new_activity`：新活动发布通知（推全员学生）
  - `gift_exchange`：学生新兑换订单（推在线管理员）
  - `activity_card`：学生完成打卡通知（推在线管理员）
  - `gift_send`：礼品核销/送达提醒（推对应学生）
  - `waitlist_promoted`：候补转正递补成功通知（推对应学生）

---

### 8. 电子荣誉证书与验真模块 (/certificate)

#### 8.1 学生查询我的电子荣誉证书
- **请求方式**：`GET`
- **请求路径**：`/certificate/my-list`
- **权限要求**：学生登录
- **成功响应**：
  ```json
  {
    "code": 200,
    "data": [
      {
        "id": 1,
        "certificateNo": "CERT-2026-ACT2-A89F3B12",
        "activityTitle": "“青年AI与新质生产力”前沿学术讲座",
        "title": "青年AI前沿学术讲座结业证书",
        "issuer": "校科技创新协会",
        "issueTime": "2026-03-01 17:00:00",
        "fileUrl": "https://images.unsplash.com/photo-1589330694653-dad6bc01cf0f?w=600",
        "status": 1
      }
    ]
  }
  ```

#### 8.2 公开扫码验真证书（真伪核验）
- **请求方式**：`GET`
- **请求路径**：`/certificate/verify/{certificateNo}`
- **权限要求**：**完全公开 (无需登录)**
- **数据脱敏保护**：不返回学生学号、密码、手机号等隐私，仅公开学生姓名脱敏（如 `张*`）、证书名称、活动名称、签发单位与有效状态。

#### 8.3 管理端分页查询电子荣誉证书台账
- **请求方式**：`GET`
- **请求路径**：`/certificate/list`
- **权限要求**：管理员
- **Query 参数**：`page`, `size`, `certificateNo`, `studentName`, `studentNo`, `activityTitle`, `status` (1-有效, 2-已撤销, 3-已过期)

#### 8.4 管理端撤销电子荣誉证书 (Revoke)
- **请求方式**：`PUT`
- **请求路径**：`/certificate/{id}/revoke`
- **权限要求**：管理员
- **请求参数 (JSON Body)**：
  ```json
  {
    "revokeReason": "学生因违反竞赛作弊纪律，经学术委员会核定撤销证书"
  }
  ```
- **说明**：状态更新为 `2`（已撤销），公开验真时将显示“已作废撤销”及原因，证书文件不可再下载。

#### 8.5 管理端荣誉证书大盘聚合指标
- **请求方式**：`GET`
- **请求路径**：`/certificate/metrics` 或 `/admin/certificate/metrics`
- **权限要求**：管理员 / 辅导员登录 (`@SaCheckRole`)
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "totalCount": 128,
      "validCount": 120,
      "revokedCount": 5,
      "expiredCount": 3,
      "verifyTotal": 356
    }
  }
  ```

---

### 9. AI 客服与智能问答模块 (/ai)

#### 9.1 获取欢迎语与常见问题快捷入口 (学生端)
- **请求方式**：`GET`
- **请求路径**：`/ai/faq/list`
- **权限要求**：公开

#### 9.2 AI 客服流式智能对话
- **请求方式**：`POST`
- **请求路径**：`/ai/chat/stream`
- **请求 Header**：`Accept: text/event-stream`
- **请求参数 (JSON Body)**：
  ```json
  {
    "question": "我兑换的保温杯在哪里领取？",
    "conversationId": "session-12345"
  }
  ```
- **数据安全与响应规范**：
  - 基于 SSE (Server-Sent Events) 流式推送回答文本片段；
  - 服务端在组装 AI 上下文前自动提取学生当前积分与订单状态，**严禁将学生密码、手机号明文输入大模型**；
  - 当模型网络不可用时，自动返回知识库预置权威兜底回答。

#### 9.3 管理端分页查询 AI 语料与 FAQ 知识库
- **请求方式**：`GET`
- **请求路径**：`/ai/faq/page`
- **权限要求**：管理员
- **Query 参数**：`page`, `size`, `keyword`, `category`, `enabled`

#### 9.4 管理端新增 AI 语料与问答条目
- **请求方式**：`POST`
- **请求路径**：`/ai/faq/create`
- **权限要求**：管理员
- **请求参数 (JSON Body)**：
  ```json
  {
    "question": "积分如何获取？",
    "answer": "通过参与校园活动并在活动现场扫动态防伪二维码打卡即可获得对应学分。",
    "category": "POINTS_RULE",
    "sortOrder": 1,
    "enabled": 1
  }
  ```

#### 9.5 管理端修改 AI 语料条目
- **请求方式**：`PUT`
- **请求路径**：`/ai/faq/update`
- **权限要求**：管理员
- **请求参数 (JSON Body)**：包含 `id`, `question`, `answer`, `category`, `sortOrder`, `enabled`

#### 9.6 管理端删除 AI 语料条目
- **请求方式**：`DELETE`
- **请求路径**：`/ai/faq/{id}`
- **权限要求**：管理员

---

### 10. 通用文件上传模块 (/common)

#### 10.1 上传图片至云存储 (七牛云 OSS)
- **请求方式**：`POST`
- **请求路径**：`/common/upload`
- **权限要求**：**必须登录** (`@SaCheckLogin` 防盗刷加固)
- **Content-Type**：`multipart/form-data`
- **Body 表单参数**：
  - `file`：二进制图片文件 (支持 jpg, png, jpeg, webp，大小限制 10MB)
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "图片上传成功",
    "data": "http://your-qiniu-domain.bkt.clouddn.com/20260307_abc123.jpg"
  }
  ```

---

### 11. 数据看板与监控大屏模块 (/dashboard & /admin/dashboard)

#### 11.1 获取仪表盘核心指标概览
- **请求方式**：`GET`
- **请求路径**：`/dashboard/overview` 或 `/admin/dashboard/overview`
- **权限要求**：管理员登录 (`@SaCheckLogin`)
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "totalStudents": 5,
      "totalCheckins": 4,
      "checkinRate": 80.0,
      "circulatingPoints": 760,
      "totalPoints": 1050,
      "totalActivities": 6,
      "categoryDistribution": [
        { "category": "文体活动", "count": 2, "percentage": 33.3 },
        { "category": "志愿服务", "count": 2, "percentage": 33.3 },
        { "category": "学术讲座", "count": 1, "percentage": 16.7 },
        { "category": "社会实践", "count": 1, "percentage": 16.7 }
      ],
      "weeklyCheckinTrend": [
        { "date": "2026-09-03", "count": 0 },
        { "date": "2026-09-04", "count": 1 },
        { "date": "2026-09-05", "count": 0 },
        { "date": "2026-09-06", "count": 0 },
        { "date": "2026-09-07", "count": 2 },
        { "date": "2026-09-08", "count": 1 },
        { "date": "2026-09-09", "count": 0 }
      ]
    }
  }
  ```

#### 11.2 获取学生积分排行榜 (Top 5) 与实时签到动态流水
- **请求方式**：`GET`
- **请求路径**：`/dashboard/top-students` 或 `/admin/dashboard/top-students`
- **权限要求**：管理员登录 (`@SaCheckLogin`)
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "topStudents": [
        {
          "studentId": 1,
          "studentNo": "20220101",
          "name": "张伟",
          "college": "信息工程学院",
          "major": "软件工程",
          "className": "软件2201",
          "avatar": "",
          "totalPoints": 320,
          "currentPoints": 180,
          "rank": 1
        },
        {
          "studentId": 2,
          "studentNo": "20220102",
          "name": "王芳",
          "college": "信息工程学院",
          "major": "计算机科学与技术",
          "className": "计科2201",
          "avatar": "",
          "totalPoints": 280,
          "currentPoints": 280,
          "rank": 2
        }
      ],
      "recentCheckins": [
        {
          "recordId": 101,
          "studentId": 1,
          "studentName": "张伟",
          "studentNo": "20220101",
          "avatar": "",
          "activityId": 1,
          "activityTitle": "2026春季校园马拉松",
          "points": 50,
          "checkinTime": "2026-09-08 15:30:22"
        }
      ]
    }
  }
  ```

---

## 三、测试种子数据与账号

系统数据库在初始化时已注入以下开箱即用的测试账号与数据（密码均经 BCrypt 加盐加密）：

| 用户角色 | 登录账号 | 初始明文密码 | 姓名 / 说明 |
|:---|:---|:---|:---|
| **超级管理员** | `admin` | `123` | 超级管理员，拥有全部权限 |
| **辅导员/管理员** | `counselor` | `123` | 李辅导员 |
| **学生测试账号 1** | `20220101` | `123456` | 张伟 (可用积分: 180, 累计积分: 320) |
| **学生测试账号 2** | `20220102` | `123456` | 王芳 (可用积分: 280, 累计积分: 280) |
| **学生测试账号 3** | `20220103` | `123456` | 陈明 (可用积分: 110, 累计积分: 210) |
| **学生测试账号 4** | `20220104` | `123456` | 赵丽 (可用积分: 150, 累计积分: 150) |
| **学生测试账号 5** | `20220105` | `123456` | 孙浩 (可用积分: 40, 累计积分: 90) |

---

## 四、候补功能后端 Bug 修复与联调规范 (Waitlist Bug Fix Protocol)

> **对齐说明**：移动端（`mobile-app`）已全面支持候补队列（Waitlist）的前端交互，包含活动打卡大厅状态条、详情页「取消候补」按钮以及我的活动记录页面的「候补中」Tab（`status=4`）。后端需按本节要求完成接口对齐。详细提示词参见同目录下 [《候补功能后端修复提示词.md》](./候补功能后端修复提示词.md)。

### 4.1 接口缺陷与对齐清单

#### 1. `GET /student/activity/my-list` 增加候补记录支持 (P0 致命)
- **现状**：`myList` 仅查询 `student_activity` 表。当学生只在 `activity_waitlist` 表中有排队记录时，传入 `status=4` 或查询全部时查不到候补记录。
- **对齐要求**：
  - 当前端请求 `GET /student/activity/my-list?status=4` 时，后端查询 `activity_waitlist` 中 `status=1`（候补中）的记录，并 JOIN `activity` 表返回活动摘要（`title`, `coverImage`, `startTime`, `endTime`, `locationName`, `points` 等），VO 的 `status` 字段固定为 `4`，`queueNumber` 返回当前排队序号。
  - 当前端请求 `status` 为空（全部记录）时，合并返回正式报名、出勤与正在候补中的记录。

#### 2. `GET /activity/{id}` 与 `GET /activity/page` 增加当前学生参与状态 (P1 重要)
- **现状**：活动列表与详情直接返回实体，无当前学生状态，前端需额外请求 `my-list` 逐个判断。
- **对齐要求**：
  - 当请求上下文携带有效的学生 Token 时，响应 VO 中动态注入：
    - `myEnrollmentStatus`: `"NONE"`（未报名）/ `"ENROLLED"`（已报名）/ `"WAITLIST"`（候补中）/ `"COMPLETED"`（已出勤）
    - `myQueueNumber`: 仅当 `myEnrollmentStatus == "WAITLIST"` 时返回排队序号，其余为 `null`。
  - 未登录或管理员访问时，返回 `"NONE"` 与 `null`。

#### 3. `POST /student/activity/cancel-signup` 支持取消候补 (P1 确认)
- **现状**：后端已包含 `ActivityWaitlist w.setStatus(3)` 逻辑。
- **对齐要求**：
  - 请求体统一为 `{ "activityId": <id> }`。若学生为正式报名，取消报名并触发下一名候补学生自动晋升；若学生为候补中，将候补状态更新为 `3`（已取消），释放排队位置。

---

### 9. 系统业务通知中心模块 (/notice)

#### 9.1 分页获取当前登录用户系统通知列表
- **请求方式**：`GET`
- **请求路径**：`/notice/my-list`
- **Query 参数**：
  - `page`: 当前页码（默认 1）
  - `size`: 每页大小（默认 10）
  - `isRead`: 状态过滤（0-未读, 1-已读, 不传为全部）
  - `keyword`: 搜索关键词（可选）
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
      "records": [
        {
          "id": 1,
          "receiverType": 2,
          "receiverId": 1,
          "type": "points_adjusted",
          "typeName": "积分调账",
          "title": "系统积分调账通知",
          "content": "您的账户积分变动 +50，当前可用余额 250 分。调账原因：优秀志愿者嘉奖",
          "bizId": 10,
          "isRead": 0,
          "createTime": "2026-03-09 10:00:00"
        }
      ],
      "total": 1,
      "size": 10,
      "current": 1,
      "pages": 1
    }
  }
  ```

#### 9.2 查询当前登录用户未读通知条数
- **请求方式**：`GET`
- **请求路径**：`/notice/unread-count`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": 3
  }
  ```

#### 9.3 标记单条通知为已读
- **请求方式**：`PUT`
- **请求路径**：`/notice/{id}/read`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": true
  }
  ```

#### 9.4 一键标记当前用户所有未读通知为已读
- **请求方式**：`PUT`
- **请求路径**：`/notice/read-all`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": true
  }
  ```

#### 9.5 删除单条通知 (逻辑删除)
- **请求方式**：`DELETE`
- **请求路径**：`/notice/{id}`
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": true
  }
  ```

---
*文档维护：EduPoint 研发组*


