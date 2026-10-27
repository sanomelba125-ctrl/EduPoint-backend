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

## 为什么放在仓库里

`1A实训` 根目录本身不是 Git 仓库，只有 `backend`、`admin-web`、`mobile-app` 三个子仓库。根目录的编排与脚本原先无法被版本控制，出问题只能靠手工备份。本目录把它们纳入 `backend` 仓库，至少保证历史可追溯、可回滚、可推送云端。
