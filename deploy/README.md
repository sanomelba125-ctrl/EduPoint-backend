# deploy 目录（部署资产副本）

本目录是项目根目录部署文件的**仓库内副本**，用于让生产编排与启动脚本随 `backend` 仓库一起版本化、可追溯、可推送云端。

| 本目录文件 | 根目录原文件 | 与原件差异 |
| --- | --- | --- |
| `docker-compose.prod.yml` | `../docker-compose.prod.yml` | `build.context` 按本目录重写：`./backend` → `..`，`./admin-web` → `../../admin-web`，`./mobile-app` → `../../mobile-app` |
| `start-dev.ps1` | `../start-dev.ps1` | `$rootDir` 上溯两级，仍指向项目根目录 |

## 运行方式

生产编排（在项目根目录执行，显式指定 env 文件）：

```bash
docker compose -f backend/deploy/docker-compose.prod.yml --env-file .env.prod up -d
```

开发环境一键启动：

```powershell
powershell -ExecutionPolicy Bypass -File backend\deploy\start-dev.ps1
```

## 同步规则

- 根目录文件是唯一事实来源，**先改根目录，再同步本目录**。
- 同步时保留上表中的路径差异，否则副本会因 build context 错误而无法构建。
- 不要只改本目录，否则根目录一键脚本与生产编排会与仓库版本漂移。

## 环境变量文件（.env）

`docker-compose.yml` 里所有密码/密钥都写成 `${VAR:?must be set in .env}`，**缺 `.env` 会在插值阶段直接报错退出**，`start-dev.ps1` 连 `[0/3]` 都过不去（这就是 2026-10-29 修掉的 P1-1）。

- 项目根目录必须有 `.env`（模板见 `.env.example`）；`docker compose` 只认与 compose 文件同目录、或 `--project-directory` 指定的那个 `.env`。
- **本机已有 `campus-*` 容器时，`.env` 里的口令必须与建容器时一致**：MySQL 的 root 密码是在数据卷初始化时写死的，改了 `.env` 只会让 compose 重建容器，数据卷里的旧密码不会跟着变，后端直接连不上。
- 因此脚本现在分两种情况处理：`.env` 缺失且**没有**容器 → 自动从 `.env.example` 生成（仅供本地开发，会打印警告）；`.env` 缺失但**已有**容器 → 直接中止并提示风险，绝不自动生成。
- 若 `.env` 仍是 `.env.example` 的示例口令（含 `your_strong_`），脚本会额外提示一次。

## 为什么放在仓库里

`1A实训` 根目录本身不是 Git 仓库，只有 `backend`、`admin-web`、`mobile-app` 三个子仓库。根目录的编排与脚本原先无法被版本控制，出问题只能靠手工备份。本目录把它们纳入 `backend` 仓库，至少保证历史可追溯、可回滚、可推送云端。
