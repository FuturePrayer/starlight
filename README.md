# Starlight

[English](./README.en.md) · 中文

[![Release](https://img.shields.io/github/v/release/FuturePrayer/starlight?sort=semver)](https://github.com/FuturePrayer/starlight/releases)
[![Java 25](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![License: Anti 996](https://img.shields.io/badge/License-Anti%20996-black)](./LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/FuturePrayer/starlight)

![Starlight Logo](./logo.png)

Starlight 是一个基于 **Spring Boot 4 + Vue 3** 的 Markdown 笔记系统，支持分类树、全文搜索、分享链接、公开站点、主题扩展，以及面向 AI 客户端的 **MCP Server**。

## 特性概览

- 📝 Markdown 笔记编辑、自动保存、目录大纲
- 🌲 无限级分类树、根目录笔记、置顶与回收站
- 🔎 标题与正文全文搜索
- 📦 ZIP 导入 / 导出（Markdown + 文件夹结构）
- 🔗 公开分享、密码分享、过期时间、二维码
- 🌌 星迹书阁：把分类发布为只读公开站点
- 🎨 内置与外部主题
- 🔐 TOTP 两步验证、Passkey / WebAuthn
- 🤖 无状态 Streamable HTTP MCP Server，支持 API Key、目录范围和只读权限

## 技术栈

- **后端：** Java 25、Spring Boot 4、Spring MVC、Spring Data JPA、Flyway、Sa-Token、Spring AI MCP
- **前端：** Vue 3、Vite、Pinia、Vue Router
- **数据库：** 默认 H2，同时支持 PostgreSQL 与 MySQL
- **部署形态：** 合并镜像、前后端分离镜像、Spring Boot Jar、GraalVM Native Image

## 快速部署

### 方式一：Docker Compose（推荐）

仓库已提供根目录 `docker-compose.yml`，默认使用：

- 合并镜像：`ghcr.io/futureprayer/starlight:latest`
- 数据库：H2 文件库
- 数据持久化目录：`./data`
- 主题目录：`./themes`

启动：

```bash
docker compose up -d
```

停止：

```bash
docker compose down
```

启动后访问：

- `http://localhost:8080/login`
- `http://localhost:8080/register`
- `http://localhost:8080/app`

首次启动说明：

- 第一个注册用户会自动成为管理员
- 公共注册默认关闭，可由管理员在设置中心开启
- MCP Server 默认关闭，需管理员手动开启

### 方式二：直接运行容器

```bash
docker run -d \
  --name starlight \
  -p 8080:8080 \
  -v ./data:/app/data \
  -v ./themes:/app/themes \
  ghcr.io/futureprayer/starlight:latest
```

中国大陆环境可按需替换镜像源：

- `swr.cn-east-3.myhuaweicloud.com/suhoan/starlight:latest`

### 方式三：源码构建后运行

先构建前端静态资源并打包后端：

```bash
cd frontend
npm ci
npm run build:combined

cd ..
mvn clean package
java -jar target/starlight-<version>.jar
```

如果只需要后端 API：

```bash
mvn spring-boot:run
```

> 未将前端资源构建进后端时，服务仍可提供 API，但不会自带完整的合并式 Web UI。

## 配置说明

默认配置位于 `src/main/resources/application.yaml`。

### 常用环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `STARLIGHT_DATASOURCE_URL` | `jdbc:h2:file:./data/starlight;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE` | 数据库连接串 |
| `STARLIGHT_DATASOURCE_USERNAME` | `sa` | 数据库用户名 |
| `STARLIGHT_DATASOURCE_PASSWORD` | 空 | 数据库密码 |
| `STARLIGHT_THEME_DIR` | `themes` | 外部主题目录 |
| `STARLIGHT_NOTE_TRASH_RETENTION_DAYS` | `30` | 回收站保留天数 |
| `STARLIGHT_NOTE_TRASH_CLEANUP_CRON` | `0 20 3 * * *` | 回收站清理计划 |
| `JAVA_OPTS` | 空 | JVM 启动参数（容器场景常用） |

### 数据库支持

- **H2：** 默认方案，最适合单机部署与快速试用
- **PostgreSQL：** 适合正式环境
- **MySQL：** 适合已有 MySQL 基础设施的场景

Flyway 会在启动时自动执行数据库迁移；运行时不依赖 JPA 自动建表。

### HTTPS 与 Passkey

若要启用 Passkey / WebAuthn：

- 需要管理员显式开启通行密钥功能
- 站点 URL 必须配置为 `https://`
- 变更站点域名可能导致已有通行密钥失效，需要重新注册

## MCP Server

Starlight 内置 **Stateless Streamable HTTP MCP Server**，默认端点为：

- `POST /api/mcp`

### MCP 的启用方式

1. 管理员进入设置中心，开启 **MCP Server** 开关
2. 用户在设置中心创建自己的 **API Key**
3. 为每个 Key 分别配置：
   - 是否启用
   - 是否只读（默认只读）
   - 是否允许访问全部目录
   - 指定目录范围（自动包含子目录）

### MCP 权限模型

- MCP **不使用普通登录会话**，必须通过 API Key 访问
- 管理员关闭 MCP 后，所有 API Key 都无法访问 MCP 端点
- 每个 Key 只能访问授权目录及其子目录内的分类与笔记
- 只读 Key 只能使用查询类工具，不能执行增删改
- 后端会记录每个 Key 的最近使用时间，便于在设置中心审计

### 当前提供的工具

| 工具名 | 说明 | 只读 Key 可用 |
| --- | --- | --- |
| `starlight_list_tree` | 查询目录树与笔记树，支持按分类与深度裁剪 | 是 |
| `starlight_get_note_content` | 获取笔记 Markdown 原文与元数据 | 是 |
| `starlight_search_note_content` | 在授权范围内全文搜索笔记 | 是 |
| `starlight_create_category` | 创建分类 | 否 |
| `starlight_update_category` | 修改分类名称或移动分类 | 否 |
| `starlight_delete_category` | 删除空分类 | 否 |
| `starlight_create_note` | 创建笔记 | 否 |
| `starlight_update_note` | 修改笔记标题、内容或分类 | 否 |
| `starlight_delete_note` | 将笔记移入回收站 | 否 |

### MCP 接入提示

- 推荐使用支持 **Streamable HTTP** 的 MCP 客户端
- 认证头可使用：
  - `Authorization: Bearer <api-key>`
  - `X-API-Key: <api-key>`
- 若模型会把空分类传成字符串 `"null"` 或 `"undefined"`，服务端已兼容为“根目录”

## 镜像与发布

仓库当前提供以下镜像变体：

- `ghcr.io/futureprayer/starlight:latest`：Java 前后端合并镜像
- `ghcr.io/futureprayer/starlight:latest-frontend`：独立前端镜像
- `ghcr.io/futureprayer/starlight:latest-backend`：独立后端镜像
- `ghcr.io/futureprayer/starlight:latest-native`：前后端合并原生镜像
- `ghcr.io/futureprayer/starlight:latest-backend-native`：后端原生镜像

推送 `v<version>` 标签后，可通过仓库中的发布工作流构建 Release 与容器镜像。

## 本地开发

### 前端

```bash
cd frontend
npm ci
npm run dev
```

Vite 开发服务器会将 `/api` 与 `/theme-files` 代理到 `http://localhost:8080`。

### 后端

```bash
mvn spring-boot:run
```

### 测试

```bash
mvn test
```

## 项目结构

```text
startlight/
├─ src/main/java/          # Spring Boot 后端源码
├─ src/main/resources/     # 配置、Flyway、静态资源
├─ frontend/               # Vue 3 + Vite 前端
├─ deploy/nginx/           # 独立前端部署的 Nginx 模板
├─ docker-compose.yml      # 默认 H2 的 Compose 部署文件
├─ sql/                    # 初始化 / 参考 SQL
└─ .github/workflows/      # 发布流程（如仓库中存在）
```

## 说明

- 当前工作区目录可能是 `startlight`，但应用名、包名、制品名和镜像名统一使用 `starlight`
- 搜索功能在不同数据库上的实现有所区分，但都会保证功能可用
- 后端设计兼顾 GraalVM Native Image，新增能力尽量避免不利于原生编译的实现方式

## License

[**Anti 996**](./LICENSE)
